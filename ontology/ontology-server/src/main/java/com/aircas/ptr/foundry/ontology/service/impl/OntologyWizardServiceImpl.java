package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.dto.ColumnMetaDTO;
import com.aircas.ptr.foundry.ontology.model.dto.LlmObjectDefinitionDTO;
import com.aircas.ptr.foundry.ontology.model.dto.LlmWizardListResponseDTO;
import com.aircas.ptr.foundry.ontology.model.dto.LlmWizardRelationDTO;
import com.aircas.ptr.foundry.ontology.model.dto.TableMetaDTO;
import com.aircas.ptr.foundry.ontology.model.dto.WizardObjectSpecDTO;
import com.aircas.ptr.foundry.ontology.model.dto.WizardPropertyDTO;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.WizardBuildObjectParam;
import com.aircas.ptr.foundry.ontology.model.param.WizardBuildPropertiesParam;
import com.aircas.ptr.foundry.ontology.model.param.WizardBuildRelationsParam;
import com.aircas.ptr.foundry.ontology.model.param.WizardFinalizeParam;
import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.aircas.ptr.foundry.ontology.model.po.OntologyCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.vo.WizardFinalizeResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.WizardPropertiesResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.WizardRelationVO;
import com.aircas.ptr.foundry.ontology.model.vo.WizardRelationsResultVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.DatasourceConnectionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkCategoryMapper;
import com.aircas.ptr.foundry.ontology.service.DatasourceMetadataService;
import com.aircas.ptr.foundry.ontology.service.OntologyCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.service.OntologyWizardService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 本体构建向导服务实现
 * <p>
 * 三个步骤各自调用一次 LLM，前置数据（分类清单 / 数据源元数据 / 已有本体清单）由后端拼装进 prompt，
 * LLM 只做"从候选中挑选/生成"的工作，减少幻觉。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OntologyWizardServiceImpl implements OntologyWizardService {

    private static final Integer STATUS_VALID = 1;
    private static final Integer STATUS_ENABLED = 1;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 单次 prompt 最多带入的表数量，避免大库把上下文塞爆 */
    private static final int MAX_TABLES_IN_PROMPT = 80;
    /** 单表最多带入的字段数量 */
    private static final int MAX_COLUMNS_PER_TABLE = 40;

    /** 从 LLM 返回文本中兜底提取 JSON 对象 */
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    /** 允许的属性类型枚举名（供 prompt 展示） */
    private static final String ALLOWED_PROPERTY_TYPES = Arrays.stream(OntologyDataTypeEnum.values())
            .map(Enum::name)
            .collect(Collectors.joining(", "));

    /** 允许的关系类型枚举（供 prompt 展示） */
    private static final String ALLOWED_RELATION_TYPES = Arrays.stream(OntologyLinkTypeEnum.values())
            .map(e -> e.name() + "(" + e.getName() + ")")
            .collect(Collectors.joining(", "));

    // ------------------------------------------------------------------
    // System prompts
    // ------------------------------------------------------------------

    /**
     * 阶段一：仅凭大模型内置知识，独立确认“这个对象是什么”，不看分类清单与数据源，
     * 避免归类被表名/分类名望文生义地带偏。
     */
    private static final String SYS_PROMPT_OBJECT_DEFINITION = """
            你是领域知识专家。用户会给出一个对象的名称或描述，你需要仅凭自身知识客观说明“这个东西是什么”。
            严格遵守：
            1. 只依据你的内置知识判断；本步骤不提供任何分类清单或数据库表，也不要去猜测它们。
            2. domain（领域/类型）：用简洁词组概括对象所属领域，形如“陆战装备-机动火箭炮”“海军舰艇-驱逐舰”“航天-遥感卫星”；无法确定时填“未知”。
            3. definition（基本定义）：80 字以内，客观说明该对象的本质、用途与关键特征。
            4. aliases（别名）：常见中文名、英文名或缩写，没有则返回空数组 []。
            5. 若名称含糊或存在多种可能，在 definition 中指出最可能的一种并说明不确定性。
            6. 输出必须是单一 JSON 对象，不要 markdown 代码块，不要多余文字。
               结构固定为：{"domain":"","definition":"","aliases":[]}
            """;

    private static final String SYS_PROMPT_BUILD_OBJECT = """
            你是本体建模专家。任务是根据【对象基本定义】、用户描述、可选分类清单和数据源表清单，构建一个本体对象规格。
            严格遵守：
            1. apiName 必须是英文小写下划线格式（如 arleigh_burke_destroyer），不能包含空格或中文。
            2. name 是简洁的中文显示名，description 是 100 字以内的中文描述。
            3. 归类（categoryId）必须以【对象基本定义】中已确认的领域为准，再匹配分类，宁缺毋滥：
               - 只能从【可选分类清单】的 id 中选择，禁止臆造清单外的 id。
               - 【对象基本定义】已给出该对象的真实领域/类型，归类必须与之一致（例如定义为“陆战装备-机动火箭炮”就绝不能归入舰船/水面舰艇类）；数据源表名仅作参考，不得据此推翻定义中的领域判断。
               - 仅当某分类与对象领域语义明确一致时才选它；若清单中没有语义明确匹配的分类，必须返回 null，禁止强行归入“看似沾边”的分类。
            4. reasoning（构建依据，200 字以内）必须按以下顺序组织，且“数据源参考”必须放在最前面：
               - 数据源参考（第一位，必须写明）：主要参考了【数据源表清单】中的哪张（些）表，严格用“表名（表注释）”格式，例如 hms_target（海玛斯目标信息表）；若数据源无明确对应表，则写“数据源无直接对应表，依据对象定义构建”。
               - 对象领域：一句话点明它属于什么领域（来自【对象基本定义】）。
               - 归类理由：为何选该分类，或为何返回 null。
               参考写法：“数据源参考 hms_target（海玛斯目标信息表）；该对象属陆战装备-机动火箭炮领域；分类清单无匹配的陆战类目，故 categoryId 返回 null。”
            5. 输出必须是单一 JSON 对象，不要 markdown 代码块，不要多余文字。
               结构固定为：{"name":"","apiName":"","description":"","categoryId":null,"reasoning":""}
            """;

    private static final String SYS_PROMPT_BUILD_PROPERTIES = """
            你是本体属性建模专家。任务是根据用户描述、已确定的对象规格与数据源结构，构建对象的属性列表。
            严格遵守：
            1. type 必须是以下枚举之一：""" + ALLOWED_PROPERTY_TYPES + """

            2. field 应尽量对应数据源里已存在的列名（区分大小写），若无对应列则返回 null。
            3. name 是简洁的中文属性名，summary 是 30 字以内的属性摘要。
            4. 只输出与本对象直接相关的核心属性（建议 5~20 条），不要罗列数据源里所有列。
            5. reasoning 是整体构建依据（200 字以内，放在 JSON 顶层，不要写进每个属性对象里）：先点明这批属性主要源自哪张（些）数据源表，用“表名（表注释）”格式，例如 hms_target（海玛斯目标信息表）；再简述字段选取思路。
            6. 输出必须是单一 JSON 对象，不要 markdown 代码块。
               结构固定为：{"reasoning":"","properties":[{"name":"","summary":"","field":"","type":""}]}
            """;

    private static final String SYS_PROMPT_BUILD_RELATIONS = """
            你是本体关系建模专家。任务是判断【新对象】与【同空间已有对象清单】中哪些对象存在语义关系。
            严格遵守：
            1. 新对象固定作为关系的源（source），已有对象作为目标（target），不要颠倒。
            2. targetApiName 必须在【已有对象清单】中出现，不要臆造。
            3. type 必须是以下枚举之一：""" + ALLOWED_RELATION_TYPES + """

            4. name 是简短的关系名称（如"装备宙斯盾系统"），description 是 50 字以内的关系说明。
            5. reasoning 是整体构建依据（150 字以内，放在 JSON 顶层，不要写进每个关系对象里），按以下顺序组织：
               - 数据源参考（第一位）：沿用【新对象】构建依据中的数据源表，严格用"表名（表注释）"格式，如 hms_target（海玛斯目标信息表）；若新对象依据中无对应表，则写"数据源无直接对应表，依据对象定义与已有对象清单判定"。
               - 关系判定：概述新对象与哪些已有对象存在何种关系及依据。
            6. 与所有候选都无关系时，relations 返回空数组，reasoning 仍需说明原因：{"reasoning":"...","relations":[]}
            7. 输出必须是单一 JSON 对象，不要 markdown 代码块。
               结构固定为：{"reasoning":"","relations":[{"name":"","targetApiName":"","type":"","description":""}]}
            """;

    // ------------------------------------------------------------------
    // 依赖
    // ------------------------------------------------------------------

    private final OntologyCategoryService ontologyCategoryService;
    private final OntologyMetaService ontologyMetaService;
    private final OntologyPropertyService ontologyPropertyService;
    private final OntologyLinkGroupService ontologyLinkGroupService;
    private final OntologyLinkCategoryMapper ontologyLinkCategoryMapper;
    private final DatasourceConnectionMapper datasourceConnectionMapper;
    private final DatasourceMetadataService datasourceMetadataService;
    private final ChatClient chatClient;

    // ==================================================================
    // 步骤 3：构建对象
    // ==================================================================

    @Override
    public WizardObjectSpecDTO buildObject(WizardBuildObjectParam param) {
        // 阶段一：仅凭用户描述，让大模型独立给出“这是什么”的基本定义（不看分类/数据源，避免被带偏）
        String defRaw = callLlm(SYS_PROMPT_OBJECT_DEFINITION,
                buildDefinitionPrompt(param.getUserInput()), "buildObject-定义");
        LlmObjectDefinitionDTO definition = parseObjectDefinition(defRaw);
        log.info("向导-构建对象阶段一：对象领域={}, 定义={}",
                definition == null ? "(无)" : definition.getDomain(),
                definition == null ? "(无)" : definition.getDefinition());

        // 加载候选分类
        List<OntologyCategory> categories = ontologyCategoryService.list(
                new LambdaQueryWrapper<OntologyCategory>()
                        .eq(OntologyCategory::getOntologySpaceId, param.getSpaceId()));
        // 加载数据源表清单（轻量：只有表名+注释）
        DatasourceConnection conn = loadEnabledConnection(param.getDatasourceId());
        List<TableMetaDTO> tables = safeListTables(conn);

        // 阶段二：结合阶段一定义 + 分类清单 + 数据源表清单，产出最终对象规格
        String prompt = buildObjectPrompt(param.getUserInput(), definition, categories, tables);
        log.info("向导-构建对象阶段二：spaceId={}, 分类数={}, 表数={}",
                param.getSpaceId(), categories.size(), tables.size());

        String raw = callLlm(SYS_PROMPT_BUILD_OBJECT, prompt, "buildObject");
        WizardObjectSpecDTO spec = parseObjectSpec(raw);

        // 用 categoryId 回填 name/path（前端只读展示）
        enrichCategory(spec, categories);
        return spec;
    }

    private String buildDefinitionPrompt(String userInput) {
        return "【对象名称/描述】\n" + userInput
                + "\n\n请仅凭你的知识说明这个对象是什么，按规定 JSON 格式返回。";
    }

    /**
     * 解析阶段一的对象定义；解析失败时降级为 null（阶段二会提示模型自行判断领域），保证向导流程不中断。
     */
    private LlmObjectDefinitionDTO parseObjectDefinition(String raw) {
        String json = extractJsonBlock(raw);
        if (json == null) {
            log.warn("向导-构建对象阶段一：无法从大模型响应提取 JSON，将跳过定义。raw={}", raw);
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, LlmObjectDefinitionDTO.class);
        } catch (Exception e) {
            log.warn("向导-构建对象阶段一：解析定义失败，将跳过定义。raw={}", raw, e);
            return null;
        }
    }

    private String buildObjectPrompt(String userInput,
                                     LlmObjectDefinitionDTO definition,
                                     List<OntologyCategory> categories,
                                     List<TableMetaDTO> tables) {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户描述】\n").append(userInput).append("\n\n");

        sb.append("【对象基本定义】（阶段一已独立确认的领域认知，归类时必须以此为准）\n");
        if (definition == null) {
            sb.append("(未获取到定义，请自行判断对象所属领域后再归类)\n");
        } else {
            sb.append("领域/类型: ").append(StringUtils.defaultIfBlank(definition.getDomain(), "(未知)")).append('\n');
            sb.append("基本定义: ").append(StringUtils.defaultIfBlank(definition.getDefinition(), "(无)")).append('\n');
            if (CollectionUtils.isNotEmpty(definition.getAliases())) {
                sb.append("别名/英文: ").append(String.join("、", definition.getAliases())).append('\n');
            }
        }
        sb.append('\n');

        sb.append("【可选分类清单】（格式：id | 路径 | 名称）\n");
        if (CollectionUtils.isEmpty(categories)) {
            sb.append("(当前空间下暂无分类，categoryId 请返回 null)\n");
        } else {
            for (OntologyCategory c : categories) {
                sb.append(c.getId()).append(" | ")
                        .append(StringUtils.defaultIfBlank(c.getPath(), "(根)"))
                        .append(" | ")
                        .append(StringUtils.defaultIfBlank(c.getName(), ""))
                        .append('\n');
            }
        }
        sb.append('\n');

        sb.append("【数据源表清单】（判断对象领域与构建依据的重要参考，格式：表名 - 表注释）\n");
        if (CollectionUtils.isEmpty(tables)) {
            sb.append("(数据源无表或连接失败)\n");
        } else {
            for (TableMetaDTO t : tables) {
                sb.append("- ").append(t.getTableName())
                        .append(" - ")
                        .append(StringUtils.defaultIfBlank(t.getTableComment(), "(无注释)"))
                        .append('\n');
            }
        }
        sb.append("\n请构建对象规格，按规定 JSON 格式返回。");
        return sb.toString();
    }

    private WizardObjectSpecDTO parseObjectSpec(String raw) {
        String json = extractJsonBlock(raw);
        if (json == null) {
            throw new BusinessException("大模型返回格式非法，无法提取 JSON。raw=" + raw,
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            WizardObjectSpecDTO spec = OBJECT_MAPPER.readValue(json, WizardObjectSpecDTO.class);
            if (spec == null || StringUtils.isBlank(spec.getName())
                    || StringUtils.isBlank(spec.getApiName())) {
                throw new BusinessException("大模型未返回合法的 name/apiName。raw=" + raw,
                        HttpStatus.SERVICE_UNAVAILABLE);
            }
            // 规范化 apiName：转小写 + 空格转下划线
            spec.setApiName(normalizeApiName(spec.getApiName()));
            return spec;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("解析构建对象响应失败，raw={}", raw, e);
            throw new BusinessException("大模型返回 JSON 解析失败: " + e.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private void enrichCategory(WizardObjectSpecDTO spec, List<OntologyCategory> categories) {
        if (spec.getCategoryId() == null || CollectionUtils.isEmpty(categories)) {
            spec.setCategoryName(null);
            spec.setCategoryPath(null);
            return;
        }
        OntologyCategory matched = categories.stream()
                .filter(c -> spec.getCategoryId().equals(c.getId()))
                .findFirst()
                .orElse(null);
        if (matched == null) {
            // LLM 幻觉出一个不在清单里的 categoryId，置空防止误导前端
            log.warn("向导-构建对象：大模型返回了未知分类 id={}，已重置为 null",
                    spec.getCategoryId());
            spec.setCategoryId(null);
            spec.setCategoryName(null);
            spec.setCategoryPath(null);
            return;
        }
        spec.setCategoryName(matched.getName());
        spec.setCategoryPath(matched.getPath());
    }

    // ==================================================================
    // 步骤 4：构建属性
    // ==================================================================

    @Override
    public WizardPropertiesResultVO buildProperties(WizardBuildPropertiesParam param) {
        PreconditionUtils.checkNotNull(param.getObjectSpec(),
                "objectSpec 不能为空", HttpStatus.BAD_REQUEST);

        DatasourceConnection conn = loadEnabledConnection(param.getDatasourceId());
        List<TableMetaDTO> tables = safeListTables(conn);
        // 拉每张表的字段（受 MAX_TABLES_IN_PROMPT / MAX_COLUMNS_PER_TABLE 限制）
        Map<String, List<ColumnMetaDTO>> tableColumns = loadColumnsForTables(conn, tables);

        String prompt = buildPropertiesPrompt(param, tables, tableColumns);
        log.info("向导-构建属性：spaceId={}, 对象标识={}, 表数={}",
                param.getSpaceId(), param.getObjectSpec().getApiName(), tables.size());

        String raw = callLlm(SYS_PROMPT_BUILD_PROPERTIES, prompt, "buildProperties");
        LlmWizardListResponseDTO parsed = parseListResponse(raw);
        List<WizardPropertyDTO> properties = parsed.getProperties() == null
                ? Collections.emptyList() : parsed.getProperties();

        // 规范化每条属性：type 必须在枚举内，否则回退到 String
        properties.forEach(this::normalizeProperty);

        // reasoning 提到外层，属性对象内不再保留
        return WizardPropertiesResultVO.builder()
                .reasoning(parsed.getReasoning())
                .properties(properties)
                .build();
    }

    private String buildPropertiesPrompt(WizardBuildPropertiesParam param,
                                         List<TableMetaDTO> tables,
                                         Map<String, List<ColumnMetaDTO>> tableColumns) {
        WizardObjectSpecDTO spec = param.getObjectSpec();
        StringBuilder sb = new StringBuilder();
        sb.append("【用户原始描述】\n").append(param.getUserInput()).append("\n\n");

        sb.append("【已确定的对象规格】（步骤3输出，用户可能修改过）\n")
                .append("名称: ").append(StringUtils.defaultIfBlank(spec.getName(), "(未填)")).append('\n')
                .append("标识: ").append(StringUtils.defaultIfBlank(spec.getApiName(), "(未填)")).append('\n')
                .append("描述: ").append(StringUtils.defaultIfBlank(spec.getDescription(), "(未填)")).append('\n')
                .append("分类: ").append(StringUtils.defaultIfBlank(spec.getCategoryPath(), spec.getCategoryName()))
                .append('\n')
                .append("构建依据: ").append(StringUtils.defaultIfBlank(spec.getReasoning(), "(未提供)"))
                .append("\n\n");

        sb.append("【数据源结构】（格式：表名(表注释) → 列名(类型, 主键标记): 列注释）\n");
        if (CollectionUtils.isEmpty(tables)) {
            sb.append("(数据源无表或连接失败，请仅依据对象描述生成通用属性，field 全部为 null)\n");
        } else {
            for (TableMetaDTO t : tables) {
                sb.append(t.getTableName())
                        .append('(')
                        .append(StringUtils.defaultIfBlank(t.getTableComment(), "无注释"))
                        .append(")\n");
                List<ColumnMetaDTO> cols = tableColumns.getOrDefault(t.getTableName(), Collections.emptyList());
                for (ColumnMetaDTO c : cols) {
                    sb.append("  - ").append(c.getColumnName())
                            .append(" (").append(c.getDataType());
                    if (Boolean.TRUE.equals(c.getPrimaryKey())) {
                        sb.append(", PK");
                    }
                    sb.append("): ")
                            .append(StringUtils.defaultIfBlank(c.getColumnComment(), "(无注释)"))
                            .append('\n');
                }
            }
        }
        sb.append("\n请为本对象构建属性列表，按规定 JSON 格式返回。");
        return sb.toString();
    }

    private void normalizeProperty(WizardPropertyDTO p) {
        if (p == null) return;
        // type 校验，非法回退到 String
        if (StringUtils.isBlank(p.getType()) || !isValidPropertyType(p.getType())) {
            log.warn("向导-构建属性：非法类型={}，已回退为 String", p.getType());
            p.setType(OntologyDataTypeEnum.String.name());
        }
    }

    private boolean isValidPropertyType(String type) {
        return Arrays.stream(OntologyDataTypeEnum.values())
                .anyMatch(e -> e.name().equalsIgnoreCase(type));
    }

    // ==================================================================
    // 步骤 5：构建关系
    // ==================================================================

    @Override
    public WizardRelationsResultVO buildRelations(WizardBuildRelationsParam param) {
        PreconditionUtils.checkNotNull(param.getObjectSpec(),
                "objectSpec 不能为空", HttpStatus.BAD_REQUEST);

        // 加载同空间下已有本体（排除与新对象 apiName 同名的，避免自关联）
        String newApiName = param.getObjectSpec().getApiName();
        List<OntologyMeta> existing = ontologyMetaService.list(
                new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getOntologySpaceId, param.getSpaceId())
                        .eq(OntologyMeta::getStatus, STATUS_VALID));
        List<OntologyMeta> candidates = existing.stream()
                .filter(m -> StringUtils.isBlank(newApiName)
                        || !newApiName.equalsIgnoreCase(m.getApiName()))
                .collect(Collectors.toList());

        log.info("向导-构建关系：spaceId={}, 新对象标识={}, 候选对象数={}",
                param.getSpaceId(), newApiName, candidates.size());

        // 空间下没有已有本体 → 直接返回空关系，不调 LLM
        if (CollectionUtils.isEmpty(candidates)) {
            return WizardRelationsResultVO.builder()
                    .reasoning("当前本体空间下没有可关联的已有对象，故不产生任何关系。")
                    .relations(Collections.emptyList())
                    .build();
        }

        String prompt = buildRelationsPrompt(param, candidates);
        String raw = callLlm(SYS_PROMPT_BUILD_RELATIONS, prompt, "buildRelations");
        LlmWizardListResponseDTO parsed = parseListResponse(raw);
        List<LlmWizardRelationDTO> llmRelations = parsed.getRelations() == null
                ? Collections.emptyList() : parsed.getRelations();

        // reasoning 提到外层，关系对象内不再保留
        return WizardRelationsResultVO.builder()
                .reasoning(parsed.getReasoning())
                .relations(enrichRelations(llmRelations, candidates, param.getObjectSpec()))
                .build();
    }

    private String buildRelationsPrompt(WizardBuildRelationsParam param, List<OntologyMeta> candidates) {
        WizardObjectSpecDTO spec = param.getObjectSpec();
        StringBuilder sb = new StringBuilder();
        sb.append("【用户原始描述】\n").append(param.getUserInput()).append("\n\n");

        sb.append("【新对象】\n")
                .append("名称: ").append(StringUtils.defaultIfBlank(spec.getName(), "(未填)")).append('\n')
                .append("标识: ").append(StringUtils.defaultIfBlank(spec.getApiName(), "(未填)")).append('\n')
                .append("描述: ").append(StringUtils.defaultIfBlank(spec.getDescription(), "(未填)")).append('\n')
                .append("构建依据: ").append(StringUtils.defaultIfBlank(spec.getReasoning(), "(未提供)"))
                .append('\n');

        if (CollectionUtils.isNotEmpty(param.getProperties())) {
            sb.append("属性: ");
            sb.append(param.getProperties().stream()
                    .map(p -> StringUtils.defaultIfBlank(p.getName(), "?"))
                    .collect(Collectors.joining("、")));
            sb.append('\n');
        }
        sb.append('\n');

        sb.append("【同空间下已有对象清单】（格式：apiName | 显示名 | 描述）\n");
        for (OntologyMeta m : candidates) {
            sb.append("- ").append(StringUtils.defaultIfBlank(m.getApiName(), "(无apiName)"))
                    .append(" | ").append(StringUtils.defaultIfBlank(m.getDisplayName(), "(无显示名)"))
                    .append(" | ").append(StringUtils.defaultIfBlank(m.getDescription(), "(无描述)"))
                    .append('\n');
        }
        sb.append("\n请判断新对象与哪些已有对象存在关系，按规定 JSON 格式返回。target 字段必须使用清单中的 apiName。");
        return sb.toString();
    }

    private List<WizardRelationVO> enrichRelations(List<LlmWizardRelationDTO> llmRelations,
                                                   List<OntologyMeta> candidates,
                                                   WizardObjectSpecDTO spec) {
        if (CollectionUtils.isEmpty(llmRelations)) {
            return Collections.emptyList();
        }
        // 按 apiName / displayName / uniqueIdentifier 建立索引，容忍 LLM 用不同字段引用
        Map<String, OntologyMeta> index = new HashMap<>();
        for (OntologyMeta m : candidates) {
            if (StringUtils.isNotBlank(m.getApiName())) {
                index.putIfAbsent(m.getApiName().toLowerCase(), m);
            }
            if (StringUtils.isNotBlank(m.getDisplayName())) {
                index.putIfAbsent(m.getDisplayName().toLowerCase(), m);
            }
            if (StringUtils.isNotBlank(m.getUniqueIdentifier())) {
                index.putIfAbsent(m.getUniqueIdentifier().toLowerCase(), m);
            }
        }

        List<WizardRelationVO> result = new ArrayList<>();
        Map<String, Boolean> seen = new HashMap<>();
        for (LlmWizardRelationDTO item : llmRelations) {
            if (item == null || StringUtils.isBlank(item.getTargetApiName())) {
                continue;
            }
            OntologyMeta target = index.get(item.getTargetApiName().trim().toLowerCase());
            if (target == null) {
                log.warn("向导-构建关系：丢弃幻觉目标={}", item.getTargetApiName());
                continue;
            }
            String dedupKey = target.getUniqueIdentifier();
            if (Boolean.TRUE.equals(seen.putIfAbsent(dedupKey, Boolean.TRUE))) {
                continue;
            }
            OntologyLinkTypeEnum type = parseLinkType(item.getType());
            result.add(WizardRelationVO.builder()
                    .name(item.getName())
                    .sourceObjectName(spec.getName())
                    .sourceObjectApiName(spec.getApiName())
                    .targetUniqueIdentifier(target.getUniqueIdentifier())
                    .targetObjectName(target.getDisplayName())
                    .targetObjectApiName(target.getApiName())
                    .targetObjectDescription(target.getDescription())
                    .type(type)
                    .typeName(type.getName())
                    .description(item.getDescription())
                    .build());
        }
        return result;
    }

    private OntologyLinkTypeEnum parseLinkType(String raw) {
        if (StringUtils.isBlank(raw)) {
            return OntologyLinkTypeEnum.OTHER;
        }
        return Arrays.stream(OntologyLinkTypeEnum.values())
                .filter(e -> e.name().equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("向导-构建关系：未知关系类型={}，已回退为 OTHER", raw);
                    return OntologyLinkTypeEnum.OTHER;
                });
    }

    // ==================================================================
    // 公共工具
    // ==================================================================

    private DatasourceConnection loadEnabledConnection(Integer datasourceId) {
        PreconditionUtils.checkNotNull(datasourceId, "datasourceId 不能为空", HttpStatus.BAD_REQUEST);
        DatasourceConnection conn = datasourceConnectionMapper.selectById(datasourceId);
        PreconditionUtils.checkNotNull(conn,
                "数据源不存在: id=" + datasourceId, HttpStatus.NOT_FOUND);
        if (!STATUS_ENABLED.equals(conn.getStatus())) {
            throw new BusinessException("数据源 [" + conn.getName() + "] 已禁用",
                    HttpStatus.BAD_REQUEST);
        }
        return conn;
    }

    /**
     * 拉表清单，失败时降级为空列表而非直接抛异常，保证向导流程可继续
     */
    private List<TableMetaDTO> safeListTables(DatasourceConnection conn) {
        try {
            List<TableMetaDTO> tables = datasourceMetadataService.listTables(conn, null);
            if (tables.size() > MAX_TABLES_IN_PROMPT) {
                log.warn("向导：数据源 {} 有 {} 张表，已截断为前 {} 张",
                        conn.getName(), tables.size(), MAX_TABLES_IN_PROMPT);
                return tables.subList(0, MAX_TABLES_IN_PROMPT);
            }
            return tables;
        } catch (Exception e) {
            log.error("向导：数据源 {} 拉表清单失败，降级为空列表继续",
                    conn.getName(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 拉每张表的字段，控制总量避免 prompt 过大
     */
    private Map<String, List<ColumnMetaDTO>> loadColumnsForTables(DatasourceConnection conn,
                                                                 List<TableMetaDTO> tables) {
        Map<String, List<ColumnMetaDTO>> result = new HashMap<>();
        if (CollectionUtils.isEmpty(tables)) {
            return result;
        }
        for (TableMetaDTO t : tables) {
            try {
                List<ColumnMetaDTO> cols = datasourceMetadataService.listColumns(
                        conn, t.getSchemaName(), t.getTableName());
                if (cols.size() > MAX_COLUMNS_PER_TABLE) {
                    cols = cols.subList(0, MAX_COLUMNS_PER_TABLE);
                }
                result.put(t.getTableName(), cols);
            } catch (Exception e) {
                log.warn("向导：拉取 {}.{} 字段失败，跳过",
                        t.getSchemaName(), t.getTableName(), e);
                result.put(t.getTableName(), Collections.emptyList());
            }
        }
        return result;
    }

    private String callLlm(String systemPrompt, String userPrompt, String tag) {
        log.debug("向导-{} prompt：{}", tag, userPrompt);
        try {
            String raw = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            log.info("向导-{} 大模型原始响应：{}", tag, raw);
            if (StringUtils.isBlank(raw)) {
                throw new BusinessException("大模型返回为空", HttpStatus.SERVICE_UNAVAILABLE);
            }
            return raw;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("向导-{} 调用大模型失败", tag, e);
            throw new BusinessException("调用大模型失败: " + e.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private LlmWizardListResponseDTO parseListResponse(String raw) {
        String json = extractJsonBlock(raw);
        if (json == null) {
            throw new BusinessException("大模型返回格式非法，无法提取 JSON。raw=" + raw,
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            LlmWizardListResponseDTO dto =
                    OBJECT_MAPPER.readValue(json, LlmWizardListResponseDTO.class);
            return dto == null ? new LlmWizardListResponseDTO() : dto;
        } catch (Exception e) {
            log.error("解析列表响应失败，raw={}", raw, e);
            throw new BusinessException("大模型返回 JSON 解析失败: " + e.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private String extractJsonBlock(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        Matcher m = JSON_OBJECT_PATTERN.matcher(trimmed);
        return m.find() ? m.group() : null;
    }

    /**
     * 规范化 apiName：转小写、空格/中划线转下划线、去除其他非法字符
     */
    private String normalizeApiName(String raw) {
        if (StringUtils.isBlank(raw)) return raw;
        String s = raw.trim().toLowerCase()
                .replaceAll("[\\s\\-]+", "_")
                .replaceAll("[^a-z0-9_]", "");
        // 数字开头不合法，加前缀
        if (!s.isEmpty() && Character.isDigit(s.charAt(0))) {
            s = "ont_" + s;
        }
        return s;
    }

    // ==================================================================
    // 落库
    // ==================================================================

    @Transactional(transactionManager = "chainedTransactionManager", rollbackFor = Exception.class)
    @Override
    public WizardFinalizeResultVO persist(WizardFinalizeParam param) {
        WizardObjectSpecDTO spec = param.getObjectSpec();
        PreconditionUtils.checkNotNull(spec, "objectSpec 不能为空", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(spec.getName()),
                "对象名称不能为空", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(spec.getApiName()),
                "对象标识不能为空", HttpStatus.BAD_REQUEST);

        // 1. 创建本体 meta
        var metaParam = new OntologyMetaCreateParam()
                .setDisplayName(spec.getName())
                .setApiName(spec.getApiName())
                .setDescription(spec.getDescription())
                .setCategoryId(spec.getCategoryId());
        metaParam.setSpaceId(param.getSpaceId());
        String uniqueIdentifier = ontologyMetaService.createOntology(metaParam);
        log.info("向导-落库：本体已创建，spaceId={}, apiName={}, uniqueIdentifier={}",
                param.getSpaceId(), spec.getApiName(), uniqueIdentifier);

        // 2. 创建属性
        int propertyCount = 0;
        if (CollectionUtils.isNotEmpty(param.getProperties())) {
            // 用于 apiName 去重
            Map<String, Integer> apiNameCounter = new HashMap<>();
            int index = 0;
            for (WizardPropertyDTO p : param.getProperties()) {
                index++;
                String apiName = deduplicateApiName(
                        normalizePropertyApiName(p.getField(), index), apiNameCounter);
                var propertyParam = new OntologyPropertyCreateParam()
                        .setDisplayName(StringUtils.defaultIfBlank(p.getName(), "属性" + index))
                        .setApiName(apiName)
                        .setDataType(resolveDataType(p.getType()))
                        .setDescription(p.getSummary())
                        .setIsPrimaryKey(false)
                        .setIsTitleKey(index == 1)
                        .setStorageGroup("main");
                propertyParam.setOntologyIdentifier(uniqueIdentifier);
                ontologyPropertyService.createProperty(propertyParam);
                propertyCount++;
            }
            log.info("向导-落库：已创建 {} 个属性，uniqueIdentifier={}", propertyCount, uniqueIdentifier);
        }

        // 3. 创建关系
        int relationCount = 0;
        if (CollectionUtils.isNotEmpty(param.getRelations())) {
            Integer defaultCategoryId = null;
            for (WizardRelationVO r : param.getRelations()) {
                if (StringUtils.isBlank(r.getTargetUniqueIdentifier())) {
                    log.warn("向导-落库：跳过 targetUniqueIdentifier 为空的关系，name={}",
                            r.getName());
                    continue;
                }
                if (defaultCategoryId == null) {
                    defaultCategoryId = getOrCreateDefaultLinkCategory(param.getSpaceId());
                }
                var linkParam = new OntologyLinkCreateParam()
                        .setName(StringUtils.defaultIfBlank(r.getName(),
                                spec.getName() + "→" + StringUtils.defaultIfBlank(r.getTargetObjectName(), "?")))
                        .setOntologyUniqueIdentifierFrom(uniqueIdentifier)
                        .setOntologyUniqueIdentifierTo(r.getTargetUniqueIdentifier())
                        .setType(r.getType() != null ? r.getType() : OntologyLinkTypeEnum.OTHER)
                        .setCategoryId(defaultCategoryId);
                ontologyLinkGroupService.createLink(linkParam);
                relationCount++;
            }
            log.info("向导-落库：已创建 {} 个关系，uniqueIdentifier={}", relationCount, uniqueIdentifier);
        }

        return WizardFinalizeResultVO.builder()
                .spaceId(param.getSpaceId())
                .ontologyUniqueIdentifier(uniqueIdentifier)
                .apiName(spec.getApiName())
                .displayName(spec.getName())
                .propertyCount(propertyCount)
                .relationCount(relationCount)
                .build();
    }

    /**
     * 规范化属性 apiName，使其符合 ^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$
     */
    private String normalizePropertyApiName(String field, int index) {
        if (StringUtils.isBlank(field)) {
            return "prop_" + index;
        }
        String s = field.trim().toLowerCase()
                .replaceAll("[^a-z0-9_$]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (s.isEmpty()) {
            return "prop_" + index;
        }
        if (Character.isDigit(s.charAt(0))) {
            s = "p_" + s;
        }
        if (s.length() > 63) {
            s = s.substring(0, 63);
        }
        return s;
    }

    /**
     * apiName 去重：若已存在则追加 _2, _3 ...
     */
    private String deduplicateApiName(String apiName, Map<String, Integer> counter) {
        int count = counter.merge(apiName, 1, Integer::sum);
        return count == 1 ? apiName : apiName + "_" + count;
    }

    /**
     * 解析属性数据类型，非法值兜底为 String
     */
    private OntologyDataTypeEnum resolveDataType(String dataType) {
        if (StringUtils.isBlank(dataType)) {
            return OntologyDataTypeEnum.String;
        }
        try {
            return OntologyDataTypeEnum.valueOf(dataType.trim());
        } catch (IllegalArgumentException e) {
            log.warn("向导-落库：未知数据类型={}，已回退为 String", dataType);
            return OntologyDataTypeEnum.String;
        }
    }

    /**
     * 获取或创建空间下的默认关系分类
     */
    private Integer getOrCreateDefaultLinkCategory(Integer spaceId) {
        var existing = ontologyLinkCategoryMapper.selectOne(
                new LambdaQueryWrapper<OntologyLinkCategory>()
                        .eq(OntologyLinkCategory::getOntologySpaceId, spaceId)
                        .eq(OntologyLinkCategory::getName, "默认分类")
                        .last("LIMIT 1"));
        if (existing != null) {
            return existing.getId();
        }
        var category = OntologyLinkCategory.builder()
                .ontologySpaceId(spaceId)
                .name("默认分类")
                .parentId(0)
                .path("默认分类")
                .build();
        ontologyLinkCategoryMapper.insert(category);
        log.info("向导-落库：已创建默认关系分类，spaceId={}, categoryId={}",
                spaceId, category.getId());
        return category.getId();
    }
}
