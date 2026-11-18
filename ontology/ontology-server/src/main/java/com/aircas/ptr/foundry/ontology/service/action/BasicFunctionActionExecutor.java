package com.aircas.ptr.foundry.ontology.service.action;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.enums.AggFuncEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BasicFunctionModeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FilterNodeTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.ActionBasicFunctionConfig;
import com.aircas.ptr.foundry.ontology.model.param.EntityPropertyGenericQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.FilterGroupParam;
import com.aircas.ptr.foundry.ontology.model.param.FilterNodeParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySelectPropertyParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.ActionBasicFunctionObjectResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.ActionBasicFunctionResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityPropertyGenericQueryVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyCategoryMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基础函数行为（subjectType=BASIC_FUNCTION）的定义校验与执行。
 *
 * <p>与基础查询算子的分工：算子那份配置引用的是变量名，执行时要靠变量绑定换成真实属性；
 * 这里的配置直接引用属性 apiName，所以不绑算子、没有变量绑定那一层，执行时把配置逐对象摊成
 * {@link EntityPropertyGenericQueryParam} 交给通用查询。多对象时每个对象各算各的聚合值，
 * 不做跨对象合并；实例主键按对象给，给了就把查询收窄到该实例，没给按该对象全量查。</p>
 */
@Slf4j
@Component
public class BasicFunctionActionExecutor {

    /** 与通用查询的默认分页一致：查询方式一次返回一页，聚合方式不受分页影响 */
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 查询方式额外给一条总行数的出参 */
    private static final String TOTAL_FIELD = "total";

    private final OntologyMetaMapper metaMapper;

    private final OntologyCategoryMapper categoryMapper;

    private final OntologyPropertyMapper propertyMapper;

    private final EntityService entityService;

    private final ObjectMapper jacksonObjectMapper;

    public BasicFunctionActionExecutor(OntologyMetaMapper metaMapper, OntologyCategoryMapper categoryMapper,
                                       OntologyPropertyMapper propertyMapper, @Lazy EntityService entityService,
                                       ObjectMapper jacksonObjectMapper) {
        this.metaMapper = metaMapper;
        this.categoryMapper = categoryMapper;
        this.propertyMapper = propertyMapper;
        this.entityService = entityService;
        this.jacksonObjectMapper = jacksonObjectMapper;
    }

    /**
     * 创建/编辑时校验基础函数配置：对象、操作方式、同分类，以及每个引用字段在各对象上的可用性。
     * <p>字段必须逐个对象核对：多对象时手输的字段名只要有一个对象没有，查询就会在那里失败，
     * 拦在创建侧比让它跑到一半再报错好定位。</p>
     */
    public void checkConfig(ActionBasicFunctionConfig config, Integer spaceId) {
        PreconditionUtils.checkNotNull(config, "基础函数行为要提供查询配置", HttpStatus.BAD_REQUEST);
        var identifiers = configOntologyIdentifiers(config);
        if (identifiers.isEmpty()) {
            throw new BusinessException("基础函数行为至少要选择一个对象", HttpStatus.BAD_REQUEST);
        }
        var metas = loadMetas(identifiers, spaceId);
        checkMode(config, metas);
        checkSameCategory(metas);
        checkFields(metas, config);
    }

    /**
     * 操作方式与其必填项：查询只作用于单对象且要选字段，聚合要选函数和目标属性。
     * <p>创建时校验，执行时也走一遍——配置可能来自手工改库或旧版本。</p>
     */
    private void checkMode(ActionBasicFunctionConfig config, List<OntologyMeta> metas) {
        var mode = config.getMode();
        if (mode == null) {
            throw new BusinessException("基础函数行为要选择操作方式：聚合还是查询", HttpStatus.BAD_REQUEST);
        }
        if (mode == BasicFunctionModeEnum.QUERY) {
            if (metas.size() > 1) {
                throw new BusinessException("查询方式只作用于一个对象，多个对象请用聚合方式：当前选了 "
                        + joinObjectNames(metas), HttpStatus.BAD_REQUEST);
            }
            if (CollectionUtils.isEmpty(config.getSelectFields())) {
                throw new BusinessException("查询方式要选查询哪些属性", HttpStatus.BAD_REQUEST);
            }
        } else {
            if (config.getAggFunc() == null) {
                throw new BusinessException("聚合方式要先选聚合函数", HttpStatus.BAD_REQUEST);
            }
            if (StringUtils.isBlank(config.getTargetField())) {
                throw new BusinessException("聚合方式要选聚合目标属性", HttpStatus.BAD_REQUEST);
            }
        }
    }

