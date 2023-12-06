package com.aircas.ptr.foundry.common.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

public class DataSizeUtil {

    private static final Logger LOG = LoggerFactory.getLogger(DataSizeUtil.class);

    private static final long BYTES_PER_KB = 1024L;
    private static final long BYTES_PER_MB = 1048576L;
    private static final long BYTES_PER_GB = 1073741824L;
    private static final long BYTES_PER_TB = 1099511627776L;
    private static final long BYTES_PER_PB = 1125899906842624L;
    private static final long BYTES_PER_EB = 1152921504606846976L;

    private static final Pattern isDigit = Pattern.compile("[0-9]*");

    @Getter
    @AllArgsConstructor
    public enum DataUnit {

        BYTES("B", 1L),
        KILOBYTES("KB", BYTES_PER_KB),
        MEGABYTES("MB", BYTES_PER_MB),
        GIGABYTES("GB", BYTES_PER_GB),
        TERABYTES("TB", BYTES_PER_TB),
        PETABYTES("PB", BYTES_PER_PB),
        EXABYTES("EB", BYTES_PER_EB);

        public static final DataUnit[] ALL_UNITS = new DataUnit[]{BYTES, KILOBYTES, MEGABYTES, GIGABYTES, TERABYTES, PETABYTES, EXABYTES};

        String suffix;
        long size;
    }

    public static DataUnit getUnit(long dataSize) {
        LOG.debug("getUnit(),{}", dataSize);
        int digitGroups = 0;
        if (dataSize > 0) {
            digitGroups = Math.min(DataUnit.ALL_UNITS.length - 1, (int) (Math.log10((double) dataSize) / Math.log10(1024.0D)));
        }
        LOG.debug("digitGroups={}", digitGroups);
        return DataUnit.ALL_UNITS[digitGroups];
    }

    public static double getUnitValue(long dataSize, DataUnit unit) {
        return dataSize * 1.00 / unit.getSize();
    }

    public static double getFormatUnitValue(long dataSize, DataUnit unit, int scale) {
        return new BigDecimal(getUnitValue(dataSize, unit)).setScale(scale, BigDecimal.ROUND_HALF_UP).setScale(scale).doubleValue();
    }

    public static String format(long dataSize) {
        DataUnit unit = getUnit(dataSize);
        return getFormatUnitValue(dataSize, unit, 2) + unit.getSuffix();
    }

    public static String format(String dataSize) {
        if (StringUtils.isEmpty(dataSize)) {
            return "0B";
        }
        if (isDigit.matcher(dataSize).matches()) {
            return format(Long.valueOf(dataSize));
        }
        return format(0);
    }

    public static DataUnit getMaxUnit(List<Long> dataSizes) {
        return getUnit(dataSizes.stream().max(Long::compare).get());
    }

    /**
     * 格式化数据大小为MB
     *
     * @param dataSize 字节数据
     * @return
     */
    public static String formatMB(long dataSize) {
        if (dataSize <= 0) {
            return "0";
        }
        double result = Double.valueOf(dataSize) / BYTES_PER_MB;
        return String.format("%.2f", result);
    }

    /**
     * 格式化数据大小为GB
     *
     * @param dataSize 字节数据
     * @return
     */
    public static String formatGB(long dataSize) {
        if (dataSize <= 0) {
            return "0";
        }
        double result = Double.valueOf(dataSize) / BYTES_PER_GB;
        return String.format("%.2f", result);
    }

}
