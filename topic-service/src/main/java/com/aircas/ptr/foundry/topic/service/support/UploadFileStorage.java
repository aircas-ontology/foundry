package com.aircas.ptr.foundry.topic.service.support;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.topic.TopicServerApplication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 原始文件存放在 topic.jar 同级的 upload 目录。从 IDE 启动时使用当前工作目录。
 */
@Component
public class UploadFileStorage {

    public Path save(MultipartFile file, String storedName) {
        Path directory = directory();
        Path target = directory.resolve(storedName).normalize();
        if (!target.startsWith(directory)) {
            throw new BusinessException("文件名称不合法", HttpStatus.BAD_REQUEST);
        }
        try {
            file.transferTo(target);
            return target;
        } catch (IOException ex) {
            throw new BusinessException("文件保存失败", HttpStatus.BAD_REQUEST);
        }
    }

    public void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 保存失败后的清理不影响原始错误返回
        }
    }

    private Path directory() {
        Path base = jarDirectory();
        Path upload = base.resolve("upload").toAbsolutePath().normalize();
        try {
            Files.createDirectories(upload);
            return upload;
        } catch (IOException ex) {
            throw new BusinessException("上传目录创建失败", HttpStatus.BAD_REQUEST);
        }
    }

    private Path jarDirectory() {
        try {
            URI uri = TopicServerApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path source = Path.of(uri).toAbsolutePath().normalize();
            if (Files.isRegularFile(source)) {
                return source.getParent();
            }
        } catch (Exception ignored) {
            // 无法定位 jar 时退回启动目录
        }
        return Path.of("").toAbsolutePath().normalize();
    }
}
