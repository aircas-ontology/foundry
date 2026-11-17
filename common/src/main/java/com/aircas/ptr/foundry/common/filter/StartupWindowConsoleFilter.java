package com.aircas.ptr.foundry.common.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import java.lang.management.ManagementFactory;

/**
 * 控制台日志过滤器：运行期仅输出 WARN+，避免每条 INFO 重复刷到 stdout 撑爆容器日志盘；
 * JVM 启动后的时间窗口内（默认 120 秒）放行 INFO，保留 Starting/Started、端口等启动日志。
 */
public class StartupWindowConsoleFilter extends Filter<ILoggingEvent> {

    private static final long JVM_START_TIME = ManagementFactory.getRuntimeMXBean().getStartTime();

    private long infoWindowSeconds = 120;

    public void setInfoWindowSeconds(long infoWindowSeconds) {
        this.infoWindowSeconds = infoWindowSeconds;
    }

    public long getInfoWindowSeconds() {
        return infoWindowSeconds;
    }

    @Override
    public FilterReply decide(ILoggingEvent event) {
        Level level = event.getLevel();
        if (level.isGreaterOrEqual(Level.WARN)) {
            return FilterReply.NEUTRAL;
        }
        if (level.isGreaterOrEqual(Level.INFO)
                && event.getTimeStamp() - JVM_START_TIME <= infoWindowSeconds * 1000L) {
            return FilterReply.NEUTRAL;
        }
        return FilterReply.DENY;
    }
}
