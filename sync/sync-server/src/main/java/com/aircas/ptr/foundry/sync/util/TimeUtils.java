package com.aircas.ptr.foundry.sync.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Time相关工具类
 * 
 * @author yibo.tang
 * @date 2021-04-25 17:41:48
 * @since 1.0
 *
 */
@Slf4j
public final class TimeUtils {

	private TimeUtils() {
	}

	public static void sleepInMills(long mills) {
		if (mills <= 0) {
			return;
		}
		try {
			Thread.sleep(mills);
		} catch (InterruptedException e) {
			log.error("sleepInMills sleep error: message={}", e.getMessage(), e);
		}

	}

	public static void sleepOneSecond(long s, long e) {
		long cost = 1000 + s - e;
		TimeUtils.sleepInMills(cost);
	}
}
