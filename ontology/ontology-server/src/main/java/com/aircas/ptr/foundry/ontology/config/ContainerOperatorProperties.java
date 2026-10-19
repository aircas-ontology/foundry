package com.aircas.ptr.foundry.ontology.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 容器算子配置：镜像 / 接口文档的存储目录与上传大小限制。
 * <p>
 * 目录留空时默认落在 jar 包同级目录下（开发环境回退到工作目录 user.dir）：
 * 镜像 → {@code container-images/}，接口文档 → {@code interface-docs/}。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "container-operator")
public class ContainerOperatorProperties {

    /** 镜像存储目录；留空默认 jar 同级 container-images/ */
    private String imageDir;

    /** 接口文档存储目录；留空默认 jar 同级 interface-docs/ */
    private String docDir;

    /** 镜像上传大小上限（MB） */
    private int maxImageSizeMb = 2048;

    /**
     * 是否启用 ARM64 镜像架构校验。默认 true；
     * 无 ARM 环境时可在 application-local.yml 配 container-operator.arm-check-enabled=false 跳过校验（仅测试用）。
     */
    private boolean armCheckEnabled = true;

    /**
     * 解析镜像存储目录（不存在则创建）。
     */
    public Path resolveImageDir() {
        return resolveDir(imageDir, "container-images");
    }

    /**
     * 解析接口文档存储目录（不存在则创建）。
     */
    public Path resolveDocDir() {
        return resolveDir(docDir, "interface-docs");
    }

    private Path resolveDir(String configured, String defaultSubDir) {
        Path base;
        if (configured != null && !configured.isBlank()) {
            base = Paths.get(configured);
        } else {
            base = appHomeDir().resolve(defaultSubDir);
        }
        base.toFile().mkdirs();
        return base;
    }

    /**
     * 应用运行根目录：优先 jar 所在目录，取不到（如在 classes 目录里跑）回退 user.dir。
     */
    private static Path appHomeDir() {
        try {
            File jarFile = new File(ContainerOperatorProperties.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File parent = jarFile.isFile() ? jarFile.getParentFile() : jarFile;
            return parent.toPath();
        } catch (URISyntaxException | IllegalStateException e) {
            return Paths.get(System.getProperty("user.dir"));
        }
    }

}
