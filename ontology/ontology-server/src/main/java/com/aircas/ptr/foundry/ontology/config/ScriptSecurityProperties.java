package com.aircas.ptr.foundry.ontology.config;

import com.aircas.ptr.foundry.ontology.model.enums.ScriptRiskSeverityEnum;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "script-security")
public class ScriptSecurityProperties {

    private boolean enabled = false;
    private boolean failClosed = true;
    @Min(1)
    private int maxCodeBytes = 262_144;
    @NotNull
    private Duration scanTimeout = Duration.ofSeconds(15);
    @Min(1)
    private int maxOutputBytes = 2_097_152;
    @Min(1)
    private int maxConcurrentScans = 4;
    @NotNull
    private Duration acquireTimeout = Duration.ofSeconds(2);
    private String rulesDir = "/app/semgrep-rules";
    private String ruleSetVersion = "";
    private String semgrepImage = "";
    private String dockerCommand = "docker";
    @Min(1)
    private int pidsLimit = 64;
    private String memory = "256m";
    private String cpus = "0.5";
    private String user = "65534:65534";
    private String tmpfs = "/tmp:rw,noexec,nosuid,size=64m";
    @Min(1)
    private int semgrepMaxMemoryMb = 256;
    @Min(1)
    private int maxFindings = 100;
    @NotNull
    private Set<ScriptRiskSeverityEnum> blockSeverities = EnumSet.of(
            ScriptRiskSeverityEnum.ERROR,
            ScriptRiskSeverityEnum.WARNING);

    @PostConstruct
    public void validateConfiguration() {
        if (scanTimeout == null || scanTimeout.isZero() || scanTimeout.isNegative()) {
            throw new IllegalStateException("script-security.scan-timeout must be positive");
        }
        if (acquireTimeout == null || acquireTimeout.isZero() || acquireTimeout.isNegative()) {
            throw new IllegalStateException("script-security.acquire-timeout must be positive");
        }
        if (!enabled) {
            return;
        }
        if (StringUtils.isAnyBlank(rulesDir, ruleSetVersion, semgrepImage, dockerCommand)) {
            throw new IllegalStateException("enabled script security requires rules-dir, rule-set-version, semgrep-image and docker-command");
        }
        String imageName = semgrepImage.toLowerCase(Locale.ROOT);
        String finalSegment = imageName.substring(imageName.lastIndexOf('/') + 1);
        if (imageName.endsWith(":latest") || "latest".equals(imageName)
                || (!imageName.contains("@sha256:") && !finalSegment.contains(":"))) {
            throw new IllegalStateException("script-security.semgrep-image must use a fixed tag or digest");
        }
        Path rulesPath = Path.of(rulesDir);
        if (!Files.isDirectory(rulesPath) || !Files.isReadable(rulesPath)) {
            throw new IllegalStateException("script-security.rules-dir must be a readable directory");
        }
    }
}