    /**
     * 执行一次基础函数查询。
     *
     * @param config            行为的查询配置
     * @param primaryKeyByObject 对象id -&gt; 实例主键；某个对象没有键时该对象按全量查
     * @return 结果 JSON，逐对象一条
     */
    @SneakyThrows
    public String run(ActionBasicFunctionConfig config, Map<String, Object> primaryKeyByObject) {
        var identifiers = configOntologyIdentifiers(config);
        if (identifiers.isEmpty()) {
            throw new BusinessException("基础函数行为没有选择对象", HttpStatus.BAD_REQUEST);
        }
        var metas = loadMetas(identifiers, null);
        checkMode(config, metas);
        checkFields(metas, config);
        var mode = config.getMode();
        var results = new ArrayList<ActionBasicFunctionObjectResultVO>();
        for (var meta : metas) {
            var queryParam = buildQueryParam(config, mode, meta,
                    primaryKeyByObject == null ? null : primaryKeyByObject.get(meta.getUniqueIdentifier()));
            var page = entityService.genericQuery(queryParam);
            var item = ActionBasicFunctionObjectResultVO.builder()
                    .ontologyUniqueIdentifier(meta.getUniqueIdentifier())
                    .ontologyDisplayName(meta.getDisplayName())
                    .build();
            if (mode == BasicFunctionModeEnum.AGGREGATE) {
                item.setAggFunc(config.getAggFunc());
                item.setTargetField(config.getTargetField());
                item.setValue(firstValue(page));
            } else {
                item.setRecords(page == null ? List.of() : page.getRecords());
                item.setTotal(page == null ? 0L : page.getTotal());
            }
            results.add(item);
        }
        return jacksonObjectMapper.writeValueAsString(ActionBasicFunctionResultVO.builder()
                .mode(mode)
                .results(results)
                .build());
    }

    /**
     * 这份配置的结果里有哪些可点名的出参：行为树节点按它列出入参，回传黑板时按 {@code expression}
     * 从 {@link #run} 的结果 JSON 里取值。
     * <p>{@code results} 的下标就是所选对象的顺序，与执行侧逐项产出一致；查询方式一次取第一行的对应列
     * （黑板键和下游标量入参装不下整批行，口径同通配出参取批内首值），总条数单独列一条。</p>
     */
    public List<BasicFunctionOutput> declareOutputs(ActionBasicFunctionConfig config) {
        if (config == null || config.getMode() == null) {
            return List.of();
        }
        var identifiers = configOntologyIdentifiers(config);
        if (identifiers.isEmpty()) {
            return List.of();
        }
        var metas = metasById(identifiers);
        var outputs = new ArrayList<BasicFunctionOutput>();
        if (config.getMode() == BasicFunctionModeEnum.QUERY) {
            // 查询只作用一个对象：多对象那份配置本来就跑不到执行，这里不报出参
            var meta = identifiers.size() == 1 ? metas.get(identifiers.get(0)) : null;
            if (meta == null) {
                return List.of();
            }
            var props = propsByApiName(meta.getUniqueIdentifier());
            var index = 0;
            for (var field : distinctFields(config.getSelectFields())) {
                var property = props.get(field);
                outputs.add(new BasicFunctionOutput(outputKey(meta, field), propertyDisplayName(property, field),
                        "$.results[0].records[0][" + index + "].value",
                        property == null ? null : property.getPropertyType()));
                index++;
            }
            var totalKey = outputKey(meta, TOTAL_FIELD);
            if (outputs.stream().noneMatch(output -> StringUtils.equals(totalKey, output.key()))) {
                outputs.add(new BasicFunctionOutput(totalKey, meta.getDisplayName() + " 查询到的总行数",
                        "$.results[0].total", OntologyDataTypeEnum.Long));
            }
            return outputs;
        }
        var aggFunc = config.getAggFunc();
        var targetField = StringUtils.trimToEmpty(config.getTargetField());
        if (aggFunc == null || targetField.isEmpty()) {
            return List.of();
        }
        for (var i = 0; i < identifiers.size(); i++) {
            var meta = metas.get(identifiers.get(i));
            // 对象已被删掉时这条不列：执行侧同样会在认对象时报错，不存在结果里少一项、路径下标错位的情况
            if (meta == null) {
                continue;
            }
            var property = propsByApiName(meta.getUniqueIdentifier()).get(targetField);
            outputs.add(new BasicFunctionOutput(
                    outputKey(meta, aggFunc.getValue().toLowerCase() + "_" + targetField),
                    meta.getDisplayName() + " " + propertyDisplayName(property, targetField) + " 的" + aggFunc.getValue(),
                    "$.results[" + i + "].value",
                    aggregateValueType(aggFunc, property == null ? null : property.getPropertyType())));
        }
        return outputs;
    }

