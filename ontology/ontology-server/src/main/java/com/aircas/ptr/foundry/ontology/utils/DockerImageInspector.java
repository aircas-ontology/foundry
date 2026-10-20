package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * docker save 导出的镜像 tar 包解析工具：读取镜像 config，提取架构（architecture）与标签（RepoTags），
 * 用于容器算子上传镜像时的 ARM64 架构校验。
 * <p>
 * 兼容 {@code .tar} 与 {@code .tar.gz/.tgz}；层内容只做流式跳过不加载进内存，仅保留少量 JSON。</p>
 */
@Slf4j
public final class DockerImageInspector {

    private DockerImageInspector() {
    }

    /** 架构解析失败时的兜底标识；上层 ARM 校验据此判定非法（本地关闭 ARM 校验时可放行） */
    public static final String UNKNOWN_ARCH = "unknown";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** manifest.json 在 tar 中的固定名 */
    private static final String MANIFEST_JSON = "manifest.json";
    /** OCI layout 索引文件 */
    private static final String INDEX_JSON = "index.json";

    /**
     * 解析镜像 tar，返回架构与标签。文件无法解析或缺失 config 时抛业务异常。
     */
    public static ImageMeta inspect(Path tarPath) {
        Map<String, byte[]> jsonEntries = new LinkedHashMap<>();
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(tarPath));
             TarArchiveInputStream tar = openTar(raw)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                // 仅缓存体积较小的 JSON 元数据（manifest/config/index），大层直接跳过
                if (name.endsWith(".json") && entry.getSize() < 8 * 1024 * 1024) {
                    jsonEntries.put(normalize(name), readFully(tar));
                } else {
                    // 消费掉该 entry 以便流前进（不保留内容）
                    skip(tar);
                }
            }
        } catch (IOException e) {
            throw new BusinessException("镜像包解析失败，请确认是 docker save 导出的 tar/tar.gz：" + e.getMessage());
        }

        byte[] manifest = jsonEntries.get(MANIFEST_JSON);
        if (manifest != null) {
            return fromClassicManifest(manifest, jsonEntries);
        }
        byte[] index = jsonEntries.get(INDEX_JSON);
        if (index != null) {
            return fromOciIndex(index, jsonEntries);
        }
        throw new BusinessException("镜像包中未找到 manifest.json / index.json，无法识别架构");
    }

    /**
     * 经典 docker save：manifest.json = [{Config, RepoTags, Layers}]
     * <p>RepoTags 与 architecture 解耦：架构解析失败仅返回 {@link #UNKNOWN_ARCH}，不影响 tag 落地，
     * 避免运行期因 tag 缺失而跳过 {@code docker load}。</p>
     */
    private static ImageMeta fromClassicManifest(byte[] manifest, Map<String, byte[]> jsonEntries) {
        try {
            JsonNode root = MAPPER.readTree(manifest);
            JsonNode first = root.isArray() && root.size() > 0 ? root.get(0) : root;
            List<String> repoTags = readRepoTags(first.get("RepoTags"));
            String configName = text(first.get("Config"));
            // Config 可能是 blobs/sha256/xxx.json 形式，做 basename 匹配
            byte[] configBytes = lookupJson(jsonEntries, configName);
            String architecture = readArchitectureQuietly(configBytes, "manifest");
            return new ImageMeta(architecture, repoTags);
        } catch (IOException e) {
            throw new BusinessException("镜像 manifest.json 解析失败：" + e.getMessage());
        }
    }

    /**
     * OCI layout：index.json 的 manifests 指向 config blob（多平台时取首个）。
     * <p>同样解耦 tag 与架构：架构解析失败仅记 unknown。</p>
     */
    private static ImageMeta fromOciIndex(byte[] index, Map<String, byte[]> jsonEntries) {
        try {
            JsonNode root = MAPPER.readTree(index);
            JsonNode manifests = root.get("manifests");
            if (manifests == null || !manifests.isArray() || manifests.isEmpty()) {
                throw new BusinessException("OCI index.json 无 manifests，无法识别架构");
            }
            for (JsonNode m : manifests) {
                String mediaType = text(m.get("mediaType"));
                // 跳过嵌套的 index（多平台清单），只取 image manifest
                if (mediaType != null && mediaType.contains("index")) {
                    continue;
                }
                JsonNode annotations = m.get("annotations");
                List<String> repoTags = new ArrayList<>();
                if (annotations != null && annotations.has("org.opencontainers.image.ref.name")) {
                    repoTags.add(annotations.get("org.opencontainers.image.ref.name").asText());
                }
                // config.digest 指向 blobs/sha256/<digest>，直接读该 blob 拿 architecture
                JsonNode config = m.get("config");
                String digest = config != null ? text(config.get("digest")) : null;
                String arch = readArchitectureQuietly(lookupBlob(jsonEntries, digest), "oci");
                return new ImageMeta(arch, repoTags);
            }
            throw new BusinessException("OCI index.json 未找到镜像清单，无法识别架构");
        } catch (IOException e) {
            throw new BusinessException("OCI index.json 解析失败：" + e.getMessage());
        }
    }

    /** 尽力读取架构；config 缺失或字段非法时返回 {@link #UNKNOWN_ARCH} 而非抛异常，保留 tag 落地 */
    private static String readArchitectureQuietly(byte[] configBytes, String flavor) {
        if (configBytes == null) {
            log.warn("镜像 {} 清单指向的 config 缺失，架构按 unknown 处理", flavor);
            return UNKNOWN_ARCH;
        }
        try {
            return readArchitecture(configBytes);
        } catch (Exception e) {
            log.warn("镜像 {} config 架构解析失败（{}），架构按 unknown 处理", flavor, e.getMessage());
            return UNKNOWN_ARCH;
        }
    }

    private static String readArchitecture(byte[] configBytes) throws IOException {
        if (configBytes == null) {
            throw new BusinessException("镜像 config 缺失，无法识别架构");
        }
        JsonNode cfg = MAPPER.readTree(configBytes);
        String architecture = text(cfg.get("architecture"));
        if (architecture == null || architecture.isBlank()) {
            throw new BusinessException("镜像 config 未包含 architecture 字段");
        }
        return architecture.toLowerCase();
    }

    private static List<String> readRepoTags(JsonNode node) {
        List<String> tags = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(t -> tags.add(t.asText()));
        }
        return tags;
    }

    /** 按 config 路径在缓存的 json 中查找：先精确，再按 basename，再按包含 */
    private static byte[] lookupJson(Map<String, byte[]> jsonEntries, String configName) {
        if (configName == null) {
            return null;
        }
        String normalized = normalize(configName);
        if (jsonEntries.containsKey(normalized)) {
            return jsonEntries.get(normalized);
        }
        String base = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
        for (Map.Entry<String, byte[]> e : jsonEntries.entrySet()) {
            if (e.getKey().equals(base) || e.getKey().endsWith("/" + base)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** OCI blob 以 digest 末段（sha256:xxx 的 xxx）命名，按后缀匹配 */
    private static byte[] lookupBlob(Map<String, byte[]> jsonEntries, String digest) {
        if (digest == null) {
            return null;
        }
        String hex = digest.contains(":") ? digest.substring(digest.indexOf(':') + 1) : digest;
        for (Map.Entry<String, byte[]> e : jsonEntries.entrySet()) {
            if (e.getKey().contains(hex)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static TarArchiveInputStream openTar(InputStream in) throws IOException {
        in.mark(2);
        int b1 = in.read();
        int b2 = in.read();
        in.reset();
        boolean gzip = b1 == 0x1f && b2 == 0x8b;
        InputStream stream = gzip ? new GzipCompressorInputStream(in) : in;
        return new TarArchiveInputStream(new BufferedInputStream(stream));
    }

    private static String normalize(String name) {
        return name.startsWith("./") ? name.substring(2) : name;
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
        return out.toByteArray();
    }

    private static void skip(InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        while (in.read(buf) != -1) {
            // 丢弃内容，仅前进流
        }
    }

    /**
     * 镜像元数据。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageMeta {
        private String architecture;
        private List<String> repoTags;
    }
}
