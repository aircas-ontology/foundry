package com.aircas.ptr.foundry.ontology.service.action;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.enums.AggFuncEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BasicFunctionModeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FilterNodeTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FilterValueSourceEnum;
import com.aircas.ptr.foundry.ontology.model.enums.QueryOpEnum;
import com.aircas.ptr.foundry.ontology.model.enums.SortEnum;
import com.aircas.ptr.foundry.ontology.model.param.ActionBasicFunctionConfig;
import com.aircas.ptr.foundry.ontology.model.param.EntityPropertyGenericQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityUnionAggregateParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityUnionQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.FilterGroupParam;
import com.aircas.ptr.foundry.ontology.model.param.FilterNodeParam;
import com.aircas.ptr.foundry.ontology.model.param.OrderByParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySelectPropertyParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyFilterParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.ActionBasicFunctionObjectResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.ActionBasicFunctionResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.ActionBasicFunctionWriteBackVO;
import com.aircas.ptr.foundry.ontology.model.vo.BasicFunctionCommonPropertyVO;
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
import java.util.Collection;
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
 * 这里的配置直接引用属性 apiName，所以不绑算子、没有变量绑定那一层，执行时按操作方式交给通用查询：
 * 查询方式单个对象直接查、多个对象按 UNION ALL 合并后统一分页，聚合方式把每个对象的取值合批（UNION ALL）后只算一次。
 * 实例主键按对象给，给了就把该对象贡献的行收窄到那条实例，没给按该对象全量参与。</p>
 *
 * <p>过滤条件可以声明取值来源：固定值随配置落库，外部输入只随配置落库「是哪个属性」，
 * 值在测试/执行时按该属性的 apiName 从 params 传入。</p>
 */
@Slf4j
@Component
public class BasicFunctionActionExecutor {

    /** 与通用查询的默认分页一致：查询方式一次返回一页，聚合方式不受分页影响 */
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 查询方式额外给一条总行数的出参 */
    private static final String TOTAL_FIELD = "total";

    /** 多对象合并查询的出参前缀：结果不属于其中某一个对象，不能再拿单个对象的 apiName 打头 */
    private static final String UNION_OUTPUT_PREFIX = "union";

    /**
     * 配了结果回写时一次取满的行数上限：回写要落全部命中行，而不是首页那十几条。
     * <p>命中行数超过上限直接报错，不分页循环取——通用查询没有默认排序，翻页取数会漏行也会重行，
     * 宁可让人收窄条件。</p>
     */
    private static final int MAX_WRITE_BACK_ROWS = 1000;

    private final OntologyMetaMapper metaMapper;

    private final OntologyCategoryMapper categoryMapper;

    private final OntologyPropertyMapper propertyMapper;

    private final EntityService entityService;

    private final ObjectMapper jacksonObjectMapper;

    private final BasicFunctionWriteBackExecutor writeBackExecutor;

    public BasicFunctionActionExecutor(OntologyMetaMapper metaMapper, OntologyCategoryMapper categoryMapper,
                                       OntologyPropertyMapper propertyMapper, @Lazy EntityService entityService,
                                       ObjectMapper jacksonObjectMapper,
                                       BasicFunctionWriteBackExecutor writeBackExecutor) {
        this.metaMapper = metaMapper;
        this.categoryMapper = categoryMapper;
        this.propertyMapper = propertyMapper;
        this.entityService = entityService;
        this.jacksonObjectMapper = jacksonObjectMapper;
        this.writeBackExecutor = writeBackExecutor;
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
        checkMode(config);
        checkSameCategory(metas);
        checkPage(config);
        checkFields(metas, config);
        checkFilterConfig(config.getFilters());
        writeBackExecutor.checkConfig(config, spaceId);
    }