    /**
     * 聚合值的类型：COUNT 恒为条数，SUM/AVG 一定算成数值，MAX/MIN/DISTINCT 取的就是属性本身的值。
     * <p>黑板键与下游入参要做类型兼容校验，报成属性类型会把条数当成字符串拒掉。</p>
     */
    private OntologyDataTypeEnum aggregateValueType(AggFuncEnum aggFunc, OntologyDataTypeEnum propertyType) {
        if (aggFunc == AggFuncEnum.COUNT) {
            return OntologyDataTypeEnum.Long;
        }
        if (aggFunc == AggFuncEnum.SUM || aggFunc == AggFuncEnum.AVG) {
            return isNumericType(propertyType) ? propertyType : OntologyDataTypeEnum.Double;
        }
        return propertyType;
    }

    private boolean isNumericType(OntologyDataTypeEnum type) {
        return type == OntologyDataTypeEnum.Int || type == OntologyDataTypeEnum.Long
                || type == OntologyDataTypeEnum.Float || type == OntologyDataTypeEnum.Double
                || type == OntologyDataTypeEnum.Decimal || type == OntologyDataTypeEnum.Short;
    }

    /** 基础函数结果的一条出参：点名用的 key、界面展示名、结果 JSON 的取值路径与值类型 */
    public record BasicFunctionOutput(String key, String displayName, String expression,
                                      OntologyDataTypeEnum valueType) {
    }

    /** 读库里的配置 JSON；读不出来直接报错，不能让行为拿着半套配置去查 */
    @SneakyThrows
    public ActionBasicFunctionConfig parse(String configJson, String actionApi) {
        if (StringUtils.isBlank(configJson)) {
            throw new BusinessException("基础函数行为没有查询配置：" + actionApi, HttpStatus.BAD_REQUEST);
        }
        try {
            return jacksonObjectMapper.readValue(configJson, ActionBasicFunctionConfig.class);
        } catch (Exception e) {
            throw new BusinessException("基础函数行为的查询配置无法解析：" + actionApi, HttpStatus.BAD_REQUEST);
        }
    }

    /** 配置写成 JSON 准备落库 */
    @SneakyThrows
    public String write(ActionBasicFunctionConfig config) {
        return jacksonObjectMapper.writeValueAsString(config);
    }

    /** 配置里选中的对象id，去掉空值并保持选择顺序 */
    public List<String> configOntologyIdentifiers(ActionBasicFunctionConfig config) {
        if (config == null || config.getOntologyIdentifiers() == null) {
            return List.of();
        }
        return config.getOntologyIdentifiers().stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }

