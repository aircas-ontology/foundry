package com.aircas.ptr.foundry.common.util;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReader;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.Date;

/**
 * Debezium 时间戳字段的 fastjson2 反序列化器。
 * <p>
 * PostgreSQL 的 timestamp 列经 Debezium（MicroTimestamp / MicroTimestampField）序列化后为
 * int64 微秒时间戳（如 1789881855098789），而 fastjson2 默认按毫秒解析 {@code new Date(long)}，
 * 导致年份被放大到数万年。本 reader 按数量级自动判别毫秒/微秒并统一转换为 {@link Date}。
 * <p>
 * 仅作用于 fastjson2 的 JSON 反序列化（CDC 消费链路），不影响 MyBatis 的数据库读写。
 */
public class DebeziumDateReader implements ObjectReader<Date> {

    /**
     * 毫秒/微秒判别阈值：epoch 毫秒在公元 5138 年前均小于 1e14，
     * 而 epoch 微秒自 1973 年起即大于 1e14。
     */
    private static final long MICROSECOND_THRESHOLD = 100_000_000_000_000L;

    @Override
    public Date readObject(JSONReader jsonReader, Type fieldType, Object fieldName, long features) {
        if (jsonReader.nextIfNull()) {
            return null;
        }
        if (jsonReader.isString()) {
            String str = jsonReader.readString();
            if (str == null || str.isEmpty()) {
                return null;
            }
            try {
                return toDate(Long.parseLong(str.trim()));
            } catch (NumberFormatException ignore) {
                // 非数字字符串，尝试 ISO-8601（Debezium timestamptz 自适应模式）
                try {
                    return Date.from(OffsetDateTime.parse(str).toInstant());
                } catch (Exception ex) {
                    return null;
                }
            }
        }
        Number number = jsonReader.readNumber();
        if (number == null) {
            return null;
        }
        return toDate(number.longValue());
    }

    private static Date toDate(long epochValue) {
        long millis = Math.abs(epochValue) >= MICROSECOND_THRESHOLD ? epochValue / 1000L : epochValue;
        return new Date(millis);
    }
}
