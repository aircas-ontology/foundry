package com.aircas.ptr.foundry.ontology.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ScriptSecurityRuleSetValidator {

    private final ScriptSecurityProperties properties;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void validate() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            Path root = Path.of(properties.getRulesDir()).toAbsolutePath().normalize();
            JsonNode manifest = objectMapper.readTree(root.resolve("manifest.json").toFile());
            requireEqual("ruleSetVersion", properties.getRuleSetVersion(), manifest.path("ruleSetVersion").asText());
            requireEqual("semgrepImage", properties.getSemgrepImage(), manifest.path("semgrepImage").asText());
            JsonNode files = manifest.path("files");
            if (!files.isObject() || files.size() == 0) {
                throw new IllegalStateException("script security rule manifest has no checksums");
            }
            Set<Path> declaredRuleFiles = new HashSet<>();
            Iterator<Map.Entry<String, JsonNode>> entries = files.properties().iterator();
            while (entries.hasNext()) {
                Map.Entry<String, JsonNode> entry = entries.next();
                Path ruleFile = root.resolve(entry.getKey()).normalize();
                if (!ruleFile.startsWith(root) || !Files.isRegularFile(ruleFile)) {
                    throw new IllegalStateException("script security rule manifest contains an invalid file");
                }
                declaredRuleFiles.add(ruleFile);
                String actual = sha256(ruleFile);
                if (!actual.equalsIgnoreCase(entry.getValue().asText())) {
                    throw new IllegalStateException("script security rule checksum validation failed");
                }
            }
            for (String directory : new String[]{"python", "typescript", "groovy-generic"}) {
                try (var rulePaths = Files.walk(root.resolve(directory))) {
                    boolean hasUndeclaredRule = rulePaths
                            .filter(Files::isRegularFile)
                            .anyMatch(path -> !declaredRuleFiles.contains(path.toAbsolutePath().normalize()));
                    if (hasUndeclaredRule) {
                        throw new IllegalStateException("script security rule directory contains an undeclared file");
                    }
                }
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("script security rule manifest validation failed", exception);
        }
    }

    private void requireEqual(String field, String configured, String manifestValue) {
        if (!configured.equals(manifestValue)) {
            throw new IllegalStateException("script security " + field + " does not match manifest");
        }
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
