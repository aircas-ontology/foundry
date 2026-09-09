package com.aircas.ptr.foundry.ontology

import com.aircas.ptr.foundry.ontology.aspect.FuncParam
import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO
import groovy.util.logging.Slf4j
import org.hipparchus.geometry.euclidean.threed.Vector3D
import org.hipparchus.util.FastMath
import org.orekit.bodies.GeodeticPoint
import org.orekit.bodies.OneAxisEllipsoid
import org.orekit.data.DataContext
import org.orekit.data.DataProvidersManager
import org.orekit.data.DirectoryCrawler
import org.orekit.frames.Frame
import org.orekit.frames.FramesFactory
import org.orekit.propagation.analytical.tle.TLE
import org.orekit.propagation.analytical.tle.TLEPropagator
import org.orekit.time.AbsoluteDate
import org.orekit.time.TimeScalesFactory
import org.orekit.utils.Constants
import org.orekit.utils.IERSConventions

import java.text.SimpleDateFormat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 星地微波链路（S2G）连接机会算子
 *
 * 对齐 C++ LaserLinkConnectivityCalculator 星地分支：
 *   Pass1 computeGroundStationLinks：最大斜距建几何窗
 *   Pass2 checkGroundVisibility：地球遮挡球 + 最小仰角（二次校验）
 *
 * 来源：
 *   - isEarthOccludedBySphere / checkGroundVisibility / computeGroundStationLinks
 *   - m_ground_min_elev_deg=5°, m_ground_max_link_dist=5000km
 *
 * 轨道：Orekit TLEPropagator；地面站：经纬度 → WGS84 ECEF。
 */
@Slf4j
class MicrowaveS2gOperator {


    private static final double R_EARTH = 6378137.0
    private static final double WGS84_E2 = 6.69437999014e-3

    private static final double DEFAULT_MAX_LINK_KM = 5000.0
    private static final double DEFAULT_MIN_ELEV_DEG = 5.0
    private static final double DEFAULT_ESTABLISH_SEC = 11.0
    private static final double DEFAULT_TIME_STEP_SEC = 60.0
    private static final double DEFAULT_BANDWIDTH_MBPS = 100.0
    private static final double DEFAULT_FREQ_GHZ = 26.5
    private static final String DEFAULT_BAND = "Ka-band"
    private static final String DEFAULT_DIRECTION = "s2g_only"
    /** 防止超长时段/过小步长撑爆堆：最多约 2 天@1s 或 约 7 天@60s */
    private static final int MAX_SAMPLES = 200_000
    private static final double MIN_STEP_SEC = 1.0
    private static final long MAX_SPAN_MS = 30L * 24 * 3600 * 1000 // 30 天

    static void main(String[] args) {
        DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager()
        File orekitData = new File("ontology/ontology-server/src/main/resources/orekit-data")
        manager.addProvider(new DirectoryCrawler(orekitData))
        MicrowaveS2gOperator op = new MicrowaveS2gOperator()
        String tle1 = "1 99999U          25060.00000000  .00018383  00000-0  11127-2 0 00008"
        String tle2 = "2 99999 097.4555 146.6730 0009736 296.2736 029.6710 15.10537517000010"

        // 北京附近地面站
        double gsLat = 40.0
        double gsLon = 116.0
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
        Date start = sdf.parse("2025-03-01 00:00:00")
        Date end = sdf.parse("2025-03-01 12:00:00")
        FunctionResultVO result = op.handle(
                tle1, tle2, gsLat, gsLon,
                start, end, 60.0d,
                "Ka-band", 26.5d, 11.0d,
                15.0d, 5000.0d, 100.0d, "s2g_only"
        )
        log.info("result: {}", result)
    }