    /** 分页号与每页条数只能是正整数；没配按默认 1/10，存量配置不带这两个字段 */
    private void checkPage(ActionBasicFunctionConfig config) {
        PreconditionUtils.checkArgument(config.getPageNum() == null || config.getPageNum() >= 1,
                "查询方式的分页号要大于 0", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(config.getPageSize() == null || config.getPageSize() >= 1,
                "查询方式的每页条数要大于 0", HttpStatus.BAD_REQUEST);
    }

    /**
     * 多个对象能共同使用的字段：每个对象上都有同名属性、属性类型完全一致、且都已关联数据源列。
     * <p>查询字段、聚合目标、条件字段都只能从这批里选：少一个有对象查不出来，类型不一致时
     * UNION ALL 合不到一列里，MAX/MIN 还会按文本比出错值。单对象时就是该对象全部可用字段。</p>
     */
    public BasicFunctionCommonPropertyVO commonProperties(List<String> ontologyIdentifiers, Integer spaceId) {
        var identifiers = ontologyIdentifiers == null ? List.<String>of() : ontologyIdentifiers.stream()
                .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
        PreconditionUtils.checkArgument(!identifiers.isEmpty(), "要传至少一个对象id", HttpStatus.BAD_REQUEST);
        var metas = loadMetas(identifiers, spaceId);
        checkSameCategory(metas);
        var propsByObject = metas.stream()
                .map(meta -> propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, meta.getUniqueIdentifier())))
                .collect(Collectors.toList());
        var seen = new LinkedHashSet<String>();
        var properties = new ArrayList<BasicFunctionCommonPropertyVO.CommonProperty>();
        for (var property : propsByObject.get(0)) {
            var apiName = property.getApiName();
            if (StringUtils.isBlank(apiName) || !seen.add(apiName)) {
                continue;
            }
            var common = true;
            var types = new LinkedHashSet<OntologyDataTypeEnum>();
            var displayNames = new ArrayList<String>();
            for (var props : propsByObject) {
                var match = props.stream()
                        .filter(candidate -> StringUtils.equals(apiName, candidate.getApiName()))
                        .findFirst().orElse(null);
                if (match == null || StringUtils.isEmpty(match.getDatasourceId())
                        || StringUtils.isEmpty(match.getDatasourceColumnName())) {
                    common = false;
                    break;
                }
                types.add(match.getPropertyType());
                displayNames.add(StringUtils.defaultIfBlank(match.getDisplayName(), apiName));
            }
            // 类型不一致的字段跨对象合不到一列里，不提供给前端选
            if (!common || types.size() > 1) {
                continue;
            }
            properties.add(BasicFunctionCommonPropertyVO.CommonProperty.builder()
                    .apiName(apiName)
                    .propertyType(property.getPropertyType())
                    .isPrimaryKey(property.getIsPrimaryKey() != null && property.getIsPrimaryKey() == 1)
                    .isTitleKey(property.getIsTitleKey() != null && property.getIsTitleKey() == 1)
                    .displayNames(displayNames)
                    .build());
        }
        return BasicFunctionCommonPropertyVO.builder()
                .ontologyIdentifiers(identifiers)
                .ontologyNames(metas.stream()
                        .map(meta -> StringUtils.defaultIfBlank(meta.getDisplayName(), meta.getUniqueIdentifier()))
                        .collect(Collectors.toList()))
                .properties(properties)
                .build();
    }

    /**
     * 操作方式与其必填项：两种方式都要选必填字段，聚合要选函数和目标属性。
     * <p>创建时校验，执行时也走一遍——配置可能来自手工改库或旧版本。</p>
     */
    private void checkMode(ActionBasicFunctionConfig config) {
        var mode = config.getMode();
        if (mode == null) {
            throw new BusinessException("基础函数行为要选择操作方式：聚合还是查询", HttpStatus.BAD_REQUEST);
        }
        if (mode == BasicFunctionModeEnum.QUERY) {
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

    public String run(ActionBasicFunctionConfig config, Map<String, Object> primaryKeyByObject) {
        return run(config, primaryKeyByObject, null, null);
    }

    public String run(ActionBasicFunctionConfig config, Map<String, Object> primaryKeyByObject,
                      Object writeBackEntityPrimaryKey) {
        return run(config, primaryKeyByObject, writeBackEntityPrimaryKey, null);
    }

    /**
     * 执行一次基础函数查询，行为配了结果回写时顺带把这次结果落到对象数据上。
     *
     * @param config                    行为的查询配置
     * @param primaryKeyByObject        对象id -&gt; 实例主键；某个查询对象没有键时该对象按全量参与。
     *                                  没单独给回写实例时，这份键里要带上回写目标对象的实例主键，否则回写没有归属、直接报错
     * @param writeBackEntityPrimaryKey 单独指定的回写实例主键；只覆盖回写目标对象，不参与查询收窄
     * @param externalParams            属性 apiName -&gt; 值，解析过滤条件里 source=EXTERNAL 的条件；
     *                                  没声明外部输入条件时不看这份值
     * @return 结果 JSON，一条；聚合方式多对象时也是合批算出的那一个统一值。配了回写时附带 writeBack 说明落到了哪里、落了几行
     */
    @SneakyThrows
    public String run(ActionBasicFunctionConfig config, Map<String, Object> primaryKeyByObject,
                      Object writeBackEntityPrimaryKey, Map<String, Object> externalParams) {
        var identifiers = configOntologyIdentifiers(config);
        if (identifiers.isEmpty()) {
            throw new BusinessException("基础函数行为没有选择对象", HttpStatus.BAD_REQUEST);
        }
        var metas = loadMetas(identifiers, null);
        checkMode(config);
        checkPage(config);
        checkFields(metas, config);
        checkFilterConfig(config.getFilters());
        // 外部输入只在执行时才取值：复制一份条件树、把外部条件换成具体值，后面的查询/聚合都只看解析后的条件
        var filters = resolveFilters(config.getFilters(), externalParams);
        var mode = config.getMode();
        var writeBackEnabled = writeBackExecutor.isEnabled(config);
        var pageNum = configPageNum(config);
        var pageSize = configPageSize(config);
        List<ActionBasicFunctionObjectResultVO> results;
        Object aggregateValue = null;
        var writeBackRows = new ArrayList<List<EntityPropertyGenericQueryVO>>();
        if (mode == BasicFunctionModeEnum.AGGREGATE) {
            // 各对象算各自的 AVG/COUNT 再拼起来不是全局值，所以每个对象只出一个取值分支，聚合函数只在外层调一次
            var page = entityService.genericAggregateUnion(buildUnionAggregateParam(config, metas, primaryKeyByObject, filters));
            aggregateValue = firstValue(page);
            results = List.of(ActionBasicFunctionObjectResultVO.builder()
                    .ontologyUniqueIdentifier(metas.size() == 1 ? metas.get(0).getUniqueIdentifier() : null)
                    .ontologyDisplayName(joinObjectNames(metas))
                    .aggFunc(config.getAggFunc())
                    .targetField(config.getTargetField())
                    .value(aggregateValue)
                    .build());
        } else {
            // 回写要落全部命中行：把页宽抬到上限、从第一页取满（命中更多直接报错，见 checkWriteBackRows）
            var totalPageNum = writeBackEnabled ? DEFAULT_PAGE_NUM : pageNum;
            var totalPageSize = writeBackEnabled ? MAX_WRITE_BACK_ROWS : pageSize;
            Page<List<EntityPropertyGenericQueryVO>> page;
            if (metas.size() == 1) {
                var meta = metas.get(0);
                var queryParam = buildQueryParam(config, meta, instanceKey(primaryKeyByObject, meta), filters,
                        totalPageNum, totalPageSize);
                if (writeBackEnabled) {
                    // 通用查询没有默认排序：回写按主键倒序取最近命中行，同一条件重复执行落的是同一批行，回显页与实写行也对得上
                    queryParam.setOrderBy(List.of(OrderByParam.builder()
                            .propertyApiName(requirePrimaryKeyApiName(meta.getUniqueIdentifier()))
                            .sort(SortEnum.DESC)
                            .build()));
                }
                page = entityService.genericQuery(queryParam);
            } else {
                // 多对象：各对象各查一遍选中字段，SQL 层 UNION ALL 合并后统一分页
                var unionParam = buildUnionQueryParam(config, metas, primaryKeyByObject, filters, totalPageNum, totalPageSize);
                page = entityService.genericQueryUnion(unionParam);
            }
            var rows = page == null || page.getRecords() == null
                    ? List.<List<EntityPropertyGenericQueryVO>>of() : page.getRecords();
            var item = ActionBasicFunctionObjectResultVO.builder()
                    .ontologyUniqueIdentifier(metas.size() == 1 ? metas.get(0).getUniqueIdentifier() : null)
                    .ontologyDisplayName(joinObjectNames(metas))
                    .build();
            if (writeBackEnabled) {
                checkWriteBackRows(page);
                writeBackRows.addAll(rows);
                // 出参和黑板取值仍按配置的那一页回显，不因为配了回写就把整批行回显出去
                item.setRecords(pageSlice(rows, pageNum, pageSize));
            } else {
                item.setRecords(rows);
            }
            item.setTotal(page == null ? 0L : page.getTotal());
            results = List.of(item);
        }
        return jacksonObjectMapper.writeValueAsString(ActionBasicFunctionResultVO.builder()
                .mode(mode)
                .results(results)
                .writeBack(writeBackEnabled
                        ? writeBack(mode, config,
                        writeBackKeys(config, primaryKeyByObject, writeBackEntityPrimaryKey),
                        writeBackRows, aggregateValue) : null)
                .build());
    }

    /** 配置里的分页号，没配按 1 */
    private int configPageNum(ActionBasicFunctionConfig config) {
        return config.getPageNum() == null || config.getPageNum() < 1 ? DEFAULT_PAGE_NUM : config.getPageNum();
    }

    /** 配置里的每页条数，没配按 10 */
    private int configPageSize(ActionBasicFunctionConfig config) {
        return config.getPageSize() == null || config.getPageSize() < 1 ? DEFAULT_PAGE_SIZE : config.getPageSize();
    }

    /**
     * 回写模式下从「一次取满」的结果里切出配置的那一页用于回显。
     * <p>取数和回显不是同一页：取数从第一页取到上限是为了把全部命中行都落库，
     * 回显仍按配置的 pageNum/pageSize，与不配回写时看到的一致。</p>
     */
    private List<List<EntityPropertyGenericQueryVO>> pageSlice(List<List<EntityPropertyGenericQueryVO>> rows,
                                                              int pageNum, int pageSize) {
        var from = (long) (pageNum - 1) * pageSize;
        if (from >= rows.size()) {
            return List.of();
        }
        return new ArrayList<>(rows.subList((int) from, (int) Math.min(rows.size(), from + pageSize)));
    }

    /**
     * 回写落点的实例键：单独给的回写实例只覆盖回写目标对象，查询收窄仍用原那份；
     * 没给就沿用按对象传的键，保持行为测试旧的传法。
     */
    private Map<String, Object> writeBackKeys(ActionBasicFunctionConfig config,
                                              Map<String, Object> primaryKeyByObject,
                                              Object writeBackEntityPrimaryKey) {
        if (writeBackEntityPrimaryKey == null) {
            return primaryKeyByObject;
        }
        var keys = new LinkedHashMap<String, Object>();
        if (primaryKeyByObject != null) {
            keys.putAll(primaryKeyByObject);
        }
        var target = writeBackTargetIdentifier(config);
        if (target != null) {
            keys.put(target, writeBackEntityPrimaryKey);
        }
        return keys;
    }

    /** 执行请求为该对象指定的实例主键；没指定就是 null，按该对象全量查 */
    private Object instanceKey(Map<String, Object> primaryKeyByObject, OntologyMeta meta) {
        return primaryKeyByObject == null ? null : primaryKeyByObject.get(meta.getUniqueIdentifier());
    }

    /**
     * 聚合配置摊成一次跨对象统一聚合：每个选中对象一个取值分支，实例主键按对象给，
     * 过滤条件用解析后的那份（外部输入已换成值）在每台上共用。
     * <p>条件共用是配置本身的口径（一份 filters 作用于行为选中的所有对象），各对象按自己的属性表解析。</p>
     */
    private EntityUnionAggregateParam buildUnionAggregateParam(ActionBasicFunctionConfig config, List<OntologyMeta> metas,
                                                               Map<String, Object> primaryKeyByObject,
                                                               FilterGroupParam filters) {
        var sources = new ArrayList<EntityUnionAggregateParam.Source>();
        for (var meta : metas) {
            sources.add(EntityUnionAggregateParam.Source.builder()
                    .ontologyIdentifier(meta.getUniqueIdentifier())
                    .entityPrimaryKey(instanceKey(primaryKeyByObject, meta))
                    .filters(filters)
                    .build());
        }
        return EntityUnionAggregateParam.builder()
                .aggFunc(config.getAggFunc())
                .propertyApiName(StringUtils.trimToEmpty(config.getTargetField()))
                .sources(sources)
                .build();
    }

    /** 查询方式的跨对象合并查询：字段按 apiName 共用，实例主键与分页按对象/整批给 */
    private EntityUnionQueryParam buildUnionQueryParam(ActionBasicFunctionConfig config, List<OntologyMeta> metas,
                                                       Map<String, Object> primaryKeyByObject,
                                                       FilterGroupParam filters, int pageNum, int pageSize) {
        var selectProperties = new ArrayList<OntologySelectPropertyParam>();
        for (var field : distinctFields(config.getSelectFields())) {
            selectProperties.add(OntologySelectPropertyParam.builder().propertyApiName(field).build());
        }
        var keys = new LinkedHashMap<String, Object>();
        for (var meta : metas) {
            var key = instanceKey(primaryKeyByObject, meta);
            if (key != null) {
                keys.put(meta.getUniqueIdentifier(), key);
            }
        }
        return EntityUnionQueryParam.builder()
                .ontologyIdentifiers(metas.stream().map(OntologyMeta::getUniqueIdentifier).collect(Collectors.toList()))
                .entityPrimaryKeys(keys)
                .selectProperties(selectProperties)
                .filters(filters)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();
    }

    /** 行为是否配了结果回写：配了执行才会落库，行为的输出方式据此给 WRITE_PROPERTY 还是 RESULT_ONLY */
    public boolean hasWriteBack(ActionBasicFunctionConfig config) {
        return writeBackExecutor.isEnabled(config);
    }

    /** 回写目标对象id；没配回写就是 null。执行侧靠它认下「实例主键给的是回写落点、不是查询对象」这种传法 */
    public String writeBackTargetIdentifier(ActionBasicFunctionConfig config) {
        return hasWriteBack(config) ? config.getWriteBack().getTargetOntologyIdentifier() : null;
    }

    /** 把本次结果落到回写目标上：查询方式逐行追加子表，聚合方式改主表属性 */
    private ActionBasicFunctionWriteBackVO writeBack(BasicFunctionModeEnum mode, ActionBasicFunctionConfig config,
                                                     Map<String, Object> primaryKeyByObject,
                                                     List<List<EntityPropertyGenericQueryVO>> rows,
                                                     Object aggregateValue) {
        return mode == BasicFunctionModeEnum.QUERY
                ? writeBackExecutor.writeQueryRows(config, primaryKeyByObject, rows)
                : writeBackExecutor.writeAggregateValue(config, primaryKeyByObject, aggregateValue);
    }

    /**
     * 回写行数封顶：命中行超过一次能落的量就直接报错。
     * <p>通用查询没有默认排序，靠翻页凑全量会漏行也会重行，落进子表就是错数据；
     * 让人收窄条件比悄悄少写几百行好定位。</p>
     */
    private void checkWriteBackRows(Page<List<EntityPropertyGenericQueryVO>> page) {
        var total = page == null ? 0L : page.getTotal();
        PreconditionUtils.checkArgument(total <= MAX_WRITE_BACK_ROWS,
                "本次查询命中 " + total + " 行，超过结果回写的单次上限 " + MAX_WRITE_BACK_ROWS
                        + " 行，请给行为加过滤条件缩小范围后再执行", HttpStatus.BAD_REQUEST);
    }

    /**
     * 这份配置的结果里有哪些可点名的出参：行为树节点按它列出入参，回传黑板时按 {@code expression}
     * 从 {@link #run} 的结果 JSON 里取值。
     * <p>查询方式一次取第一行的对应列（黑板键和下游标量入参装不下整批行，口径同通配出参取批内首值），
     * 总条数单独列一条；聚合方式只有一个统一聚合值，只列一条。</p>
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
            // 字段展示名与类型按第一个对象取；多对象时结果不属于其中某一个对象，出参键统一挂 union 前缀
            var meta = metas.get(identifiers.get(0));
            if (meta == null) {
                return List.of();
            }
            var props = propsByApiName(meta.getUniqueIdentifier());
            var multiObject = identifiers.size() > 1;
            var keyPrefix = multiObject ? UNION_OUTPUT_PREFIX : outputKeyPrefix(meta);
            var namePrefix = multiObject
                    ? identifiers.stream().map(metas::get).filter(Objects::nonNull)
                    .map(OntologyMeta::getDisplayName).collect(Collectors.joining("、"))
                    : meta.getDisplayName();
            var index = 0;
            for (var field : distinctFields(config.getSelectFields())) {
                var property = props.get(field);
                outputs.add(new BasicFunctionOutput(keyPrefix + "_" + field,
                        namePrefix + " " + propertyDisplayName(property, field),
                        "$.results[0].records[0][" + index + "].value",
                        property == null ? null : property.getPropertyType()));
                index++;
            }
            var totalKey = keyPrefix + "_" + TOTAL_FIELD;
            if (outputs.stream().noneMatch(output -> StringUtils.equals(totalKey, output.key()))) {
                outputs.add(new BasicFunctionOutput(totalKey, namePrefix + " 查询到的总行数",
                        "$.results[0].total", OntologyDataTypeEnum.Long));
            }
            return outputs;
        }
        var aggFunc = config.getAggFunc();
        var targetField = StringUtils.trimToEmpty(config.getTargetField());
        if (aggFunc == null || targetField.isEmpty()) {
            return List.of();
        }
        // 多对象合批只出一个统一值，出参就一条：逐对象各列一条会让人以为还能取到每个对象自己的值
        var chosenMetas = identifiers.stream().map(metas::get).filter(Objects::nonNull).collect(Collectors.toList());
        if (chosenMetas.isEmpty()) {
            return List.of();
        }
        var property = propsByApiName(chosenMetas.get(0).getUniqueIdentifier()).get(targetField);
        outputs.add(new BasicFunctionOutput(aggregateOutputKey(config),
                joinObjectNames(chosenMetas) + " " + propertyDisplayName(property, targetField) + " 的" + aggFunc.getValue(),
                "$.results[0].value",
                aggregateValueType(aggFunc, property == null ? null : property.getPropertyType())));
        return outputs;
    }

    /**
     * 统一聚合值的点名标识与结果列名：聚合函数 + 目标属性，例如 {@code avg_speed}。
     * <p>不带对象前缀：这个值是所选对象合批算出来的，不属于其中任何一个对象。</p>
     */
    private String aggregateOutputKey(ActionBasicFunctionConfig config) {
        return config.getAggFunc().getValue().toLowerCase() + "_" + StringUtils.trimToEmpty(config.getTargetField());
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

    /**
     * 查询选中对象的名称，与 {@code config.getOntologyIdentifiers()} 同序、逐项对应。
     * <p>对象已删除或名称为空时用对象id占位，保证前端按下标就能对上标识。</p>
     */
    public List<String> queryOntologyDisplayNames(ActionBasicFunctionConfig config) {
        if (config == null || CollectionUtils.isEmpty(config.getOntologyIdentifiers())) {
            return List.of();
        }
        var identifiers = config.getOntologyIdentifiers();
        var metas = metasById(identifiers.stream().filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList()));
        return identifiers.stream()
                .map(id -> {
                    var meta = metas.get(id);
                    return meta == null || StringUtils.isBlank(meta.getDisplayName()) ? id : meta.getDisplayName();
                })
                .collect(Collectors.toList());
    }

    /** 回写目标对象的名称；没配回写返回 null，对象已删除或名称为空时用对象id占位 */
    public String writeBackOntologyDisplayName(ActionBasicFunctionConfig config) {
        var identifier = writeBackTargetIdentifier(config);
        if (StringUtils.isBlank(identifier)) {
            return null;
        }
        var meta = metasById(List.of(identifier)).get(identifier);
        return meta == null || StringUtils.isBlank(meta.getDisplayName()) ? identifier : meta.getDisplayName();
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
        return outputKeyPrefix(meta) + "_" + field;
    }

    private String outputKeyPrefix(OntologyMeta meta) {
        return StringUtils.defaultIfBlank(meta.getApiName(), meta.getUniqueIdentifier());
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
     * <p>执行时也走这一道：属性可能在行为创建后被删掉或改了数据源，报错要点名是哪个对象缺的。
     * 多对象合并查询还要求查询字段在各对象上的类型完全一致，否则 UNION ALL 合不到一列里。</p>
     */
    private void checkFields(List<OntologyMeta> metas, ActionBasicFunctionConfig config) {
        var fields = referencedFields(config);
        var propsByObject = new LinkedHashMap<String, Map<String, OntologyProperty>>();
        for (var meta : metas) {
            propsByObject.put(meta.getUniqueIdentifier(), propsByApiName(meta.getUniqueIdentifier()));
        }
        if (!fields.isEmpty()) {
            for (var meta : metas) {
                var props = propsByObject.get(meta.getUniqueIdentifier());
                for (var field : fields) {
                    var property = props.get(field);
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
        if (config.getMode() == BasicFunctionModeEnum.QUERY && metas.size() > 1) {
            checkSelectFieldTypes(metas, propsByObject, config);
        }
    }

    /** 多对象合并查询的查询字段：同名字段在各对象上的属性类型必须一致，否则 UNION ALL 合不到一列里 */
    private void checkSelectFieldTypes(List<OntologyMeta> metas,
                                       Map<String, Map<String, OntologyProperty>> propsByObject,
                                       ActionBasicFunctionConfig config) {
        for (var field : distinctFields(config.getSelectFields())) {
            var types = new LinkedHashSet<OntologyDataTypeEnum>();
            var details = new ArrayList<String>();
            for (var meta : metas) {
                var property = propsByObject.get(meta.getUniqueIdentifier()).get(field);
                if (property == null) {
                    continue;
                }
                types.add(property.getPropertyType());
                details.add(meta.getDisplayName() + "：" + (property.getPropertyType() == null
                        ? "未设置" : property.getPropertyType().getValue()));
            }
            if (types.size() > 1) {
                throw new BusinessException("查询字段[" + field + "]在各对象上的数据类型不一致（" + String.join("、", details)
                        + "），合并查询要求同名字段类型一致，请改字段类型或去掉这个字段", HttpStatus.BAD_REQUEST);
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

    /**
     * 过滤条件的取值来源校验：固定值要填值，外部输入要指明属性且不能同时填固定值。
     * <p>创建时校验，执行时再走一遍——存量配置可能来自手工改库。</p>
     */
    private void checkFilterConfig(FilterGroupParam group) {
        if (group == null || CollectionUtils.isEmpty(group.getChildren())) {
            return;
        }
        for (var node : group.getChildren()) {
            if (node == null) {
                continue;
            }
            if (node.getType() == FilterNodeTypeEnum.FILTER && node.getFilter() != null) {
                checkFilterValueSource(node.getFilter());
            } else if (node.getType() == FilterNodeTypeEnum.GROUP) {
                checkFilterConfig(node.getGroup());
            }
        }
    }

    private void checkFilterValueSource(PropertyFilterParam filter) {
        var apiName = filter.getPropertyApiName();
        var op = filter.getOp() == null ? QueryOpEnum.EQ : filter.getOp();
        if (filter.getSource() == FilterValueSourceEnum.EXTERNAL) {
            PreconditionUtils.checkArgument(op != QueryOpEnum.APPLY,
                    "过滤条件[" + apiName + "]的 APPLY 不能用外部输入", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(StringUtils.isNotBlank(apiName),
                    "过滤条件的取值来源是外部输入，要指明是哪个属性（按属性 apiName 传值）", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(filter.getValue() == null && CollectionUtils.isEmpty(filter.getValues()),
                    "过滤条件[" + apiName + "]的取值来源是外部输入，不要再填固定值（填了也不会用）", HttpStatus.BAD_REQUEST);
            return;
        }
        if (op == QueryOpEnum.IS_NULL || op == QueryOpEnum.IS_NOT_NULL) {
            return;
        }
        if (op == QueryOpEnum.APPLY) {
            PreconditionUtils.checkArgument(filter.getValue() != null,
                    "过滤条件[" + apiName + "]是 APPLY，要填 SQL 模板", HttpStatus.BAD_REQUEST);
            return;
        }
        if (op == QueryOpEnum.IN || op == QueryOpEnum.NOT_IN) {
            PreconditionUtils.checkArgument(filter.getValues() != null && !filter.getValues().isEmpty(),
                    "过滤条件[" + apiName + "]是 " + op + "，要填至少一个候选值", HttpStatus.BAD_REQUEST);
            return;
        }
        if (op == QueryOpEnum.BETWEEN || op == QueryOpEnum.NOT_BETWEEN) {
            PreconditionUtils.checkArgument(filter.getValues() != null && filter.getValues().size() >= 2,
                    "过滤条件[" + apiName + "]是 " + op + "，要按 [下界, 上界] 填两个值", HttpStatus.BAD_REQUEST);
            return;
        }
        PreconditionUtils.checkArgument(filter.getValue() != null,
                "过滤条件[" + apiName + "]是固定值来源，要填取值", HttpStatus.BAD_REQUEST);
    }

    /**
     * 把外部输入条件换成具体值：复制一份条件树，EXTERNAL 节点按属性 apiName 从本次执行的值里取值，
     * 其余节点原样保留。原配置不受影响（配置里始终只存取值来源，不存某一次的值）。
     */
    private FilterGroupParam resolveFilters(FilterGroupParam group, Map<String, Object> externalParams) {
        if (group == null || CollectionUtils.isEmpty(group.getChildren())) {
            return group;
        }
        var children = new ArrayList<FilterNodeParam>(group.getChildren().size());
        for (var node : group.getChildren()) {
            if (node == null) {
                children.add(null);
            } else if (node.getType() == FilterNodeTypeEnum.GROUP) {
                children.add(FilterNodeParam.builder()
                        .type(FilterNodeTypeEnum.GROUP)
                        .group(resolveFilters(node.getGroup(), externalParams))
                        .build());
            } else if (node.getType() == FilterNodeTypeEnum.FILTER && node.getFilter() != null) {
                children.add(FilterNodeParam.builder()
                        .type(FilterNodeTypeEnum.FILTER)
                        .filter(resolveFilter(node.getFilter(), externalParams))
                        .build());
            } else {
                children.add(node);
            }
        }
        return FilterGroupParam.builder().logic(group.getLogic()).children(children).build();
    }

    /** 单个条件的外部输入取值，参数名就是属性 apiName；不是外部输入的原样返回，避免无谓复制 */
    private PropertyFilterParam resolveFilter(PropertyFilterParam filter, Map<String, Object> externalParams) {
        if (filter.getSource() != FilterValueSourceEnum.EXTERNAL) {
            return filter;
        }
        var apiName = StringUtils.trimToEmpty(filter.getPropertyApiName());
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(apiName),
                "过滤条件的取值来源是外部输入，要指明是哪个属性（按属性 apiName 传值）", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(externalParams != null && externalParams.get(apiName) != null,
                "过滤条件[" + apiName + "]用了外部输入参数[" + apiName + "]，本次执行没有提供它的值", HttpStatus.BAD_REQUEST);
        var raw = externalParams.get(apiName);
        var op = filter.getOp() == null ? QueryOpEnum.EQ : filter.getOp();
        var resolved = PropertyFilterParam.builder()
                .propertyApiName(apiName)
                .op(filter.getOp())
                .dataType(filter.getDataType())
                .source(FilterValueSourceEnum.FIXED)
                .build();
        if (op == QueryOpEnum.IN || op == QueryOpEnum.NOT_IN) {
            resolved.setValues(externalValueList(raw, apiName));
        } else if (op == QueryOpEnum.BETWEEN || op == QueryOpEnum.NOT_BETWEEN) {
            var values = externalValueList(raw, apiName);
            PreconditionUtils.checkArgument(values.size() >= 2,
                    "过滤条件[" + apiName + "]是 " + op + "，外部输入参数[" + apiName + "]要给两个值：[下界, 上界]",
                    HttpStatus.BAD_REQUEST);
            resolved.setValues(values);
        } else {
            resolved.setValue(raw);
        }
        return resolved;
    }

    private List<Object> externalValueList(Object raw, String apiName) {
        if (raw instanceof Collection<?> values) {
            return new ArrayList<>(values);
        }
        throw new BusinessException("外部输入参数[" + apiName + "]要传数组值，当前给的是单值", HttpStatus.BAD_REQUEST);
    }

    /** 查询方式的通用查询参数：只查行为选中的那一个对象，字段按选择顺序去重 */
    private EntityPropertyGenericQueryParam buildQueryParam(ActionBasicFunctionConfig config, OntologyMeta meta,
                                                            Object entityPrimaryKey, FilterGroupParam filters,
                                                            int pageNum, int pageSize) {        var selectProperties = new ArrayList<OntologySelectPropertyParam>();
        config.getSelectFields().stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .forEach(apiName -> selectProperties.add(OntologySelectPropertyParam.builder()
                        .propertyApiName(apiName)
                        .build()));
        var queryParam = new EntityPropertyGenericQueryParam();
        queryParam.setOntologyIdentifier(meta.getUniqueIdentifier());
        queryParam.setEntityPrimaryKey(entityPrimaryKey);
        queryParam.setSelectProperties(selectProperties);
        queryParam.setFilters(filters);
        queryParam.setPageNum(pageNum);
        queryParam.setPageSize(pageSize);
        return queryParam;
    }

    /** 对象的主键属性 apiName：回写排序按它倒序取；主键属性本身缺失时通用查询也会报错，这里点名提示 */
    private String requirePrimaryKeyApiName(String ontologyIdentifier) {
        var pkApiName = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier)
                        .eq(OntologyProperty::getIsPrimaryKey, 1))
                .stream().findFirst().map(OntologyProperty::getApiName).orElse(null);
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(pkApiName),
                "对象 " + ontologyIdentifier + " 没有主键属性，结果回写无法按稳定行序取数", HttpStatus.BAD_REQUEST);
        return pkApiName;
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
