package com.aircas.ptr.foundry.ontology.entity.util;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 卫星工具类
 */
@Component
public class SatelliteUtils {

    private static final double DEFAULT_ELEVATION_THRESHOLD = FastMath.toRadians(5.0); // 默认仰角阈值（5度）
    private static boolean dataInitialized = false;
    
    // TLE行1和行2的正则表达式模式
    private static final Pattern TLE_LINE1_PATTERN = Pattern.compile("^1 \\d{5}[A-Z] \\d{5}[A-Z] \\d{2}\\d{3}[A-Z] \\d{2}\\.\\d{8} .{8} .{8} \\d{1} \\d{4}$");
    private static final Pattern TLE_LINE2_PATTERN = Pattern.compile("^2 \\d{5} \\d{3}\\.\\d{4} \\d{3}\\.\\d{4} \\d{7} \\d{3}\\.\\d{4} \\d{3}\\.\\d{4} \\d{2}\\.\\d{8}\\d{6}$");

    //@PostConstruct
    public void initialize() {
        try {
            if (!dataInitialized) {
                System.out.println("初始化Orekit数据...");
                
                // 尝试从多个位置加载Orekit数据
                boolean dataLoaded = false;
                
                // 1. 首先尝试从jar包内部的资源目录加载
                try {
                    System.out.println("尝试从jar包内部资源加载Orekit数据...");
                    
                    // 获取类路径中的orekit-data目录
                    InputStream testFile = getClass().getClassLoader().getResourceAsStream("orekit-data/tai-utc.dat");
                    if (testFile != null) {
                        testFile.close(); // 关闭测试文件流
                        
                        // 使用类路径资源配置Orekit
                        DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
                        
                        // 使用资源流方式加载数据
                        String[] resourceNames = {
                            "orekit-data/tai-utc.dat",
                            "orekit-data/eopc04_IAU2000.62-now",
                            "orekit-data/earth_latest_high_prec.npy"
                        };
                        
                        for (String resourceName : resourceNames) {
                            InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName);
                            if (is != null) {
                                // 创建临时文件
                                Path tempFile = Files.createTempFile("orekit", ".tmp");
                                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                                is.close();
                                
                                // 添加临时文件作为数据源
                                manager.addProvider(new DirectoryCrawler(tempFile.getParent().toFile()));
                                
                                // 设置临时文件在JVM退出时删除
                                tempFile.toFile().deleteOnExit();
                                
                                System.out.println("加载资源: " + resourceName);
                            }
                        }
                        
                        // 测试是否成功加载
                        try {
                            TimeScalesFactory.getUTC();
                            System.out.println("成功从jar包内部资源加载Orekit数据");
                            dataLoaded = true;
                        } catch (Exception e) {
                            System.out.println("从jar包内部资源加载Orekit数据失败: " + e.getMessage());
                        }
                    } else {
                        System.out.println("在类路径中未找到orekit-data资源");
                    }
                } catch (Exception e) {
                    System.out.println("尝试从jar包内部资源加载Orekit数据时出错: " + e.getMessage());
                }
                
                // 2. 如果从jar包内部加载失败，尝试从当前目录的orekit-data加载
                if (!dataLoaded) {
                    try {
                        System.out.println("尝试从当前目录加载Orekit数据...");
                        File currentDirData = new File("orekit-data");
                        if (currentDirData.exists() && currentDirData.isDirectory()) {
                            DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
                            manager.addProvider(new DirectoryCrawler(currentDirData));
                            // 测试是否成功加载
                            try {
                                TimeScalesFactory.getUTC();
                                System.out.println("成功从当前目录加载Orekit数据");
                                dataLoaded = true;
                            } catch (Exception e) {
                                System.out.println("从当前目录加载Orekit数据失败: " + e.getMessage());
                            }
                        } else {
                            System.out.println("当前目录下没有orekit-data目录");
                        }
                    } catch (Exception e) {
                        System.out.println("尝试从当前目录加载Orekit数据时出错: " + e.getMessage());
                    }
                }
                
                // 3. 如果前两种方法都失败，尝试从用户目录加载或创建
                if (!dataLoaded) {
                    try {
                        System.out.println("尝试从用户目录加载Orekit数据...");
                        String userHome = System.getProperty("user.home");
                        File userOrekitDataDir = new File(userHome, "orekit-data");
                        
                        if (!userOrekitDataDir.exists() || userOrekitDataDir.list() == null || userOrekitDataDir.list().length == 0) {
                            userOrekitDataDir.mkdirs();
                            System.out.println("创建" + userOrekitDataDir.getAbsolutePath() + "目录");
                            
                            // 从类路径资源复制数据文件到用户目录
                            copyResourcesFromClasspath(userOrekitDataDir);
                        }
                        
                        // 配置数据提供者
                        DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
                        manager.addProvider(new DirectoryCrawler(userOrekitDataDir));
                        
                        // 测试是否成功加载
                        TimeScalesFactory.getUTC();
                        System.out.println("成功从用户目录加载Orekit数据");
                        dataLoaded = true;
                    } catch (Exception e) {
                        System.out.println("尝试从用户目录加载Orekit数据时出错: " + e.getMessage());
                        throw new RuntimeException("无法初始化Orekit数据", e);
                    }
                }
                
                // 标记数据已初始化
                dataInitialized = true;
                System.out.println("Orekit数据初始化完成");
            }
        } catch (Exception e) {
            System.err.println("初始化Orekit数据时发生错误: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("初始化Orekit数据失败", e);
        }
    }
    