    FunctionResultVO handle(
            @FuncParam(name = "orbitTledataTle1", description = "【卫星实体】轨道两行根数第1行") String orbitTledataTle1,
            @FuncParam(name = "orbitTledataTle2", description = "【卫星实体】轨道两行根数第2行") String orbitTledataTle2,
            @FuncParam(name = "groundStationLatDeg", description = "【地面站实体】纬度(度)") Double groundStationLatDeg,
            @FuncParam(name = "groundStationLonDeg", description = "【地面站实体】经度(度)") Double groundStationLonDeg,
            @FuncParam(name = "startTime", description = "分析起始UTC，可空=当前") Date startTime,
            @FuncParam(name = "endTime", description = "分析结束UTC，可空=起始+1h") Date endTime,
            @FuncParam(name = "timeStepSec", description = "采样步长(秒)，默认60") Double timeStepSec,
            @FuncParam(name = "band", description = "微波波段Ka/X/S，默认Ka-band") String band,
            @FuncParam(name = "centerFreqGhz", description = "中心频率(GHz)，默认随波段") Double centerFreqGhz,
            @FuncParam(name = "linkEstablishTimeSec", description = "建链开销(秒)，默认11；几何窗后再扣") Double linkEstablishTimeSec,
            @FuncParam(name = "minElevationDeg", description = "最小仰角(度)，默认5") Double minElevationDeg,
            @FuncParam(name = "maxLinkDistanceKm", description = "最大斜距(km)，默认5000") Double maxLinkDistanceKm,
            @FuncParam(name = "bandwidthMbps", description = "标称带宽(Mbps)，默认100") Double bandwidthMbps,
            @FuncParam(name = "direction", description = "链路方向s2g_only/g2s_only/both，默认s2g_only") String direction
    ) {
        String tle1 = trim(orbitTledataTle1), tle2 = trim(orbitTledataTle2)
        if (!tle1 || !tle2) return empty("S2G缺少卫星两行根数")
        if (groundStationLatDeg == null || groundStationLonDeg == null) {
            return empty("S2G缺少地面站经纬度")
        }
        if (groundStationLatDeg < -90 || groundStationLatDeg > 90
                || groundStationLonDeg < -180 || groundStationLonDeg > 180) {
            return empty("S2G地面站经纬度非法")
        }

        Date t0 = startTime ?: new Date()
        Date t1 = endTime ?: new Date(t0.time + 3600_000L)
        if (t1.time < t0.time) return empty("S2G结束时间早于起始时间")
        if (t1.time - t0.time > MAX_SPAN_MS) {
            return empty("S2G分析时段超过${MAX_SPAN_MS / 86400000L}天上限，请缩小时间范围或增大步长")
        }

        Map bandMeta = resolveBand(band, centerFreqGhz)
        double step = (timeStepSec != null && timeStepSec >= MIN_STEP_SEC) ? timeStepSec : DEFAULT_TIME_STEP_SEC
        step = sanitizeStep(t0, t1, step)
        S2gParams p = new S2gParams(
                maxDistM: d(maxLinkDistanceKm, DEFAULT_MAX_LINK_KM) * 1000.0,
                minElevDeg: d(minElevationDeg, DEFAULT_MIN_ELEV_DEG),
                stepSec: step,
                establishSec: d(linkEstablishTimeSec, DEFAULT_ESTABLISH_SEC),
                direction: (direction?.trim() ?: DEFAULT_DIRECTION)
        )

        try {
            Map out = computeTwoPass(tle1, tle2, groundStationLatDeg, groundStationLonDeg, t0, t1, p)
            List<VisibilityWindow> windows = out.windows as List<VisibilityWindow>
            VisibilityWindow nearest = pickNearest(windows)
            String linkType = p.direction == "g2s_only" ? "G2S" : "S2G"
            String desc = windows.isEmpty() ? "分析时段内无星地微波可见窗口"
                    : (nearest ? "最近可见窗口开始时间:${nearest.startTime},结束时间：${nearest.endTime}"
                    : "共${windows.size()}个可见窗口")

            def b = FunctionResultVO.builder()
                    .description(desc)
                    .timeWindows(windows)
                    .data([
                            linkType            : linkType,
                            medium              : "MICROWAVE",
                            band                : bandMeta.band,
                            centerFreqGhz       : bandMeta.freqGhz,
                            channelBwMhz        : bandMeta.channelBwMhz,
                            rainFadeMarginDb    : bandMeta.rainFadeMarginDb,
                            groundStationLatDeg : groundStationLatDeg,
                            groundStationLonDeg : groundStationLonDeg,
                            direction           : p.direction,
                            orbitPropagator     : "Orekit-TLEPropagator-SGP4/SDP4",
                            algorithm           : "distance_pass + checkGroundVisibility",
                            minElevationDeg     : p.minElevDeg,
                            maxLinkDistanceKm   : p.maxDistM / 1000.0,
                            linkEstablishTimeSec: p.establishSec,
                            bandwidthMbps       : d(bandwidthMbps, DEFAULT_BANDWIDTH_MBPS),
                            timeStepSec         : p.stepSec,
                            sampleCount         : out.sampleCount,
                            geoWindowCount      : out.geoWindowCount,
                            windowCount         : windows.size()
                    ])
            if (nearest) b.startTime(nearest.startTime).endTime(nearest.endTime)
            return b.build()
        } catch (Exception e) {
            log.error("Orekit S2G failed", e)
            return empty("S2G Orekit计算失败: ${e.message}", [error: e.message])
        }
    }

    // ===================== C++ 对齐：两遍 =====================

