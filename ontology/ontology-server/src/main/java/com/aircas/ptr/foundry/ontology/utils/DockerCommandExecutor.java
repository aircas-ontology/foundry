package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 容器算子 docker 命令执行器：用 {@link ProcessBuilder} 驱动 docker CLI。
 * <p>启动命令由用户提供（整条 {@code docker run}），本执行器负责尊重引号切分、补 {@code -d} 与
 * {@code --name} 后执行；停止/重启/状态回查按容器名或 ID 操作。与 {@code CompositeTransformExecutor}
 * / {@code CommandUtil} 一致，走本机 docker CLI（前置：部署服务器具备 docker 且进程有权限）。</p>
 */
@Slf4j
@Component
public class DockerCommandExecutor {

    private static final String DOCKER_BIN = "docker";

    /** 平台默认容器名前缀：foundry-ctr-&lt;containerKey&gt;（containerKey 为 UUID，天然唯一） */
    public static final String CONTAINER_NAME_PREFIX = "foundry-ctr-";

    /** 按 containerKey 生成平台默认容器名 */
    public static String defaultContainerName(String containerKey) {
        return CONTAINER_NAME_PREFIX + containerKey;
    }

    /** docker 操作超时秒数（run/stop 可能较慢） */
    private static final int DOCKER_TIMEOUT_SECONDS = 120;

    /** 捕获输出上限，避免异常大报文撑爆内存 */
    private static final int MAX_OUTPUT_BYTES = 256 * 1024;

    /** docker 命令执行结果 */
    public record DockerResult(int exitCode, String stdout, String stderr) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    /** 启动结果：容器 ID + 实际使用的容器名 */
    public record StartResult(String containerId, String containerName) {
    }

    /**
     * 启动容器：规范化 {@code docker run} 命令（补 -d/--name）后执行，返回容器 ID 与容器名。
     *
     * @param runCommand  用户提供的完整启动命令
     * @param defaultName 用户未指定 --name 时使用的容器名（含 containerKey，供停止/回查定位）
     */
    public StartResult startContainer(String runCommand, String defaultName) {
        List<String> argv = normalizeRunCommand(runCommand, defaultName);
        DockerResult result = exec(argv, DOCKER_TIMEOUT_SECONDS);
        if (!result.isSuccess()) {
            throw new BusinessException("容器启动失败：" + firstNonBlank(result.stderr(), result.stdout(),
                    "docker 退出码 " + result.exitCode()), HttpStatus.BAD_REQUEST);
        }
        // docker run -d 输出容器完整 ID（首行）；拿不到时按名字回查
        String containerId = firstLine(result.stdout());
        if (isBlank(containerId)) {
            containerId = inspectId(effectiveNameFromArgv(argv, defaultName));
        }
        return new StartResult(containerId, effectiveNameFromArgv(argv, defaultName));
    }

    /** docker stop（按名称或 ID），幂等：容器不存在不视为失败 */
    public void stop(String nameOrId) {
        DockerResult result = exec(List.of(DOCKER_BIN, "stop", nameOrId), DOCKER_TIMEOUT_SECONDS);
        if (!result.isSuccess() && !containsNotFound(result.stderr())) {
            throw new BusinessException("容器停止失败：" + firstNonBlank(result.stderr(), result.stdout(), ""),
                    HttpStatus.BAD_REQUEST);
        }
    }