    /** 按对象id取对象，spaceId 非空时同时核对归属空间 */
    private List<OntologyMeta> loadMetas(List<String> identifiers, Integer spaceId) {
        var metas = metasById(identifiers);
        var missing = identifiers.stream().filter(id -> !metas.containsKey(id)).collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new BusinessException("无效的对象：" + String.join("、", missing), HttpStatus.BAD_REQUEST);
        }
        var ordered = identifiers.stream().map(metas::get).collect(Collectors.toList());
        if (spaceId != null) {
            for (var meta : ordered) {
                if (!spaceId.equals(meta.getOntologySpaceId())) {
                    throw new BusinessException("对象[" + meta.getDisplayName() + "]不属于该对象空间", HttpStatus.BAD_REQUEST);
                }
            }
        }
        return ordered;
    }

    /** 对象id -> 对象；认不出的id留空，由调用方决定是报错还是跳过（执行侧报错，出参声明跳过） */
    private Map<String, OntologyMeta> metasById(List<String> identifiers) {
        return metaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                        .in(OntologyMeta::getUniqueIdentifier, identifiers))
                .stream()
                .collect(Collectors.toMap(OntologyMeta::getUniqueIdentifier, meta -> meta, (a, b) -> a, LinkedHashMap::new));
    }

    /** 一个对象下 apiName -> 属性 */
    private Map<String, OntologyProperty> propsByApiName(String ontologyIdentifier) {
        return propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier))
                .stream()
                .collect(Collectors.toMap(OntologyProperty::getApiName, property -> property, (a, b) -> a));
    }

    /** 出参点名用的键：对象 apiName + 字段（或聚合函数 + 字段），同分类下不同对象不会撞名 */
    private String outputKey(OntologyMeta meta, String field) {
        return StringUtils.defaultIfBlank(meta.getApiName(), meta.getUniqueIdentifier()) + "_" + field;
    }

    private String propertyDisplayName(OntologyProperty property, String apiName) {
        return property == null ? apiName : property.getDisplayName();
    }

    private List<String> distinctFields(List<String> fields) {
        if (fields == null) {
            return List.of();
        }
        return fields.stream().filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
    }

    /** 多对象必须挂在同一个对象分类节点上；单对象不比 */
    private void checkSameCategory(List<OntologyMeta> metas) {
        if (metas.size() < 2) {
            return;
        }
        var categoryIds = metas.stream()
                .map(meta -> meta.getOntologyCategoryId() == null ? "未分类" : String.valueOf(meta.getOntologyCategoryId()))
                .collect(Collectors.toSet());
        if (categoryIds.size() == 1) {
            return;
        }
        var names = categoryNames(metas);
        throw new BusinessException("多个对象必须属于同一个对象分类：" + metas.stream()
                .map(meta -> meta.getDisplayName() + "（分类：" + names.getOrDefault(categoryKey(meta), "未知分类") + "）")
                .collect(Collectors.joining("、")), HttpStatus.BAD_REQUEST);
    }

    private Map<String, String> categoryNames(List<OntologyMeta> metas) {
        var ids = metas.stream().map(OntologyMeta::getOntologyCategoryId).filter(Objects::nonNull).distinct().toList();
        var names = new LinkedHashMap<String, String>();
        var categories = ids.isEmpty() ? List.<OntologyCategory>of()
                : categoryMapper.selectList(new LambdaQueryWrapper<OntologyCategory>().in(OntologyCategory::getId, ids));
        categories.forEach(category -> names.put(String.valueOf(category.getId()), category.getName()));
        names.put("未分类", "未分类");
        return names;
    }

    private String categoryKey(OntologyMeta meta) {
        return meta.getOntologyCategoryId() == null ? "未分类" : String.valueOf(meta.getOntologyCategoryId());
    }

    /**
     * 引用的属性字段（聚合目标、查询字段、条件字段）必须在每个选中的对象上都有，并且已经关联到数据源列。
     * <p>执行时也走这一道：属性可能在行为创建后被删掉或改了数据源，报错要点名是哪个对象缺的。</p>
     */
    private void checkFields(List<OntologyMeta> metas, ActionBasicFunctionConfig config) {
        var fields = referencedFields(config);
        if (fields.isEmpty()) {
            return;
        }
        for (var meta : metas) {
            var propsByApiName = propsByApiName(meta.getUniqueIdentifier());
            for (var field : fields) {
                var property = propsByApiName.get(field);
                if (property == null) {
                    throw new BusinessException("对象[" + meta.getDisplayName() + "]没有属性 " + field
                            + "，基础函数的聚合目标、查询字段和条件字段要在每个选中的对象上都有", HttpStatus.BAD_REQUEST);
                }
                if (StringUtils.isEmpty(property.getDatasourceId())
                        || StringUtils.isEmpty(property.getDatasourceColumnName())) {
                    throw new BusinessException("对象[" + meta.getDisplayName() + "]的属性["
                            + property.getDisplayName() + "]还没关联数据源字段，查询跑不出来，请先在对象里完成数据源关联",
                            HttpStatus.BAD_REQUEST);
                }
            }
        }
    }

    /** 配置里引用到的全部属性 apiName：聚合目标 + 查询字段 + 条件字段（含嵌套条件组） */
    private Set<String> referencedFields(ActionBasicFunctionConfig config) {
        var fields = new LinkedHashSet<String>();
        if (config.getMode() == BasicFunctionModeEnum.AGGREGATE) {
            if (StringUtils.isNotBlank(config.getTargetField())) {
                fields.add(config.getTargetField());
            }
        } else if (config.getSelectFields() != null) {
            config.getSelectFields().stream().filter(StringUtils::isNotBlank).forEach(fields::add);
        }
        collectFilterFields(config.getFilters(), fields);
        return fields;
    }

    private void collectFilterFields(FilterGroupParam group, Set<String> fields) {
        if (group == null || CollectionUtils.isEmpty(group.getChildren())) {
            return;
        }
        for (var node : group.getChildren()) {
            if (node == null) {
                continue;
            }
            if (node.getType() == FilterNodeTypeEnum.FILTER && node.getFilter() != null
                    && StringUtils.isNotBlank(node.getFilter().getPropertyApiName())) {
                fields.add(node.getFilter().getPropertyApiName());
            } else if (node.getType() == FilterNodeTypeEnum.GROUP) {
                collectFilterFields(node.getGroup(), fields);
            }
        }
    }

    private EntityPropertyGenericQueryParam buildQueryParam(ActionBasicFunctionConfig config, BasicFunctionModeEnum mode,
                                                            OntologyMeta meta, Object entityPrimaryKey) {
        var selectProperties = new ArrayList<OntologySelectPropertyParam>();
        if (mode == BasicFunctionModeEnum.AGGREGATE) {
            var aggFunc = config.getAggFunc();
            selectProperties.add(OntologySelectPropertyParam.builder()
                    .propertyApiName(config.getTargetField())
                    .aggFunc(aggFunc)
                    .alias(aggFunc.getValue().toLowerCase() + "_" + config.getTargetField())
                    .build());
        } else {
            config.getSelectFields().stream()
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .forEach(apiName -> selectProperties.add(OntologySelectPropertyParam.builder()
                            .propertyApiName(apiName)
                            .build()));
        }
        var queryParam = new EntityPropertyGenericQueryParam();
        queryParam.setOntologyIdentifier(meta.getUniqueIdentifier());
        queryParam.setEntityPrimaryKey(entityPrimaryKey);
        queryParam.setSelectProperties(selectProperties);
        queryParam.setFilters(config.getFilters());
        queryParam.setPageNum(DEFAULT_PAGE_NUM);
        queryParam.setPageSize(DEFAULT_PAGE_SIZE);
        return queryParam;
    }

    /** 聚合查询只有一行一列；没有命中数据时数据库回 null */
    private Object firstValue(Page<List<EntityPropertyGenericQueryVO>> page) {
        if (page == null || CollectionUtils.isEmpty(page.getRecords())) {
            return null;
        }
        var firstRow = page.getRecords().get(0);
        if (CollectionUtils.isEmpty(firstRow)) {
            return null;
        }
        return firstRow.get(0).getValue();
    }

    private String joinObjectNames(List<OntologyMeta> metas) {
        return metas.stream().map(OntologyMeta::getDisplayName).collect(Collectors.joining("、"));
    }
}