    private static Map computeTwoPass(String tle1, String tle2, double latDeg, double lonDeg,
                                      Date start, Date end, S2gParams p) {
        Frame itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, true)
        OneAxisEllipsoid earth = new OneAxisEllipsoid(
                Constants.WGS84_EARTH_EQUATORIAL_RADIUS, Constants.WGS84_EARTH_FLATTENING, itrf)
        Vector3D gsEcef = earth.transform(new GeodeticPoint(
                FastMath.toRadians(latDeg), FastMath.toRadians(lonDeg), 0.0))

        TLEPropagator prop = TLEPropagator.selectExtrapolator(new TLE(tle1, tle2))
        AbsoluteDate tStart = new AbsoluteDate(start, TimeScalesFactory.getUTC())
        AbsoluteDate tEnd = new AbsoluteDate(end, TimeScalesFactory.getUTC())

        List<Sample> samples = sampleOrbit(prop, itrf, tStart, tEnd, p.stepSec)

        // Pass1：最大斜距
        List<int[]> geoWins = buildDistanceWindows(samples, gsEcef, p.maxDistM)

        // Pass2：二次校验 — 地球遮挡 + 最小仰角（对齐 checkGroundVisibility）
        List<int[]> filtered = filterVisibility(samples, geoWins, gsEcef, latDeg, p.minElevDeg)

