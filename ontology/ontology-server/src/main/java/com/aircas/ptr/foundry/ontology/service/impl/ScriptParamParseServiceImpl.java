package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.dto.ScriptParseOutcome;
import com.aircas.ptr.foundry.ontology.model.dto.ScriptParsedParam;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScriptParseStatus;
import com.aircas.ptr.foundry.ontology.model.param.ScriptParseParam;
import com.aircas.ptr.foundry.ontology.model.vo.ScriptParseResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.ScriptParsedParamVO;
import com.aircas.ptr.foundry.ontology.service.ScriptParamParseService;
import com.aircas.ptr.foundry.ontology.service.parser.ScriptParamParser;
import com.aircas.ptr.foundry.ontology.utils.ScriptParamSchemaUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 脚本参数解析服务实现：按语言路由到对应解析器（Java/Groovy 进程内 AST，Python/TS 走 runner），
 * 解析结果按 category 分组、组内自动编号后返回。
 */
@Slf4j
@Service
public class ScriptParamParseServiceImpl implements ScriptParamParseService {

    private final Map<FunctionLanguageEnum, ScriptParamParser> router = new EnumMap<>(FunctionLanguageEnum.class);

    private final List<ScriptParamParser> parsers;

    public ScriptParamParseServiceImpl(List<ScriptParamParser> parsers) {
        this.parsers = parsers;
    }

    @PostConstruct
    public void init() {
        for (ScriptParamParser parser : parsers) {
            for (FunctionLanguageEnum language : parser.supports()) {
                router.put(language, parser);
            }
        }
    }

    @Override
    public ScriptParseResultVO parseParams(ScriptParseParam param) {
        FunctionLanguageEnum language = param.getLanguage();
        String code = param.getCode();
        if (code == null || code.isBlank()) {
            throw new BusinessException("code 不能为空");
        }
        ScriptParamParser parser = router.get(language);
        if (parser == null) {
            throw new BusinessException("暂不支持该语言的参数解析：" + (language == null ? "未知" : language.getCode()));
        }
        ScriptParseOutcome outcome;
        try {
            outcome = parser.parse(language, code);
        } catch (Exception e) {
            log.warn("脚本参数解析异常：language={}, cause={}", language.getCode(), e.getMessage(), e);
            outcome = ScriptParseOutcome.of(ScriptParseStatus.PARSE_EXCEPTION, e.getMessage());
        }
        return toVO(outcome, language);
    }

    private ScriptParseResultVO toVO(ScriptParseOutcome outcome, FunctionLanguageEnum language) {
        List<ScriptParsedParamVO> inputs = new ArrayList<>();
        List<ScriptParsedParamVO> outputs = new ArrayList<>();
        int inputOrder = 1;
        int outputOrder = 1;
        for (ScriptParsedParam p : outcome.getParams()) {
            if (p.getName() == null || p.getName().isBlank()) {
                continue;
            }
            if (p.getCategory() == FunctionParamCategoryEnum.OUTPUT) {
                outputs.add(toItemVO(p, outputOrder++, language));
            } else {
                inputs.add(toItemVO(p, inputOrder++, language));
            }
        }
        // 与保存接口 parameters 结构一致：入参在前、出参在后，合成单个扁平列表
        List<ScriptParsedParamVO> parameters = new ArrayList<>(inputs);
        parameters.addAll(outputs);
        ScriptParseStatus status = outcome.getStatus();
        boolean hasParams = !parameters.isEmpty();
        boolean parsedOk = status == ScriptParseStatus.OK && hasParams;
        String message;
        if (status == ScriptParseStatus.OK) {
            message = hasParams ? null : "已定位 handle 入口，但脚本为完全动态写法，未能静态解析出参数，请手动声明";
        } else {
            String raw = outcome.getMessage();
            message = (raw != null && !raw.isBlank()) ? raw : status.getDescription();
        }
        return ScriptParseResultVO.builder()
                .status(status)
                .parsed(parsedOk)
                .parameters(parameters)
                .message(message)
                .build();
    }

    private ScriptParsedParamVO toItemVO(ScriptParsedParam p, int order, FunctionLanguageEnum language) {
        FunctionParamCategoryEnum category =
                p.getCategory() == null ? FunctionParamCategoryEnum.INPUT : p.getCategory();
        ScriptParsedParamVO.ScriptParsedParamVOBuilder builder = ScriptParsedParamVO.builder()
                .paramName(p.getName())
                .paramType(p.getType())
                .category(category)
                .paramOrder(order)
                .referenceType(referenceType(p, language));
                // paramSchema/description 不产出（null）：前端保存请求本就不传这两项，由用户自行补充
        if (p.getFields() != null && !p.getFields().isEmpty()) {
            List<ScriptParsedParamVO> fieldVOs = new ArrayList<>();
            for (int i = 0; i < p.getFields().size(); i++) {
                fieldVOs.add(toItemVO(p.getFields().get(i), i + 1, language));
            }
            builder.fields(fieldVOs);
        }
        return builder.build();
    }

    /**
     * referenceType 口径：仅 JVM 语言（Java/Groovy）填真实全限定类名（执行链路用它做类型转换）；
     * Python/TS 无 Java 类概念，置空，执行链路（buildInputMap）对空值按原始 JSON 透传，不改变执行语义。
     * JVM 启发推断项（无类型声明）按枚举兜底：OBJECT → java.lang.Object，标量 → 对应装箱类名。
     */
    private String referenceType(ScriptParsedParam p, FunctionLanguageEnum language) {
        if (language != FunctionLanguageEnum.JAVA && language != FunctionLanguageEnum.GROOVY) {
            return "";
        }
        if (p.getRawType() != null && !p.getRawType().isBlank()) {
            return ScriptParamSchemaUtil.jvmReferenceType(p.getRawType());
        }
        return ScriptParamSchemaUtil.jvmReferenceType(p.getType() == null ? "" : p.getType().getVale());
    }
}