    /** docker rm -f（按名称或 ID），幂等 */
    public void remove(String nameOrId) {
        DockerResult result = exec(List.of(DOCKER_BIN, "rm", "-f", nameOrId), DOCKER_TIMEOUT_SECONDS);
        if (!result.isSuccess() && !containsNotFound(result.stderr())) {
            throw new BusinessException("容器删除失败：" + firstNonBlank(result.stderr(), result.stdout(), ""),
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 回查容器运行状态：docker inspect -f '{{@.State.Status}}'（此处用花括号模板）。
     *
     * @return running/exited/created/restarting 等；容器不存在或查询失败返回 null
     */
    public String inspectStatus(String nameOrId) {
        DockerResult result = exec(List.of(DOCKER_BIN, "inspect", "-f", "{{.State.Status}}", nameOrId),
                DOCKER_TIMEOUT_SECONDS);
        return result.isSuccess() ? firstLine(result.stdout()) : null;
    }

    /** 容器是否存在 */
    public boolean exists(String nameOrId) {
        return inspectStatus(nameOrId) != null;
    }

    /**
     * 本地是否已存在指定镜像：{@code docker image inspect <tag>}，存在返回 true。
     * 用于 start 前判断是否需要先 {@code docker load}。
     */
    public boolean isImagePresent(String imageTag) {
        if (imageTag == null || imageTag.isBlank()) {
            return false;
        }
        return exec(List.of(DOCKER_BIN, "image", "inspect", imageTag), DOCKER_TIMEOUT_SECONDS).isSuccess();
    }

    /**
     * 从镜像 tar 包加载镜像到本地 docker：{@code docker load -i <tarPath>}。
     * <p>启动容器前若目标镜像 tag 本地不存在则调用；失败抛出带 docker 回显的异常。</p>
     */
    public void loadImage(String tarPath) {
        PreconditionUtils.checkArgument(tarPath != null && !tarPath.isBlank(), "镜像文件路径为空", HttpStatus.BAD_REQUEST);
        DockerResult result = exec(List.of(DOCKER_BIN, "load", "-i", tarPath), DOCKER_TIMEOUT_SECONDS);
        if (!result.isSuccess()) {
            throw new BusinessException("镜像加载失败：" + firstNonBlank(result.stderr(), result.stdout(),
                    "docker 退出码 " + result.exitCode()), HttpStatus.BAD_REQUEST);
        }
    }

    private String inspectId(String nameOrId) {
        DockerResult result = exec(List.of(DOCKER_BIN, "inspect", "-f", "{{.Id}}", nameOrId), DOCKER_TIMEOUT_SECONDS);
        return result.isSuccess() ? firstLine(result.stdout()) : null;
    }

    /**
     * 规范化启动命令：确保 docker 前缀与 run 子命令、补 -d、补 --name（已存在则保留用户的）。
     */
    private List<String> normalizeRunCommand(String runCommand, String defaultName) {
        PreconditionUtils.checkArgument(!isBlank(runCommand), "启动命令为空", HttpStatus.BAD_REQUEST);
        List<String> argv = tokenize(runCommand);
        PreconditionUtils.checkArgument(!argv.isEmpty(), "启动命令无法解析：" + runCommand, HttpStatus.BAD_REQUEST);

        // 去可能存在的 shell 前缀干扰，定位 docker / run
        int dockerIdx = indexOf(argv, DOCKER_BIN);
        int runIdx = indexOf(argv, "run");
        PreconditionUtils.checkArgument(runIdx >= 0, "启动命令必须是 docker run 形式：" + runCommand, HttpStatus.BAD_REQUEST);
        if (dockerIdx < 0) {
            // 用户只写了 run ...，在最前补 docker
            argv.add(runIdx, DOCKER_BIN);
            runIdx += 1;
        }
        // 补 -d（若未显式指定 detach）
        if (!argv.contains("-d") && !argv.contains("--detach")) {
            argv.add(runIdx + 1, "-d");
        }
        // 补 --name（若未指定）
        if (!argv.contains("--name")) {
            argv.add(runIdx + 1, "--name");
            argv.add(runIdx + 2, defaultName);
        }
        return argv;
    }

    /** 取实际生效的容器名：用户给了 --name 用其值，否则用 defaultName */
    private String effectiveNameFromArgv(List<String> argv, String defaultName) {
        int idx = argv.indexOf("--name");
        if (idx >= 0 && idx + 1 < argv.size()) {
            return argv.get(idx + 1);
        }
        return defaultName;
    }

    private static boolean containsNotFound(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("no such") || lower.contains("not found") || lower.contains("no such container");
    }

    private static int indexOf(List<String> list, String token) {
        for (int i = 0; i < list.size(); i++) {
            if (token.equalsIgnoreCase(list.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 尊重单/双引号的命令行切分（不处理反斜杠转义，够用于典型 docker run 命令）。
     */
    static List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean hasToken = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                hasToken = true;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                hasToken = true;
            } else if (Character.isWhitespace(c) && !inSingle && !inDouble) {
                if (hasToken) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                    hasToken = false;
                }
            } else {
                cur.append(c);
                hasToken = true;
            }
        }
        if (hasToken) {
            tokens.add(cur.toString());
        }
        return tokens;
    }

    private DockerResult exec(List<String> argv, int timeoutSeconds) {
        log.debug("执行 docker 命令：{}", argv);
        try {
            ProcessBuilder pb = new ProcessBuilder(argv);
            Process process = pb.start();
            // 后台线程读 stderr，主线程读 stdout，避免管道缓冲写满导致死锁
            ByteArrayOutputStream stderrBuf = new ByteArrayOutputStream();
            Thread errThread = new Thread(() -> copyBounded(process.getErrorStream(), stderrBuf));
            errThread.setDaemon(true);
            errThread.start();
            ByteArrayOutputStream stdoutBuf = new ByteArrayOutputStream();
            copyBounded(process.getInputStream(), stdoutBuf);
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("docker 命令执行超时：" + String.join(" ", argv), HttpStatus.BAD_REQUEST);
            }
            errThread.join(2000);
            String stdout = stdoutBuf.toString(StandardCharsets.UTF_8).trim();
            String stderr = stderrBuf.toString(StandardCharsets.UTF_8).trim();
            return new DockerResult(process.exitValue(), stdout, stderr);
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("docker 命令被中断", HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            throw new BusinessException("docker 命令执行失败（请确认已安装 docker 且进程有权限）：" + e.getMessage(),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private static void copyBounded(InputStream in, ByteArrayOutputStream out) {
        try (in) {
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) != -1) {
                if (total < MAX_OUTPUT_BYTES) {
                    int allow = Math.min(n, MAX_OUTPUT_BYTES - total);
                    out.write(buf, 0, allow);
                    total += allow;
                }
            }
        } catch (Exception e) {
            log.warn("读取 docker 输出失败：{}", e.getMessage());
        }
    }

    private static String firstLine(String text) {
        if (text == null) {
            return null;
        }
        int nl = text.indexOf('\n');
        return (nl >= 0 ? text.substring(0, nl) : text).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) {
                return v;
            }
        }
        return "";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
