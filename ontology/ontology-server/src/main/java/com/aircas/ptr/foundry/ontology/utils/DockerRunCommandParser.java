package com.aircas.ptr.foundry.ontology.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    /** docker run 带值的选项（需跳过下一个 token） */
    private static final Set<String> VALUE_FLAGS = Set.of(
            "-p", "--publish", "-e", "--env", "-v", "--volume", "--name", "-a", "--attach",
            "--network", "--net", "--restart", "-m", "--memory", "--memory-swap", "--cpus", "--cpuset-cpus",
            "--volume-driver", "-w", "--workdir", "-u", "--user", "--hostname", "--add-host",
            "--entrypoint", "--health-cmd", "--health-interval", "--health-timeout", "--health-retries",
            "--health-start-period", "--mount", "--device", "--dns", "--dns-search", "--dns-option",
            "--expose", "--link", "--mac-address", "--pid", "--security-opt", "--stop-signal",
            "--stop-timeout", "--ulimit", "--label", "--log-driver", "--log-opt", "--platform",
            "--tmpfs", "--shm-size", "--sysctl", "--userns", "--runtime", "--pull",
            "-h", "--host", "--api-caller", "--blkio-weight", "--cgroup-parent", "--cidfile",
            "--cpu-count", "--cpu-period", "--cpu-quota", "--cpu-shares", "--cpu-rt-period", "--cpu-rt-runtime",
            "--detach-keys", "--group-add", "--ip", "--ip6", "--ipc", "--isolation", "--oom-score-adj",
            "--pids-limit", "--storage-opt", "--uts"
    );

    /** docker run 布尔开关（不跟值） */
    private static final Set<String> BOOLEAN_FLAGS = Set.of(
            "-d", "--detach", "--rm", "-i", "--interactive", "-t", "--tty",
            "-P", "--publish-all", "--init", "--read-only", "--no-healthcheck",
            "--privileged", "--disable-content-trust"
    );

    /** 兼容 -it / -dti 等短选项堆叠：拆成单个字母后全为布尔开关才视为布尔 flag */
    private static boolean isStackedShortBoolean(String token) {
        if (token.length() < 2 || token.charAt(0) != '-' || token.charAt(1) == '-') {
            return false;
        }
        for (int i = 1; i < token.length(); i++) {
            if (!BOOLEAN_FLAGS.contains("-" + token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

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

    /**
     * 从 docker run 命令中提取目标镜像引用（如 {@code mccutchen/go-httpbin:latest}）。
     * <p>用于 {@code repoTags} 缺失时的兜底：启动前可据此判断本地镜像是否存在、必要时 {@code docker load}。
     * 策略：跳过 docker 与全局 flag 定位 run 子命令，再逐个跳过 run 的选项后取第一个位置参数。</p>
     *
     * @return 解析不到时返回 null（交由 docker run 自行处理）
     */
    public static String parseImageRef(String runCommand) {
        if (runCommand == null || runCommand.isBlank()) {
            return null;
        }
        List<String> tokens = tokenizeSimple(runCommand);
        int i = 0;
        // 1. 定位 run 子命令，前面可能是 docker / 全局选项
        while (i < tokens.size()) {
            String t = tokens.get(i);
            if ("run".equals(t)) {
                i++;
                break;
            }
            if (t.startsWith("-")) {
                i = skipFlag(tokens, i);
                continue;
            }
            i++;
        }
        // 2. 越过 run 的选项，取第一个位置参数 = 镜像引用
        while (i < tokens.size()) {
            String t = tokens.get(i);
            if (t.startsWith("-")) {
                i = skipFlag(tokens, i);
                continue;
            }
            return t;
        }
        return null;
    }

    /** 跳过命令中一个选项及其可能的值，返回下一个待处理下标 */
    private static int skipFlag(List<String> tokens, int i) {
        String t = tokens.get(i);
        String flag = t.contains("=") ? t.substring(0, t.indexOf('=')) : t;
        // --xxx=value 或布尔开关：仅占 1 个 token
        if (t.contains("=") || BOOLEAN_FLAGS.contains(flag) || isStackedShortBoolean(t)) {
            return i + 1;
        }
        // 已知带值选项：跳 2
        if (VALUE_FLAGS.contains(flag)) {
            return i + 2;
        }
        // 未知选项，保守假定带值：跳 2
        return i + 2;
    }

    /** 简单切分命令：尊重单/双引号包裹，不处理反斜杠转义，够用于典型 docker run 命令 */
    private static List<String> tokenizeSimple(String command) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]*)\"|'(.*?)'|(\\S+)").matcher(command);
        while (m.find()) {
            if (m.group(1) != null) {
                out.add(m.group(1));
            } else if (m.group(2) != null) {
                out.add(m.group(2));
            } else {
                out.add(m.group(3));
            }
        }
        return out;
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
