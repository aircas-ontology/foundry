package com.aircas.ptr.foundry.ontology.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 容器算子启动命令（整条 docker run）解析工具：从 {@code -p}/{@code --publish} 中抽取端口映射。
 * <p>保存算子时调用一次，把宿主端口固化进 docker_config，执行期直接读取，无需再解析命令字符串。</p>
 */
public final class DockerRunCommandParser {

    private DockerRunCommandParser() {
    }

    /** 匹配独立的 -p / --publish 参数值：要求端口 flag 是独立 token（前面是行首或空白） */
    private static final Pattern PUBLISH_FLAG =
            Pattern.compile("(?:^|\\s)(?:-p|--publish)(?:\\s+|=)(\\S+)");

    /** 匹配 --name 指定的容器名（支持 --name xxx 与 --name=xxx） */
    private static final Pattern NAME_FLAG =
            Pattern.compile("(?:^|\\s)--name(?:\\s+|=)(\\S+)");

    /** 一条端口映射：宿主端口 + 容器端口（容器端口仅记录，宿主端口为调用入口） */
    public static final class PortMapping {
        private final Integer hostPort;
        private final Integer containerPort;

        public PortMapping(Integer hostPort, Integer containerPort) {
            this.hostPort = hostPort;
            this.containerPort = containerPort;
        }

        public Integer getHostPort() {
            return hostPort;
        }

        public Integer getContainerPort() {
            return containerPort;
        }
    }

    /**
     * 解析命令中第一个「显式指定宿主端口」的映射。
     * 支持 {@code -p hostPort:containerPort} 与 {@code -p ip:hostPort:containerPort}；
     * 忽略随机映射（大写 {@code -P}、{@code -p :containerPort}、{@code -p containerPort}、端口范围）。
     *
     * @return 解析不到可用宿主端口时返回 null
     */
    public static PortMapping parseFirstPublished(String runCommand) {
        if (runCommand == null || runCommand.isBlank()) {
            return null;
        }
        Matcher matcher = PUBLISH_FLAG.matcher(runCommand);
        while (matcher.find()) {
            PortMapping pm = parseSpec(matcher.group(1));
            if (pm != null && pm.getHostPort() != null) {
                return pm;
            }
        }
        return null;
    }

    /**
     * 从启动命令解析用户指定的容器名（--name）。
     *
     * @return 命令未显式指定 --name 时返回 null（交由平台按 containerKey 生成默认名）
     */
    public static String parseContainerName(String runCommand) {
        if (runCommand == null || runCommand.isBlank()) {
            return null;
        }
        Matcher matcher = NAME_FLAG.matcher(runCommand);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            return name.isEmpty() ? null : name;
        }
        return null;
    }

    /** 解析单个端口规格：[ip:]hostPort:containerPort[/protocol] */
    private static PortMapping parseSpec(String spec) {
        String s = spec;
        int slash = s.indexOf('/');            // 去掉 /tcp、/udp 后缀
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        String[] parts = s.split(":");
        if (parts.length == 3) {               // ip:hostPort:containerPort
            return new PortMapping(toPort(parts[1]), toPort(parts[2]));
        } else if (parts.length == 2) {        // hostPort:containerPort
            return new PortMapping(toPort(parts[0]), toPort(parts[1]));
        } else if (parts.length == 1) {        // 仅 containerPort，宿主端口由 docker 随机分配
            return new PortMapping(null, toPort(parts[0]));
        }
        return null;
    }

    /** 端口号解析：空、非纯数字（如范围 8080-8090）、越界均返回 null */
    private static Integer toPort(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            int port = Integer.parseInt(value.trim());
            return (port >= 1 && port <= 65535) ? port : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
