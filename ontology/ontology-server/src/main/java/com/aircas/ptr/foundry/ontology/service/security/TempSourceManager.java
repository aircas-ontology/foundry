package com.aircas.ptr.foundry.ontology.service.security;

import com.aircas.ptr.foundry.ontology.config.ScriptSecurityProperties;
import com.aircas.ptr.foundry.ontology.model.enums.ScriptTypeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.EnumSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class TempSourceManager {

    private final ScriptSecurityProperties properties;

    public TempSource create(ScriptTypeEnum scriptType, String code) throws IOException {
        byte[] content = code.getBytes(StandardCharsets.UTF_8);
        if (content.length > properties.getMaxCodeBytes()) {
            throw new IllegalArgumentException("脚本大小超过限制");
        }
        Path directory = Files.createTempDirectory("foundry-script-scan-");
        try {
            setOwnerOnlyPermissions(directory, true);
            Path sourceFile = directory.resolve(scriptType.getSourceFileName());
            Files.write(sourceFile, content);
            setContainerReadablePermissions(sourceFile, false);
            setContainerReadablePermissions(directory, true);
            return new TempSource(directory, sourceFile);
        } catch (Exception exception) {
            deleteRecursively(directory);
            throw exception;
        }
    }

    private void setOwnerOnlyPermissions(Path path, boolean directory) {
        try {
            EnumSet<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            if (directory) {
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
            }
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows 等非 POSIX 文件系统由操作系统 ACL 管理。
        }
    }

    private void setContainerReadablePermissions(Path path, boolean directory) {
        try {
            EnumSet<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.OTHERS_READ);
            if (directory) {
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
                permissions.add(PosixFilePermission.GROUP_EXECUTE);
                permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            }
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows 等非 POSIX 文件系统由操作系统 ACL 管理。
        }
    }

    private static void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try {
            Files.setPosixFilePermissions(directory, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows 等非 POSIX 文件系统由操作系统 ACL 管理。
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    log.warn("Failed to clean script scan temporary file");
                }
            });
        } catch (IOException exception) {
            log.warn("Failed to clean script scan temporary directory");
        }
    }

    @Getter
    public static final class TempSource implements AutoCloseable {
        private final Path directory;
        private final Path sourceFile;

        private TempSource(Path directory, Path sourceFile) {
            this.directory = directory;
            this.sourceFile = sourceFile;
        }

        @Override
        public void close() {
            deleteRecursively(directory);
        }
    }
}
