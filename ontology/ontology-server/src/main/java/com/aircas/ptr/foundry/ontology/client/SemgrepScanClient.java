package com.aircas.ptr.foundry.ontology.client;

import com.aircas.ptr.foundry.ontology.config.SemgrepScanProperties;
import com.aircas.ptr.foundry.ontology.exception.SemgrepScanException;
import com.aircas.ptr.foundry.ontology.model.dto.scan.SemgrepCallResult;
import com.aircas.ptr.foundry.ontology.model.dto.scan.SemgrepScanRequest;
import com.aircas.ptr.foundry.ontology.model.dto.scan.SemgrepScanResponse;
import com.aircas.ptr.foundry.ontology.model.enums.ScanErrorCode;
import com.aircas.ptr.foundry.ontology.model.enums.ScanPurpose;
import com.aircas.ptr.foundry.ontology.model.enums.SemgrepScriptType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Semgrep HTTP 客户端。只负责传输、请求元数据及协议完整性校验。 */
@Slf4j
@Component
public class SemgrepScanClient {

    static final int MAX_CODE_BYTES = 256 * 1024;
    static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final String SCAN_PATH = "/api/v1/scan";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final Pattern HASH_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> REQUIRED_RESPONSE_FIELDS = Set.of(
            "scanId", "durationMs", "cached", "findingsTruncated", "findings");

    private final SemgrepScanProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final Clock clock;