        return [
                windows       : toWindows(samples, filtered, p.establishSec),
                sampleCount   : samples.size(),
                geoWindowCount: geoWins.size()
        ]
    }

    private static List<Sample> sampleOrbit(TLEPropagator prop, Frame itrf,
                                            AbsoluteDate t0, AbsoluteDate t1, double stepSec) {
        if (stepSec < MIN_STEP_SEC) {
            throw new IllegalArgumentException("timeStepSec too small: " + stepSec)
        }
        double spanSec = t1.durationFrom(t0)
        if (spanSec < 0) throw new IllegalArgumentException("end before start")
        long estimate = (long) (spanSec / stepSec) + 2L
        if (estimate > MAX_SAMPLES) {
            throw new IllegalArgumentException(
                    "sample count ${estimate} exceeds MAX_SAMPLES=${MAX_SAMPLES}; enlarge timeStepSec or shrink time span")
        }
        List<Sample> list = new ArrayList<>((int) estimate)
        AbsoluteDate t = t0
        int n = 0
        while (t.compareTo(t1) <= 0) {
            if (++n > MAX_SAMPLES) {
                throw new IllegalStateException("sample loop exceeded MAX_SAMPLES=" + MAX_SAMPLES)
            }
            list.add(new Sample(t, prop.propagate(t).getPVCoordinates(itrf).getPosition()))
            t = t.shiftedBy(stepSec)
        }
        return list
    }

    /** 若估计采样数过大，自动抬高步长 */
    private static double sanitizeStep(Date t0, Date t1, double stepSec) {
        double spanSec = Math.max(0.0, (t1.time - t0.time) / 1000.0)
        double need = spanSec / stepSec + 2.0
        if (need <= MAX_SAMPLES) return stepSec
        double adjusted = Math.ceil(spanSec / (MAX_SAMPLES - 2.0))
        return Math.max(MIN_STEP_SEC, adjusted)
    }

    /** Pass1：对齐 computeGroundStationLinks 距离门限 */
    private static List<int[]> buildDistanceWindows(List<Sample> samples, Vector3D gsEcef, double maxDistM) {
        List<int[]> wins = []
        int start = -1
        for (int i = 0; i < samples.size(); i++) {
            boolean ok = samples[i].satEcef.distance(gsEcef) <= maxDistM
            if (ok && start < 0) start = i
            else if (!ok && start >= 0) {
                wins << ([start, i - 1] as int[])
                start = -1
            }
        }
        if (start >= 0) wins << ([start, samples.size() - 1] as int[])
        return wins
    }

    /** Pass2：对齐 checkGroundVisibility */
    private static List<int[]> filterVisibility(List<Sample> samples, List<int[]> geoWins,
                                                Vector3D gsEcef, double gsLatDeg, double minElevDeg) {
        List<int[]> out = []
        for (int[] gw : geoWins) {
            int seg = -1
            for (int i = gw[0]; i <= gw[1]; i++) {
                boolean vis = checkGroundVisibility(gsEcef, samples[i].satEcef, gsLatDeg, minElevDeg)
                if (vis && seg < 0) seg = i
                else if (!vis && seg >= 0) {
                    out << ([seg, i - 1] as int[])
                    seg = -1
                }
            }
            if (seg >= 0) out << ([seg, gw[1]] as int[])
        }
        return out
    }

    /**
     * 对齐 C++ checkGroundVisibility + isEarthOccludedBySphere
     */
    static boolean checkGroundVisibility(Vector3D gs, Vector3D sat, double gsLatDeg, double minElevDeg) {
        Vector3D los = sat.subtract(gs)
        double losLen = los.getNorm()
        if (losLen <= 1e-9) return false

        double latRad = FastMath.toRadians(gsLatDeg)
        double sinLat = FastMath.sin(latRad), cosLat = FastMath.cos(latRad)
        double N = R_EARTH / FastMath.sqrt(1.0 - WGS84_E2 * sinLat * sinLat)
        double oneMe2 = 1.0 - WGS84_E2
        double Rsurf = FastMath.sqrt(N * N * cosLat * cosLat + N * N * oneMe2 * oneMe2 * sinLat * sinLat)

        if (isEarthOccludedBySphere(gs, sat, Rsurf)) return false

        Vector3D up = gs.normalize()
        double sinElev = Vector3D.dotProduct(los, up) / losLen
        return sinElev >= FastMath.sin(FastMath.toRadians(minElevDeg))
    }

    /** 对齐 C++ isEarthOccludedBySphere */
    static boolean isEarthOccludedBySphere(Vector3D p1, Vector3D p2, double Rlocal) {
        Vector3D d = p2.subtract(p1)
        double a = Vector3D.dotProduct(d, d)
        if (a < 1e-12) return false
        double b = 2.0 * Vector3D.dotProduct(p1, d)
        double c = Vector3D.dotProduct(p1, p1) - Rlocal * Rlocal
        if (b >= 0.0) return false
        double disc = b * b - 4.0 * a * c
        if (disc <= 0.0) return false
        double sqrtDisc = FastMath.sqrt(disc)
        double inv2a = 1.0 / (2.0 * a)
        double t1 = (-b - sqrtDisc) * inv2a
        double t2 = (-b + sqrtDisc) * inv2a
        final double eps = 1e-9
        return (t1 > eps && t1 < 1.0 - eps) || (t2 > eps && t2 < 1.0 - eps)
    }

    private static List<VisibilityWindow> toWindows(List<Sample> samples, List<int[]> idxWins, double establishSec) {
        List<VisibilityWindow> list = []
        for (int[] w : idxWins) {
            AbsoluteDate geoS = samples[w[0]].date
            AbsoluteDate geoE = samples[w[1]].date
            AbsoluteDate commS = geoS.shiftedBy(establishSec)
            if (commS.compareTo(geoE) < 0) {
                list << VisibilityWindow.builder()
                        .startTime(commS.toDate(TimeScalesFactory.getUTC()))
                        .endTime(geoE.toDate(TimeScalesFactory.getUTC()))
                        .build()
            }
        }
        return list
    }

    private static Map resolveBand(String band, Double centerFreqGhz) {
        String key = band?.trim()?.toUpperCase() ?: "KA"
        if (key.contains("X") && !key.contains("KA")) {
            return [band: "X-band", freqGhz: centerFreqGhz ?: 8.2d, channelBwMhz: 50.0d, rainFadeMarginDb: 2.0d]
        }
        if ((key.startsWith("S") || key.contains("S-BAND")) && !key.contains("KA")) {
            return [band: "S-band", freqGhz: centerFreqGhz ?: 2.2d, channelBwMhz: 5.0d, rainFadeMarginDb: 1.0d]
        }
        return [band        : (band?.trim() ?: DEFAULT_BAND), freqGhz: centerFreqGhz ?: DEFAULT_FREQ_GHZ,
                channelBwMhz: 500.0d, rainFadeMarginDb: 6.0d]
    }

    private static VisibilityWindow pickNearest(List<VisibilityWindow> windows) {
        if (!windows) return null
        long now = System.currentTimeMillis()
        VisibilityWindow best = null
        for (VisibilityWindow w : windows) {
            if (w.endTime?.time >= now && (best == null || w.startTime.time < best.startTime.time)) best = w
        }
        return best ?: windows[0]
    }

    private static FunctionResultVO empty(String desc, Map data = null) {
        def b = FunctionResultVO.builder().description(desc).timeWindows([])
        if (data) b.data(data)
        return b.build()
    }

    private static String trim(String s) { s?.trim() ?: "" }

    private static double d(Double v, double fallback) { v != null ? v : fallback }

    private static class S2gParams {
        double maxDistM, minElevDeg, stepSec, establishSec
        String direction
    }

    private static class Sample {
        AbsoluteDate date
        Vector3D satEcef

        Sample(AbsoluteDate date, Vector3D satEcef) {
            this.date = date; this.satEcef = satEcef
        }
    }
}
