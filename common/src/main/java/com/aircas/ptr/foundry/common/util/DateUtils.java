package com.aircas.ptr.foundry.common.util;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.springframework.util.Assert;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DateUtils {

    public static final long MILLIS_PER_SECOND = 1000L;
    public static final long MILLIS_PER_MINUTE = 60000L;
    public static final long MILLIS_PER_HOUR = 3600000L;
    public static final long MILLIS_PER_DAY = 86400000L;

    public static Date MAX_DATE = null;
    public static Date MIN_DATE = null;


    private static final Map<String, DateFormat> dateFormatMap = new ConcurrentHashMap();

    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME;


    static {
        dateFormatMap.put("yyyy-MM-dd", new SimpleDateFormat("yyyy-MM-dd"));
        dateFormatMap.put("yyyy-MM-dd HH:mm:ss", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        dateFormatMap.put("yyyy-MM-dd hh:mm:ss", new SimpleDateFormat("yyyy-MM-dd hh:mm:ss"));
        dateFormatMap.put("yyyy/MM/dd", new SimpleDateFormat("yyyy/MM/dd"));
        dateFormatMap.put("yyyy/MM/dd hh:mm:ss", new SimpleDateFormat("yyyy/MM/dd hh:mm:ss"));
        dateFormatMap.put("yyyy/MM/dd HH:mm:ss", new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"));
        dateFormatMap.put("yyyyMMdd", new SimpleDateFormat("yyyyMMdd"));
        dateFormatMap.put("yyyyMMddhhmmss", new SimpleDateFormat("yyyyMMddhhmmss"));
        dateFormatMap.put("yyyy-MM-dd HH:mm:ss.SSS", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"));

        try {
            MAX_DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("9999-12-31 23:59:59");
            MIN_DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1970-01-01 00:00:00");
        } catch (ParseException e) {
            e.printStackTrace();
        }

    }

    private DateUtils() {
    }

    /**
     * 获取白天日期
     *
     * @param date
     * @return
     */
    public static Date getMorning(Date date) {

        String dateStr = format(date, "yyyyMMdd");
        return fromString2Date(dateStr, "yyyyMMdd");
    }

    /**
     * 获取晚上日期
     *
     * @param date
     * @return
     */
    public static Date getNight(Date date) {
        long time = getMorning(date).getTime();
        long night = time + MILLIS_PER_DAY;
        return new Date(night);
    }

    /**
     * 判断两个日期是否是一天
     *
     * @param date1
     * @param date2
     * @return
     */
    public static boolean isOneDay(Date date1, Date date2) {

        Calendar c1 = Calendar.getInstance();
        Calendar c2 = Calendar.getInstance();
        c1.setTime(date1);
        c2.setTime(date2);
        return c1.get(5) == c2.get(5);
    }

    /**
     * 格式化日期
     *
     * @param date
     * @param pattern
     * @return
     */
    public static String format(Date date, String pattern) {

        Assert.notNull(date);
        DateFormat dateFormat = getDateFormat(pattern);
        return dateFormat.format(date);
    }

    /**
     * 获取当前时间，显示格式为yyyy-MM-dd HH:mm:ss
     *
     * @return
     */
    public static String currentFormat() {
        return stdFormat(new Date());
    }


    /**
     * 获取格式化时间，显示格式为yyyy-MM-dd HH:mm:ss
     *
     * @return
     */
    public static String stdFormat(Date date) {
        return format(date, "yyyy-MM-dd HH:mm:ss");
    }


    /**
     * 时间字符串转日期
     *
     * @param dateString
     * @param pattern
     * @return
     */
    public static Date fromString2Date(String dateString, String pattern) {

        Assert.hasLength(dateString);
        DateFormat dateFormat = getDateFormat(pattern);

        try {
            return dateFormat.parse(dateString);
        } catch (ParseException var4) {
            throw new IllegalArgumentException("日期解析错误。");
        }
    }


    /**
     * 解析格式化字符串为时间毫秒，格式为yyyy-MM-dd HH:mm:ss
     *
     * @return
     * @throws IllegalArgumentException if the text to parse is invalid
     */
    public static long fromString2Timestamp(String dateString, String pattern) {

        Assert.hasLength(dateString);
        DateTime dt = DateTime.parse(dateString, DateTimeFormat.forPattern(pattern));
        return dt.getMillis();
    }

    /**
     * 将毫秒数转换为日期格式的字符串
     *
     * @param mis
     * @return
     */
    public static String getSqlDate(long mis) {
        String dateStr = format(new Date(mis), "yyyy-MM-dd HH:mm:ss");
        String sqlDate = "TO_DATE('" + dateStr + "', 'YYYY-MM-DD HH24:MI:SS')";
        return sqlDate;
    }


    /**
     * 某一天的时间范围，从00:00:00到23:59:59
     *
     * @param date 待取范围的某一天
     * @return
     */
    public static Date[] getRegionDateOfDay(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date from = cal.getTime();

        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        Date to = cal.getTime();

        return new Date[]{from, to};
    }

    /**
     * Get the millis of day field value. 取某时刻与当天00:00:00差值
     *
     * @param timestamp
     * @return the millis of day
     */
    public static long getMillisOfDay(long timestamp) {
        DateTime dateTime = new DateTime(timestamp);
        return dateTime.getMillisOfDay();
    }

    public static Date addSecond(Date date, int second) {

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(13, second);
        return calendar.getTime();
    }

    public static Date addMinute(Date date, int minute) {

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(12, minute);
        return calendar.getTime();
    }

    public static Date addHour(Date date, int hour) {

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(10, hour);
        return calendar.getTime();
    }

    public static Date addDay(Date date, int day) {

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(5, day);
        return calendar.getTime();
    }

    public static Date addMonth(Date date, int month) {

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(2, month);
        return calendar.getTime();
    }

    public static Date addYear(Date date, int year) {

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(1, year);
        return calendar.getTime();
    }

    /**
     * 是否是周末
     *
     * @param date
     * @return
     */
    public static boolean isWeekend(Date date) {

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int dayOfWeek = calendar.get(7) - 1;
        return dayOfWeek < 1 || dayOfWeek > 5;
    }

    /**
     * 获取时间格式化类
     *
     * @param pattern
     * @return
     */
    private static DateFormat getDateFormat(String pattern) {

        Object dateFormat;
        if (StringUtil.isBlank(pattern)) {
            dateFormat = dateFormatMap.get("yyyy-MM-dd");
        } else {
            dateFormat = dateFormatMap.get(pattern);
            if (dateFormat == null) {
                dateFormat = new SimpleDateFormat(pattern);
                dateFormatMap.put(pattern, (DateFormat) dateFormat);
            }
        }

        return (DateFormat) dateFormat;
    }


    public static Date parseISO8601(String isoString) {
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(isoString, formatter);
        return Date.from(zonedDateTime.toInstant());
    }

}
