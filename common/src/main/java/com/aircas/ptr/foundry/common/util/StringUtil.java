package com.aircas.ptr.foundry.common.util;



import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StringUtil extends org.apache.commons.lang3.StringUtils {
    public StringUtil() {
    }

    public static String nullOrNotBlank(String string) {
        return isBlank(string) ? null : string;
    }

    public static String deleteTail(String string) {
        StringBuilder builder = new StringBuilder(string);
        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }

    public static String fillAtLeft(String string, char prefix, int number) {
        int length = string.length();
        StringBuilder stringBuilder = new StringBuilder(string);

        for (int i = 0; i < number - length; ++i) {
            stringBuilder.insert(0, prefix);
        }

        return stringBuilder.toString();
    }

    public static String fillAtRight(String string, char suffix, int number) {
        int length = string.length();
        StringBuilder stringBuilder = new StringBuilder(string);

        for (int i = 0; i < number - length; ++i) {
            stringBuilder.append(suffix);
        }

        return stringBuilder.toString();
    }

    public static boolean universalNamingConvention(String name) {
        return !name.matches("^.*[/|\\\\\\\\:|\\\\*|\\\\?|\\\"|<|>].*$");
    }

    /**
     * 字符串转list
     * @param string
     * @param delimiter
     * @return
     */
    public static List<String> asList(String string, String delimiter) {
        if (string.equals(delimiter)) {
            return Arrays.asList(string.split(delimiter));
        } else if (string != null) {
            return Arrays.asList(string);
        }else {
            return new ArrayList<>();
        }
    }
}
