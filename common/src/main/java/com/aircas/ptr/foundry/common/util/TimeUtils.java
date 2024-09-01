package com.aircas.ptr.foundry.common.util;
/**
 * Copyright: 2017 dingxiang-inc.com Inc. All rights reserved.
 */
import com.aircas.ptr.foundry.common.exception.DmException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.joda.time.DateTime;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * fileName: TimeUtils.java
 * description: TimeUtils.java类说明
 * author: jiangqian
 * date: 2018/1/31 10:32
 * @Copyright: 2017 dingxiang-inc.com Inc. All rights reserved.
 */
@Slf4j
public class TimeUtils {

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_TIME_STR_FORMAT = "yyyyMMddHHmmssSSS";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String TIME_FORMAT = "HH:mm:ss";
    private static final String BASE_DATE_FORMAT = "yyyyMMdd";

    /**
     * 作为标准的时间格式
     */
    private static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
    private static DateTimeFormatter dateTimeStrFormatter = DateTimeFormatter.ofPattern(DATE_TIME_STR_FORMAT);
    private static DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT);
    private static DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_FORMAT);
    private static DateTimeFormatter baseDateFormatter = DateTimeFormatter.ofPattern(BASE_DATE_FORMAT);

    private static final Pattern TS_PATTERN = Pattern.compile("\\d{13}");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}\\-\\d{1,2}\\-\\d{1,2}$");
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}$");
    private static final Pattern DATETIME_PATTERN = Pattern.compile("^\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}:\\d{2}:\\d{2}$");
    private static DateFormat NORM_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public static Period getTimeInterval(LocalDate begin, LocalDate end) {
        Period period =  Period.between(begin, end);
        return period;
    }

    public static void main(String[] args) {
//        System.out.println( parseMilliSecondToStr(50 * 1000));
//        System.out.println( parseMilliSecondToStr(5 * 50 * 1000));
//        System.out.println( parseMilliSecondToStr( 5 * 5 * 50 * 1000));
//        System.out.println( parseMilliSecondToStr( 10 * 5 * 5 * 50 * 1000));
//        System.out.println( parseMilliSecondToStr( 10 * 10 * 5 * 5 * 50 * 1000));
        org.joda.time.LocalDate localDate = org.joda.time.LocalDate.now();
        System.out.println(localDate.plusDays(1));
        System.out.println(getRangeTime(7));;
    }

    /**
     * 将毫秒装换为 xx小时xx分钟xx秒
     * @param milliSeconds 毫秒
     * @return
     */
    public static String parseMilliSecondToStr(long milliSeconds){
        if(milliSeconds < 0){
            return "";
        }
        if(milliSeconds < 1000){
            return milliSeconds + "毫秒";
        }
        StringBuilder sb = new StringBuilder();
        long totalSecond = milliSeconds / 1000; //秒
        System.out.println("totalSecond：" + totalSecond);
        if(totalSecond > 60 &&  totalSecond < 60 * 60){
            long minute = TimeUnit.SECONDS.toMinutes(totalSecond);
            sb.append(minute + "分钟");
            long second = totalSecond - 60 * minute;
            if(second > 0){
                sb.append(second + "秒");
            }
        }else if(totalSecond > 60 * 60){
            long hour = TimeUnit.SECONDS.toHours(totalSecond);
            sb.append(hour + "小时");
            long minute = TimeUnit.SECONDS.toMinutes(totalSecond) - 60 * hour;
            if(minute > 0){
                sb.append(minute + "分钟");
            }
            long second = totalSecond - (60 * hour + minute) * 60;
            if(second > 0){
                sb.append(second + "秒");
            }
        }else {
            sb.append(totalSecond + "秒");
        }
        return sb.toString();
    }

    public static void calculateTime(long seconds) {
        int day = (int) TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) - TimeUnit.SECONDS.toHours(TimeUnit.SECONDS.toDays(seconds));
        long minute = TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.SECONDS.toMinutes(TimeUnit.SECONDS.toHours(seconds));
        long second = TimeUnit.SECONDS.toSeconds(seconds) - TimeUnit.SECONDS.toSeconds(TimeUnit.SECONDS.toMinutes(seconds));

        System.out.println("Day " + day + " Hour " + hours + " Minute " + minute + " Seconds " + second);

    }

    /**
     * 时间格式, 转换为 LocalDateTime, 并且是 yyyy-MM-dd HH:mm:ss
     */
    public static LocalDateTime parseToLocalDateTime(String time) {
        try {
            if(DATETIME_PATTERN.matcher(time).find()) {
                return LocalDateTime.parse(time, dateTimeFormatter);
            }
            if (DATE_PATTERN.matcher(time).find()) {
                return LocalDate.parse(time, dateFormatter).atStartOfDay();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     *  时间间隔(年)
     */
    public static int getTimeIntervalYear(LocalDate begin, LocalDate end) {
        Period period =  getTimeInterval(begin, end);
        return Math.abs(period.getYears());
    }

    /**
     *  时间间隔(天)
     */
    public static int getTimeIntervalDay(LocalDate begin, LocalDate end) {
        Period period =  getTimeInterval(begin, end);
        return Math.abs(period.getDays());
    }

    /**
     *  时间间隔(天)
     */
    public static long getTimeIntervalDay(LocalDateTime begin, LocalDateTime end) {
        return Math.abs(begin.until(end, ChronoUnit.DAYS));
    }

    /**
     *  时间间隔(小时)
     */
    public static long getTimeIntervalHour(LocalDateTime begin, LocalDateTime end) {
        return Math.abs(begin.until(end, ChronoUnit.HOURS));
    }

    /**
     *  时间间隔(分钟)
     */
    public static long getTimeIntervalMinute(LocalDateTime begin, LocalDateTime end) {
        return Math.abs(begin.until(end, ChronoUnit.MINUTES));
    }

    /**
     *  时间间隔(秒)
     */
    public static long getTimeIntervalSecond(LocalDateTime begin, LocalDateTime end) {
        return Math.abs(begin.until(end, ChronoUnit.SECONDS));
    }

    public static LocalDateTime parseDateTime(Object obj) {
        if (obj == null)
            return null;
        if (obj instanceof LocalDateTime)
            return (LocalDateTime)obj;
        if (obj instanceof Date)
            return parseDateToLocalDateTime((Date)obj);
        if (obj instanceof Long)
            return parseDateToLocalDateTime(new Date((Long)obj));
        if (obj instanceof String) {
            if (NumberUtils.isDigits((String)obj)) {
                return parseDateToLocalDateTime(new Date(Long.parseLong((String)obj)));
            }
            return parseToLocalDateTime((String)obj);
        }

        return null;
    }

    private static LocalDateTime parseDateToLocalDateTime(Date date) {
        Instant instant = date.toInstant();//An instantaneous point on the time-line.(时间线上的一个瞬时点。)
        ZoneId zoneId = ZoneId.systemDefault();//A time-zone ID, such as {@code Europe/Paris}.(时区)
        return instant.atZone(zoneId).toLocalDateTime();
    }

    public static LocalTime parseTime(Object obj) {
        if (obj == null || "".equals(obj.toString()))
            return null;
        if (obj instanceof LocalTime)
            return (LocalTime)obj;
        if (obj instanceof LocalDateTime)
            return ((LocalDateTime) obj).toLocalTime();
        if (obj instanceof Long)
            return parseDateToLocalDateTime(new Date((Long)obj)).toLocalTime();
        if (obj instanceof String) { //HH:mm:ss
            try {
                return LocalTime.parse((String)obj, timeFormatter);
            } catch (Exception e) {
                //可能是传入了 yyyy-MM-dd HH:mm:ss
                LocalDateTime dateTime = parseToLocalDateTime((String)obj);
                return dateTime == null ? null : dateTime.toLocalTime();
            }
        }
        return null;
    }

    public static boolean isDateTime(Object obj) {
        if (obj == null)
            return false;
        if (obj instanceof LocalDateTime)
            return true;
        if (obj instanceof Date)
            return true;
        if ((obj instanceof Long || obj instanceof String) //13位长整型，从2001/9/9 9:46:40开始有效
                && TS_PATTERN.matcher(obj.toString()).find())
            return true;
        if (obj instanceof String
                && (DATE_PATTERN.matcher(obj.toString()).find()) || DATETIME_PATTERN.matcher(obj.toString()).find()) {
            return true;
        }
        return false;
    }

    public static boolean isTime(String str) {
        return str != null && TIME_PATTERN.matcher(str).find();
    }

    public static String formatDateTime(DateTime dateTime) {
        return NORM_DATE_FORMAT.format(dateTime.getMillis());
    }

    public static String currentBaseDateStr() {
        return LocalDate.now().format(baseDateFormatter);
    }

    public static String currentDateStr() {
        return LocalDate.now().format(dateFormatter);
    }

    public static String currentDateTimeStr() {
        return LocalDateTime.now().format(dateTimeFormatter);
    }

    public static String currentDateTime() {
        return LocalDateTime.now().format(dateTimeStrFormatter);
    }

    public static String dateTimeStr(LocalDateTime dateTime) {
        return dateTime.format(dateTimeFormatter);
    }

    public static String dateTimeStr(Date dateTime) {
        return DateFormatUtils.format(dateTime, DATE_TIME_FORMAT);
    }

//    public static Date getDayDate(Date dateTime) {
//        try {
//            return DateUtils.parseDate(DateFormatUtils.format(dateTime, DATE_FORMAT ), DATE_FORMAT);
//        } catch (ParseException e) {
//            e.getStackTrace();
//        }
//        return dateTime;
//    }

    public static String getDateStr(int intervalDay){
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, intervalDay); //得到前一天
        Date date = calendar.getTime();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        return df.format(date);
    }

    /**
     * 时间字符串格式转换
     * 20220101080808 -> 2022-01-01T08:08:08
     * @param input
     * @return
     */
    public static String parseTimeT(String input) {
        try {
            Date date = DateUtils.parseDate(input, "yyyyMMddHHmmss");
            return DateFormatUtils.format(date, "yyyy-MM-dd'T'HH:mm:ss");
        } catch (ParseException e) {
            log.warn("parseTimeT() :date parse error, date={}, pattern={}", input, "yyyyMMdd'T'HHmmss");
            log.error("parseTimeT() :date parse error, e={}",e.getMessage(),e);
            e.printStackTrace();
            return input;
        }
    }

    /**
     * 时间字符串格式转换
     * 2022-01-01T08:08:08 -> 20220101080808
     * @param input
     * @return
     */
    public static String parseTimeFromT(String input) {
        if(StringUtils.isBlank(input) || !input.contains("T")){
            return input;
        }
        try {
            Date date = DateUtils.parseDate(input, "yyyy-MM-dd'T'HH:mm:ss");
            return DateFormatUtils.format(date, "yyyyMMddHHmmss");
        } catch (ParseException e) {
            log.warn("parseTimeFromT(): date parse error, date={}, pattern={}", input, "yyyyMMdd'T'HHmmss");
            log.error("parseTimeFromT(): date parse error, e={}",e.getMessage(),e);
            e.printStackTrace();
            return input;
        }
    }


    /**
     * 获取近一周的日期及区间开始时间、结束时间
     * @return
     */
    public static Map<String,Object> getWeekDays(){
        Map<String,Object> result = new HashMap<>();
        org.joda.time.LocalDate localDate = org.joda.time.LocalDate.now();
        List<String> weekDays = new ArrayList<>();
        for(int i=6;i>=0;i--){
            weekDays.add(localDate.minusDays(i).toString());
        }
        Map<String,String> rangeTime = new HashMap<>();
        rangeTime.put("startTime",localDate.minusDays(6).toString() + " 00:00:00");
        rangeTime.put("endTime",localDate.plusDays(1).toString() + " 00:00:00");
        result.put("weekDays",weekDays);
        result.put("rangeTime",rangeTime);
        return result;
    }

    /**
     * 获取近4年的年份及区间开始时间、结束时间
     * @return
     */
    public static Map<String,Object> getFourYears(){
        Map<String,Object> result = new HashMap<>();
        org.joda.time.LocalDate localDate = org.joda.time.LocalDate.now();
        List<Integer> years = new ArrayList<>();
        for(int i=3;i>=0;i--){
            years.add(localDate.minusYears(i).getYear());
        }
        Map<String,String> rangeTime = new HashMap<>();
        rangeTime.put("startTime",localDate.minusYears(3).getYear() + "-01-01 00:00:00");
        rangeTime.put("endTime",localDate.plusYears(1).getYear() + "-01-01 00:00:00");
        result.put("years",years);
        result.put("rangeTime",rangeTime);
        return result;
    }

    /**
     * 获取当日的区间起止时间
     * @return
     */
    public static Map<String,String> getRangeTimeToday(){
        return getRangeTime(1);
    }
    /**
     * 获取近三天的区间起止时间
     * @return
     */
    public static Map<String,String> getRangeTime3Days(){
        return getRangeTime(3);
    }
    /**
     * 获取近一周的区间起止时间
     * @return
     */
    public static Map<String,String> getRangeTimeWeek(){
        return getRangeTime(7);
    }
    /**
     * 获取近一月的区间起止时间
     * @return
     */
    public static Map<String,String> getRangeTimeMouth(){
        return getRangeTime(30);
    }
    /**
     * 获取近一年的区间起止时间
     * @return
     */
    public static Map<String,String> getRangeTimeYear(){
        return getRangeTime(365);
    }
    /**
     * 获取近n天的区间起止时间
     * @param range
     * @return
     */
    public static Map<String,String> getRangeTime(int range){
        Map<String,String> result = new HashMap<>();
        org.joda.time.LocalDate localDate = org.joda.time.LocalDate.now();
        result.put("startTime",localDate.minusDays(range - 1).toString() + " 00:00:00");
        result.put("endTime",localDate.plusDays(1).toString() + " 00:00:00");
        return result;
    }

    public static Map<String,String> getRangeTimeDefault(){
        Map<String,String> result = new HashMap<>();
        org.joda.time.LocalDate localDate = org.joda.time.LocalDate.now();
        result.put("startTime","2020-12-28 00:00:00");
        result.put("endTime",localDate.plusDays(1).toString() + " 00:00:00");
        return result;
    }


    public static String parseTimeTT(String input) {
        if (StringUtils.isBlank(input)) {
            return null;
        }
        try {
            if (input.contains("T")) {

                String timeStr = input.replace("T", " ");
                if (timeStr.length() > 19) {
                    return timeStr.substring(0, 19);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

}