    @Autowired
    public SemgrepScanClient(SemgrepScanProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, buildClient(properties), Clock.systemDefaultZone());
    }

    SemgrepScanClient(SemgrepScanProperties properties, ObjectMapper objectMapper,
                      OkHttpClient httpClient, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.clock = clock;
    }

    public SemgrepCallResult scan(SemgrepScriptType scriptType, String code, ScanPurpose purpose) {
        validateCode(code);
        if (scriptType == null) {
            throw new SemgrepScanException(ScanErrorCode.UNSUPPORTED_LANGUAGE);
        }
        if (purpose == null) {
            throw new SemgrepScanException(ScanErrorCode.INVALID_REQUEST);
        }
        String requestId = purpose.requestIdPrefix() + UUID.randomUUID();
        if (!REQUEST_ID_PATTERN.matcher(requestId).matches()) {
            throw new SemgrepScanException(ScanErrorCode.INVALID_REQUEST);
        }
        String codeHash = sha256(code);
        if (!HASH_PATTERN.matcher(codeHash).matches()) {
            throw new SemgrepScanException(ScanErrorCode.INVALID_REQUEST);
        }
        SemgrepScanRequest payload = new SemgrepScanRequest(requestId, scriptType, code, codeHash);

        try {
            String endpoint = properties.requiredBaseUrl() + SCAN_PATH;
            Request request = new Request.Builder()
                    .url(endpoint)
                    .post(RequestBody.create(objectMapper.writeValueAsBytes(payload), JSON))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                byte[] body = readBounded(response.body());
                handleHttpStatus(response.code(), body);
                SemgrepScanResponse scanResponse = parseSuccess(body);
                OffsetDateTime scannedAt = OffsetDateTime.now(clock);
                log.info("Semgrep scan completed requestId={} scanId={} codeHash={} durationMs={}",
                        requestId, scanResponse.scanId(), codeHash, scanResponse.durationMs());
                return new SemgrepCallResult(requestId, codeHash, scannedAt, scanResponse);
            }
        } catch (SemgrepScanException exception) {
            log.warn("Semgrep scan failed requestId={} codeHash={} errorCode={}",
                    requestId, codeHash, exception.getError().code());
            throw exception;
        } catch (InterruptedIOException exception) {
            log.warn("Semgrep scan timed out requestId={} codeHash={} errorCode={}",
                    requestId, codeHash, ScanErrorCode.SEMGREP_TIMEOUT.name());
            throw new SemgrepScanException(ScanErrorCode.SEMGREP_TIMEOUT, exception);
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            log.warn("Semgrep scan unavailable requestId={} codeHash={} errorCode={}",
                    requestId, codeHash, ScanErrorCode.SEMGREP_UNREACHABLE.name());
            throw new SemgrepScanException(ScanErrorCode.SEMGREP_UNREACHABLE, exception);
        }
    }

    public static String sha256(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private static OkHttpClient buildClient(SemgrepScanProperties properties) {
        return new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .build();
    }

    private void handleHttpStatus(int status, byte[] body) {
        if (status >= 200 && status < 300) {
            if (status != 200) {
                throw unexpectedStatus();
            }
            return;
        }
        switch (status) {
            case 400 -> throw mapBadRequest(body);
            case 404 -> throw new SemgrepScanException(ScanErrorCode.SEMGREP_ENDPOINT_NOT_FOUND);
            case 429 -> throw mapExpectedError(body, ScanErrorCode.SERVICE_BUSY);
            case 500 -> throw new SemgrepScanException(ScanErrorCode.SERVICE_FAILURE);
            case 503 -> throw mapServiceUnavailable(body);
            case 504 -> throw new SemgrepScanException(ScanErrorCode.SEMGREP_TIMEOUT);
            default -> throw unexpectedStatus();
        }
    }

    private SemgrepScanException mapExpectedError(byte[] body, ScanErrorCode expected) {
        String code = extractErrorCode(body);
        return code.isEmpty() || expected.name().equals(code)
                ? new SemgrepScanException(expected)
                : new SemgrepScanException(ScanErrorCode.INVALID_SCAN_RESPONSE);
    }

    private SemgrepScanException mapServiceUnavailable(byte[] body) {
        String code = extractErrorCode(body);
        return switch (code) {
            case "", "SERVICE_NOT_READY" -> new SemgrepScanException(ScanErrorCode.SERVICE_NOT_READY);
            case "RESOURCE_UNAVAILABLE" -> new SemgrepScanException(ScanErrorCode.RESOURCE_UNAVAILABLE);
            default -> new SemgrepScanException(ScanErrorCode.INVALID_SCAN_RESPONSE);
        };
    }

    private SemgrepScanException mapBadRequest(byte[] body) {
        String code = extractErrorCode(body);
        return switch (code) {
            case "INVALID_REQUEST" -> new SemgrepScanException(ScanErrorCode.INVALID_REQUEST);
            case "UNSUPPORTED_LANGUAGE" -> new SemgrepScanException(ScanErrorCode.UNSUPPORTED_LANGUAGE);
            case "CODE_HASH_MISMATCH" -> new SemgrepScanException(ScanErrorCode.CODE_HASH_MISMATCH);
            default -> new SemgrepScanException(ScanErrorCode.INVALID_SCAN_RESPONSE);
        };
    }

    private SemgrepScanException unexpectedStatus() {
        return new SemgrepScanException(ScanErrorCode.INVALID_SCAN_RESPONSE,
                "安全扫描服务返回非预期状态");
    }

    private String extractErrorCode(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root == null ? null : root.get("error");
            JsonNode code = error == null ? null : error.get("code");
            return code != null && code.isTextual() ? code.textValue() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private SemgrepScanResponse parseSuccess(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            validateResponseShape(root);
            SemgrepScanResponse response = objectMapper.treeToValue(root, SemgrepScanResponse.class);
            validateResponseValues(response);
            return response;
        } catch (SemgrepScanException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SemgrepScanException(ScanErrorCode.INVALID_SCAN_RESPONSE, exception);
        }
    }

    private void validateResponseShape(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new SemgrepScanException(ScanErrorCode.INVALID_SCAN_RESPONSE);
        }
        for (String field : REQUIRED_RESPONSE_FIELDS) {
            if (!root.has(field)) {
                throw new SemgrepScanException(ScanErrorCode.INVALID_SCAN_RESPONSE);
            }
        }
        JsonNode error = root.get("error");
        if (!root.path("scanId").isTextual()
                || !root.path("durationMs").canConvertToLong()
                || !root.path("cached").isBoolean()
                || !root.path("findingsTruncated").isBoolean()
                || !root.path("findings").isArray()
                || error != null && !(error.isNull() || error.isObject())) {
            throw new SemgrepScanException(ScanErrorCode.INVALID_SCAN_RESPONSE);
        }
    }

    private void validateResponseValues(SemgrepScanResponse response) {
        if (response.scanId() == null || response.scanId().isBlank()
                || response.durationMs() == null || response.durationMs() < 0
                || response.cached() == null || response.cached()
                || response.findingsTruncated() == null
                || response.findings() == null || response.findings().size() > 200
                || response.error() != null
                && (response.error().code() == null || response.error().code().isBlank())) {
            throw new SemgrepScanException(ScanErrorCode.INVALID_SCAN_RESPONSE);
        }
    }

    private byte[] readBounded(ResponseBody body) throws IOException {
        if (body == null) {
            throw new SemgrepScanException(ScanErrorCode.INVALID_SCAN_RESPONSE);
        }
        try (InputStream input = body.byteStream()) {
            byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new SemgrepScanException(ScanErrorCode.REPORT_TOO_LARGE);
            }
            return bytes;
        }
    }

    private static void validateCode(String code) {
        if (code == null || code.isBlank() || code.indexOf('\0') >= 0
                || code.getBytes(StandardCharsets.UTF_8).length > MAX_CODE_BYTES) {
            throw new SemgrepScanException(ScanErrorCode.INVALID_REQUEST);
        }
    }
}
