package com.aircas.ptr.foundry.ontology.utils;

import com.xxl.job.core.util.IpUtil;

import java.net.InetAddress;

/**
 * 本机身份，供调度执行日志记录「在哪台机器上执行」。
 * IP 取 xxl-job 同款选址逻辑（内网可达地址，非 127.0.0.1）；进程生命周期内不变，静态缓存。
 */
public final class ExecutorMachineInfo {

    private static final String IP = IpUtil.getIp();
    private static final String HOST = resolveHost();

    private ExecutorMachineInfo() {
    }

    public static String ip() {
        return IP;
    }

    public static String host() {
        return HOST;
    }

    private static String resolveHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return IP;
        }
    }
}