package com.aircas.ptr.foundry.runner.web;

import com.aircas.ptr.foundry.runner.config.RunnerProperties;
import com.aircas.ptr.foundry.runner.lang.ScriptCompileException;
import com.aircas.ptr.foundry.runner.lang.ScriptRunner;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runner 对外统一接口：{@code /health}、{@code /validate}、{@code /functions/handle}。
 * <p>
 * 本容器只服务一种语言（由 {@code runner.language} 决定的唯一 {@link ScriptRunner} 生效）。
 */
@RestController
public class RunnerController {

    private static final Logger log = LoggerFactory.getLogger(RunnerController.class);

    private final ScriptRunner scriptRunner;

    private final RunnerProperties properties;

    public RunnerController(ScriptRunner scriptRunner, RunnerProperties properties) {
        this.scriptRunner = scriptRunner;
        this.properties = properties;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "UP");
        resp.put("language", scriptRunner.language());
        return resp;
    }

    @PostMapping("/validate")
    public Map<String, Object> validate(@RequestBody ValidateRequest request) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            scriptRunner.validate(request.getCode());
            resp.put("ok", true);
        } catch (ScriptCompileException e) {
            resp.put("ok", false);
            resp.put("error", e.getMessage());
        } catch (Exception e) {
            log.error("validate failed", e);
            resp.put("ok", false);
            resp.put("error", e.getMessage());
        }
        return resp;
    }

    @PostMapping("/functions/handle")
    public Map<String, Object> handle(@RequestBody HandleRequest request) {
        long start = System.currentTimeMillis();
        Map<String, Object> input = request.getInput() == null ? new HashMap<>() : request.getInput();
        Object result = scriptRunner.execute(request.getCode(), input);

        Map<String, Object> resp = new LinkedHashMap<>(normalizeResult(result));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("durationMs", System.currentTimeMillis() - start);
        meta.put("status", "ok");
        meta.put("language", scriptRunner.language());
        resp.put("_meta", meta);
        return resp;
    }

    /**
     * 归一化脚本返回值为 FunctionResultVO 形态（{data,description,taskId,...}）：
     * <ul>
     *   <li>老签名返回 {@link FunctionResultVO}：展开其核心字段，避免二次包进 data；</li>
     *   <li>新契约返回 Map：视为已是 FunctionResultVO 形态，原样展开；</li>
     *   <li>其他裸值：包进 data。</li>
     * </ul>
     */
    private static Map<String, Object> normalizeResult(Object result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        if (result instanceof FunctionResultVO<?> vo) {
            resp.put("data", vo.getData());
            resp.put("description", vo.getDescription());
            resp.put("taskId", vo.getTaskId());
            resp.put("startTime", vo.getStartTime());
            resp.put("endTime", vo.getEndTime());
        } else if (result instanceof Map) {
            resp.putAll(castMap(result));
        } else {
            resp.put("data", result);
        }
        return resp;
    }

    /**
     * 脚本编译/执行失败：返回 400 + 错误信息，主应用侧 HttpUtil 会将其转为业务异常并带上响应体。
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(ScriptCompileException.class)
    public ResponseEntity<Map<String, Object>> onScriptError(ScriptCompileException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("error", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object result) {
        return (Map<String, Object>) result;
    }

}
