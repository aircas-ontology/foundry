package com.aircas.ptr.foundry.common.util;

import java.text.DecimalFormat;

public class FileSizeUtil {

    static long B = 1;
    static long KB = 1024;
    static long MB = 1048576;
    static long GB = 1073741824;
    static long TB = 1099511627776L;
    static long PB = 1125899906842624L;
    static long EB = 1152921504606846976L;

    public static String translate(Long size) {
        DecimalFormat df = new DecimalFormat("#.0");
        String fileSizeString = "";
        String wrongSize = "0B";
        if (size == null || size == 0) {
            return wrongSize;
        }
        if (size < KB) {
            fileSizeString = df.format((double) size) + "B";
        } else if (size < MB) {
            fileSizeString = df.format((double) size / KB) + "KB";
        } else if (size < GB) {
            fileSizeString = df.format((double) size / MB) + "MB";
        } else if (size < TB) {
            fileSizeString = df.format((double) size / GB) + "GB";
        } else if (size < PB) {
            fileSizeString = df.format((double) size / TB) + "TB";
        } else if (size < EB) {
            fileSizeString = df.format((double) size / PB) + "PB";
        } else {
            fileSizeString = df.format((double) size / EB) + "EB";
        }
        return fileSizeString;
    }
}