    /**
     * 从类路径资源复制Orekit数据文件到目标目录
     */
    private void copyResourcesFromClasspath(File targetDir) throws IOException {
        System.out.println("从类路径资源复制Orekit数据文件...");
        
        ClassLoader classLoader = getClass().getClassLoader();
        String[] resourcePaths = {
            "orekit-data/tai-utc.dat",
            "orekit-data/eopc04_IAU2000.62-now",
            "orekit-data/earth_latest_high_prec.npy"
            // 添加其他必要的数据文件
        };
        
        for (String resourcePath : resourcePaths) {
            InputStream is = classLoader.getResourceAsStream(resourcePath);
            if (is != null) {
                try {
                    // 提取文件名
                    String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
                    File targetFile = new File(targetDir, fileName);
                    
                    // 复制文件
                    Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("复制文件: " + fileName);
                } finally {
                    is.close();
                }
            } else {
                System.out.println("未找到资源: " + resourcePath);
            }
        }
        
        System.out.println("Orekit数据文件复制完成");
    }
    
    /**
     * 验证TLE格式
     */
    private boolean validateTLE(String line1, String line2) {
        // 简单验证：检查行长度和开头
        if (line1 == null || line2 == null) {
            return false;
        }
        
        // 检查行是否以"1 "和"2 "开头
        if (!line1.startsWith("1 ") || !line2.startsWith("2 ")) {
            return false;
        }
        
        // 检查行长度（标准TLE格式为69个字符）
        // 但我们允许一些宽容度
        if (line1.length() < 60 || line2.length() < 60) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 修复TLE格式
     */
    private String[] fixTLEFormat(String line1, String line2) {
        // 移除可能的前缀
        if (line1.startsWith("line1=")) {
            line1 = line1.substring(6);
        }
        if (line2.startsWith("line2=")) {
            line2 = line2.substring(6);
        }
        
        // 确保行以"1 "和"2 "开头
        if (!line1.startsWith("1 ")) {
            line1 = "1 " + line1;
        }
        if (!line2.startsWith("2 ")) {
            line2 = "2 " + line2;
        }
        
        return new String[] {line1, line2};
    }

    /**
     * 计算卫星对指定位置的最近可见窗口
     *
     * @param line1 TLE第一行
     * @param line2 TLE第二行
     * @param latitude 观测点纬度（度）
     * @param longitude 观测点经度（度）
     * @param altitude 观测点海拔高度（米）
     * @param durationHours 向前计算的时间（小时）
     * @return 可见窗口信息，如果没有可见窗口则返回null
     */
    public Map<String, Object> calculateVisibilityWindow(String line1, String line2, 
                                                        double latitude, double longitude, 
                                                        double altitude, int durationHours) {
        try {
            // 确保数据已初始化
            if (!dataInitialized) {
                initialize();
            }
            
            // 尝试修复TLE格式
            String[] fixedTLE = fixTLEFormat(line1, line2);
            line1 = fixedTLE[0];
            line2 = fixedTLE[1];
            
            // 验证TLE格式
            if (!validateTLE(line1, line2)) {
                throw new IllegalArgumentException("无效的TLE格式。请提供标准的两行根数格式。");
            }
            
            System.out.println("使用TLE数据：");
            System.out.println(line1);
            System.out.println(line2);
            
            // 创建TLE对象
            TLE tle = new TLE(line1, line2);
            
            // 创建TLE传播器
            TLEPropagator propagator = TLEPropagator.selectExtrapolator(tle);
            
            // 设置地球模型
            Frame earthFrame = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
            OneAxisEllipsoid earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                                                         Constants.WGS84_EARTH_FLATTENING,
                                                         earthFrame);
            
            // 创建观测点
            GeodeticPoint observerPoint = new GeodeticPoint(FastMath.toRadians(latitude),
                                                           FastMath.toRadians(longitude),
                                                           altitude);
            TopocentricFrame observerFrame = new TopocentricFrame(earth, observerPoint, "Observer");
            
            // 获取当前时间 - 使用Orekit标准方式
            TimeScale utc = TimeScalesFactory.getUTC();
            // 使用Java Date对象创建AbsoluteDate
            AbsoluteDate now = new AbsoluteDate(new Date(), utc);
            
            System.out.println("计算开始时间: " + now);
            
            // 计算结束时间（当前时间 + durationHours小时）
            AbsoluteDate endDate = now.shiftedBy(durationHours * 3600.0);
            System.out.println("计算结束时间: " + endDate);
            
            // 步长（秒）
            double stepSize = 60.0;
            
            // 用于存储可见性状态的变量
            boolean previouslyVisible = false;
            AbsoluteDate visibilityStart = null;
            List<Map<String, Object>> visibilityWindows = new ArrayList<>();
            
            // 从当前时间开始，按步长向前传播
            for (AbsoluteDate date = now; date.compareTo(endDate) <= 0; date = date.shiftedBy(stepSize)) {
                // 获取卫星状态
                SpacecraftState state = propagator.propagate(date);
                PVCoordinates pvCoordinates = state.getPVCoordinates();
                
                // 计算卫星在观测点坐标系中的位置
                PVCoordinates pvTopocentricCoordinates = observerFrame.getTransformTo(state.getFrame(), date)
                                                                     .transformPVCoordinates(pvCoordinates);
                Vector3D position = pvTopocentricCoordinates.getPosition();
                
                // 计算仰角
                double elevation = FastMath.PI / 2 - Vector3D.angle(position, Vector3D.PLUS_K);
                
                // 检查可见性（仰角大于阈值）
                boolean visible = elevation > DEFAULT_ELEVATION_THRESHOLD;
                
                // 检测可见性变化
                if (visible && !previouslyVisible) {
                    // 可见性开始
                    visibilityStart = date;
                } else if (!visible && previouslyVisible && visibilityStart != null) {
                    // 可见性结束，记录窗口
                    Map<String, Object> window = new HashMap<>();
                    window.put("startTime", visibilityStart.toString());
                    window.put("endTime", date.toString());
                    window.put("durationSeconds", date.durationFrom(visibilityStart));
                    visibilityWindows.add(window);
                    visibilityStart = null;
                }
                
                previouslyVisible = visible;
            }
            
            // 处理计算结束时仍然可见的情况
            if (previouslyVisible && visibilityStart != null) {
                Map<String, Object> window = new HashMap<>();
                window.put("startTime", visibilityStart.toString());
                window.put("endTime", endDate.toString() + " (计算结束)");
                window.put("durationSeconds", endDate.durationFrom(visibilityStart));
                visibilityWindows.add(window);
            }
            
            // 返回结果
            if (!visibilityWindows.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("hasVisibility", true);
                result.put("nextWindow", visibilityWindows.get(0)); // 最近的可见窗口
                result.put("allWindows", visibilityWindows); // 所有可见窗口
                return result;
            } else {
                Map<String, Object> result = new HashMap<>();
                result.put("hasVisibility", false);
                return result;
            }
            
        } catch (Exception e) {
            throw new RuntimeException("计算卫星可见窗口时发生错误: " + e.getMessage(), e);
        }
    }
} 