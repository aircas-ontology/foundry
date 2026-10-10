package com.aircas.ptr.foundry.ontology.service.security;

import com.aircas.ptr.foundry.ontology.config.ScriptSecurityProperties;
import com.aircas.ptr.foundry.ontology.model.enums.ScriptTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SemgrepProcessRunner {

    private static final Duration TERMINATION_GRACE = Duration.ofSeconds(1);
    private static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(5);

    private final ScriptSecurityProperties properties;
    private final ProcessLauncher processLauncher;
    private final Semaphore concurrencyGuard;

    public SemgrepProcessRunner(ScriptSecurityProperties properties, ProcessLauncher processLauncher) {
        this.properties = properties;
        this.processLauncher = processLauncher;
        this.concurrencyGuard = new Semaphore(properties.getMaxConcurrentScans(), true);
    }

    public SemgrepProcessResult run(Path sourceDirectory, ScriptTypeEnum scriptType, String scanId) {
        long startedAt = System.nanoTime();
        boolean acquired = false;
        Process process = null;
        String containerName = "foundry-script-scan-" + UUID.randomUUID().toString().replace("-", "");
        try {
            acquired = concurrencyGuard.tryAcquire(properties.getAcquireTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                return failure(startedAt, "扫描并发已满", false, true);
            }

            List<String> command = buildCommand(sourceDirectory, scriptType, scanId, containerName);
            process = processLauncher.start(command);
            Process runningProcess = process;
            ExecutorService streamExecutor = Executors.newFixedThreadPool(2, daemonThreadFactory(scanId));
            try {
                Future<StreamReadResult> stdoutFuture = streamExecutor.submit(
                        () -> readLimited(runningProcess.getInputStream(), properties.getMaxOutputBytes()));
                Future<StreamReadResult> stderrFuture = streamExecutor.submit(
                        () -> readLimited(runningProcess.getErrorStream(), properties.getMaxOutputBytes()));

                boolean completed = runningProcess.waitFor(properties.getScanTimeout().toMillis(), TimeUnit.MILLISECONDS);
                if (!completed) {
                    terminate(runningProcess);
                    cleanupContainer(containerName);
                    awaitQuietly(stdoutFuture);
                    awaitQuietly(stderrFuture);
                    return failure(startedAt, "扫描超时", true, false);
                }

                StreamReadResult stdout = stdoutFuture.get();
                StreamReadResult stderr = stderrFuture.get();
                return SemgrepProcessResult.builder()
                        .exitCode(runningProcess.exitValue())
                        .stdout(stdout.content())
                        .stderr(stderr.content())
                        .outputTruncated(stdout.truncated() || stderr.truncated())
                        .durationMillis(elapsedMillis(startedAt))
                        .build();
            } finally {
                streamExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            cleanupContainer(containerName);
            return failure(startedAt, "扫描被中断", false, false);
        } catch (IOException exception) {
            cleanupContainer(containerName);
            return failure(startedAt, "扫描进程启动失败", false, false);
        } catch (ExecutionException exception) {
            cleanupContainer(containerName);
            return failure(startedAt, "扫描输出读取失败", false, false);
        } finally {
            if (acquired) {
                concurrencyGuard.release();
            }
        }
    }

    List<String> buildCommand(Path sourceDirectory, ScriptTypeEnum scriptType, String scanId, String containerName) {
        Path rulesDirectory = Path.of(properties.getRulesDir()).toAbsolutePath().normalize();
        Path sourcePath = sourceDirectory.toAbsolutePath().normalize();
        List<String> command = new ArrayList<>();
        command.add(properties.getDockerCommand());
        command.addAll(List.of(
                "run", "--rm",
                "--name", containerName,
                "--label", "foundry.script-scan=" + scanId,
                "--network", "none",
                "--read-only",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--pids-limit", String.valueOf(properties.getPidsLimit()),
                "--memory", properties.getMemory(),
                "--cpus", properties.getCpus(),
                "--user", properties.getUser(),
                "--env", "HOME=/tmp",
                "--tmpfs", properties.getTmpfs(),
                "--mount", "type=bind,src=" + sourcePath + ",dst=/src,readonly",
                "--mount", "type=bind,src=" + rulesDirectory + ",dst=/rules,readonly",
                properties.getSemgrepImage(),
                "semgrep", "scan",
                "--config", "/rules/" + scriptType.getRulesDirectory(),
                "--json",
                "--metrics", "off",
                "--disable-version-check",
                "--jobs", "1",
                "--timeout", String.valueOf(Math.max(1, properties.getScanTimeout().toSeconds())),
                "--max-memory", String.valueOf(properties.getSemgrepMaxMemoryMb()),
                "--max-target-bytes", String.valueOf(properties.getMaxCodeBytes()),
                "/src"));
        return List.copyOf(command);
    }

    private void terminate(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(TERMINATION_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor(TERMINATION_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void cleanupContainer(String containerName) {
        try {
            Process cleanup = processLauncher.start(List.of(
                    properties.getDockerCommand(), "rm", "--force", containerName));
            ExecutorService drainExecutor = Executors.newFixedThreadPool(2, daemonThreadFactory("cleanup"));
            try {
                Future<StreamReadResult> stdout = drainExecutor.submit(
                        () -> readLimited(cleanup.getInputStream(), 1024));
                Future<StreamReadResult> stderr = drainExecutor.submit(
                        () -> readLimited(cleanup.getErrorStream(), 1024));
                if (!cleanup.waitFor(CLEANUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    cleanup.destroyForcibly();
                }
                awaitQuietly(stdout);
                awaitQuietly(stderr);
            } finally {
                drainExecutor.shutdownNow();
            }
        } catch (Exception exception) {
            log.warn("Failed to clean the script scan container by its exact generated name");
        }
    }

    private StreamReadResult readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream stored = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (total < limit) {
                int writable = (int) Math.min(count, limit - total);
                stored.write(buffer, 0, writable);
            }
            total += count;
        }
        return new StreamReadResult(stored.toString(StandardCharsets.UTF_8), total > limit);
    }

    private void awaitQuietly(Future<?> future) {
        try {
            future.get(TERMINATION_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            future.cancel(true);
        }
    }

    private SemgrepProcessResult failure(long startedAt, String summary, boolean timedOut, boolean overloaded) {
        return SemgrepProcessResult.builder()
                .timedOut(timedOut)
                .overloaded(overloaded)
                .failureSummary(summary)
                .durationMillis(elapsedMillis(startedAt))
                .build();
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private ThreadFactory daemonThreadFactory(String scanId) {
        return runnable -> {
            Thread thread = new Thread(runnable, "script-scan-output-" + scanId);
            thread.setDaemon(true);
            return thread;
        };
    }

    private record StreamReadResult(String content, boolean truncated) {
    }
}
