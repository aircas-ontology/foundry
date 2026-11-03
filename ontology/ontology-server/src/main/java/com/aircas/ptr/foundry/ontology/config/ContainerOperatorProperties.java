package com.aircas.ptr.foundry.ontology.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 容器算子配置：镜像 / 接口文档的存储目录与上传大小限制，以及本节点的对外可达地址。
 * <p>
 * 目录留空时默认落在 jar 包同级目录下（开发环境回退到工作目录 user.dir）：
 * 镜像 → {@code container-images/}，接口文档 → {@code interface-docs/}。</p>
 * <p>{@code node.ip} 决定容器算子是否启用「节点归属」模式：镜像 tar 与 docker 容器都只在收到上传请求
 * 那台机器的本地磁盘 / 本机 docker 上，多实例部署时必须为每台机器配各自内网可达 IP，
 * 启停控制请求才会被自动转发到归属节点、执行期才会按归属节点 IP 访问容器端口。
 * 留空则保持单节点行为（不记录归属、不转发）。</p>
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
     * 是否启用镜像架构校验。默认 true；
     * 置 false 直接跳过校验（此时 {@link #allowedArchitectures} 不参与判定）。
     */
    private boolean armCheckEnabled = true;

    /**
     * 允许的镜像架构，取值对应 docker save tar 内 config.architecture（大小写不敏感）。
     * <p>默认同时放行 arm64 与 amd64，因为本项目两种部署都存在（见 deploy/script-runners/arm64/）。
     * 按实际部署机器收窄更有意义，例如 x86 环境配 {@code container-operator.allowed-architectures: amd64}，
     * 这样传上来的 arm64 镜像会在上传阶段就被挡住，而不是等到 docker run 才报 exec format error。</p>
     * <p>清单为空视为不限制架构。</p>
     */
    private List<String> allowedArchitectures = new ArrayList<>(List.of("arm64", "amd64"));

    /**
     * 执行期访问容器映射端口的基地址（ontology-server 可达的宿主机地址）。
     * 同宿主机默认 127.0.0.1；若 ontology-server 本身跑在容器内且非 host 网络，需改为宿主机 IP。
     */
    private String execHost = "127.0.0.1";

    /** 容器接口 HTTP 调用超时秒数 */
    private int execTimeoutSeconds = 30;

    /**
     * 本节点归属配置：多实例部署时每台机器在本地配置里写各自的对外可达地址。
     * <p>{@code ip} 必须是对等节点与容器端口都能访问到的内网 IP，写 127.0.0.1 会导致转发连不通。</p>
     */
    private Node node = new Node();

    /**
     * 跨节点转发超时秒数。
     * <p>需明显大于 {@code DockerCommandExecutor} 单条 docker 命令的 120s 超时
     * （restart 最坏 = stop + rm + load + run 四条），否则本端已超时返回、归属节点还在跑。</p>
     */
    private int forwardTimeoutSeconds = 600;

    /** 是否启用节点归属模式（配置了本节点可达 IP 才启用；留空保持原单节点行为） */
    public boolean isMultiNode() {
        return node != null && node.getIp() != null && !node.getIp().isBlank();
    }

    /** 本节点对外可达地址配置 */
    @Data
    public static class Node {

        /** 本节点内网可达 IP；留空 = 单节点模式，不记录归属也不转发 */
        private String ip;

        /** 本节点 java 进程对外端口；留空取 server.port（部署为 host 网络时二者相同，仅在端口映射场景需要覆盖） */
        private Integer port;
    }

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
     * <p>用 Spring Boot 的 {@link ApplicationHome} 解析：它能把 fat jar 的嵌套
     * {@code jar:file:...!/...} URI 正确还原为外层 jar 所在目录；而 {@code new File(uri)}
     * 对这种非层次化 URI 会抛 {@code IllegalArgumentException: URI is not hierarchical}。</p>
     */
    private static Path appHomeDir() {
        try {
            File dir = new ApplicationHome(ContainerOperatorProperties.class).getDir();
            if (dir != null) {
                return dir.toPath();
            }
        } catch (RuntimeException e) {
            // 解析失败则回退工作目录
        }
        return Paths.get(System.getProperty("user.dir"));
    }

}
