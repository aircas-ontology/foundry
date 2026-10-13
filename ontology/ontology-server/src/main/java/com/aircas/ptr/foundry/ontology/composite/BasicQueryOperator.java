package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.param.BasicQueryConfig;
import com.aircas.ptr.foundry.ontology.model.param.EntityPropertyGenericQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.FilterGroupParam;
import com.aircas.ptr.foundry.ontology.model.param.FilterNodeParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySelectPropertyParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyFilterParam;
import com.aircas.ptr.foundry.ontology.model.enums.FilterNodeTypeEnum;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.BasicQueryResultVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础函数算子（本体属性通用查询）执行器。
 *
 * <p>把原先只长在 {@code FunctionServiceImpl#testBasicQuery} 里的查询构建逻辑抽出来，
 * 供 REST 测试接口与函数编排节点共用：编排工作流因此可以直接把基础函数算子当作节点接入。</p>
 */
@Slf4j
@Component
public class BasicQueryOperator {

    /** 编排节点上代表"查询哪个本体"的约定入参名 */
    public static final String PARAM_ONTOLOGY_IDENTIFIER = "ontologyIdentifier";

    /** 编排节点上可选的分页入参名 */
    public static final String PARAM_PAGE_NUM = "pageNum";
    public static final String PARAM_PAGE_SIZE = "pageSize";

    /** 编排节点输出端口约定名 */
    public static final String PORT_RESULT = "result";
    public static final String PORT_VALUE = "value";
    public static final String PORT_RECORDS = "records";
    public static final String PORT_TOTAL = "total";

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final EntityService entityService;

    private final OntologyPropertyMapper propertyMapper;

    private final ObjectMapper objectMapper;

    public BasicQueryOperator(@Lazy EntityService entityService, OntologyPropertyMapper propertyMapper,
                              ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.propertyMapper = propertyMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行基础查询算子。
     *
     * @param config             算子查询配置
     * @param ontologyIdentifier 目标本体标识
     * @param variableBindings   变量名 -&gt; 实际属性 apiName
     * @param pageNum            分页号，null 时用默认值
     * @param pageSize           每页条数，null 时用默认值
     * @return 聚合模式返回 {@link BasicQueryResultVO}，查询模式返回分页结果
     */
    @SneakyThrows
    public Object run(BasicQueryConfig config, String ontologyIdentifier, Map<String, String> variableBindings,
                      Integer pageNum, Integer pageSize) {
        if (config == null) {
            throw new BusinessException("基础查询算子配置为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, String> bindings = variableBindings == null ? new LinkedHashMap<>() : variableBindings;

        var queryParam = new EntityPropertyGenericQueryParam();
        queryParam.setOntologyIdentifier(ontologyIdentifier);

        // SELECT：通过变量绑定解析实际属性名
        String targetProp = null;
        List<OntologySelectPropertyParam> selectProps = new ArrayList<>();
        if (StringUtils.isNotEmpty(config.getTargetProperty())) {
            targetProp = bindings.get(config.getTargetProperty());
            requireBinding(targetProp, "变量 " + config.getTargetProperty() + " 未绑定");
            var selectProp = new OntologySelectPropertyParam();
            selectProp.setPropertyApiName(targetProp);
            selectProp.setAggFunc(config.getAggFunc());
            if (config.getAggFunc() != null) {
                selectProp.setAlias(config.getAggFunc().name().toLowerCase() + "_" + targetProp);
            }
            selectProps.add(selectProp);
        }

        // query 模式下如果没有指定 targetProperty，查询本体的所有属性
        if (config.getAggFunc() == null && selectProps.isEmpty()) {
            var allProps = propertyMapper.selectList(
                    new LambdaQueryWrapper<OntologyProperty>()
                            .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
            for (var prop : allProps) {
                var selectProp = new OntologySelectPropertyParam();
                selectProp.setPropertyApiName(prop.getApiName());
                selectProps.add(selectProp);
            }
        }
        if (!selectProps.isEmpty()) {
            queryParam.setSelectProperties(selectProps);
        }

        // WHERE：深拷贝过滤树并替换变量名
        if (config.getFilters() != null) {
            queryParam.setFilters(replaceVariablesInFilterGroup(config.getFilters(), bindings));
        }

        if (config.getAggFunc() != null) {
            // 聚合模式：只取第一行第一列
            queryParam.setPageNum(DEFAULT_PAGE_NUM);
            queryParam.setPageSize(DEFAULT_PAGE_SIZE);
            var pageResult = entityService.genericQuery(queryParam);
            Object aggValue = null;
            if (pageResult != null && pageResult.getRecords() != null && !pageResult.getRecords().isEmpty()) {
                var firstRow = pageResult.getRecords().get(0);
                if (firstRow != null && !firstRow.isEmpty()) {
                    aggValue = firstRow.get(0).getValue();
                }
            }
            return BasicQueryResultVO.builder()
                    .aggFunc(config.getAggFunc().name())
                    .targetProperty(targetProp)
                    .alias(config.getAggFunc().name().toLowerCase() + "_" + targetProp)
                    .value(aggValue)
                    .build();
        }
        queryParam.setPageNum(pageNum == null || pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum);
        queryParam.setPageSize(pageSize == null || pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize);
        return entityService.genericQuery(queryParam);
    }

    /**
     * 解析算子配置（{@code function.code} 中的 queryConfig JSON）。
     */
    @SneakyThrows
    public BasicQueryConfig parseConfig(String code, String functionApi) {
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("基础查询算子配置为空：" + functionApi, HttpStatus.BAD_REQUEST);
        }
        try {
            return objectMapper.readValue(code, BasicQueryConfig.class);
        } catch (Exception e) {
            throw new BusinessException("基础查询算子配置解析失败：" + functionApi, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 编排节点视角：把节点入参拆成本体标识 + 变量绑定，执行后整理成端口可直接取值的键值对。
     *
     * @param config 查询配置
     * @param inputs 节点入参（含 {@code ontologyIdentifier}、变量、可选分页参数）
     * @return {@code result} 为业务数据；聚合模式另有 {@code value/aggFunc/alias/targetProperty}，
     *         查询模式另有 {@code records/total}
     */
    public Map<String, Object> runAsNode(BasicQueryConfig config, Map<String, Object> inputs) {
        Object ontologyValue = inputs.get(PARAM_ONTOLOGY_IDENTIFIER);
        String ontologyIdentifier = ontologyValue == null ? null : String.valueOf(ontologyValue).trim();
        if (StringUtils.isEmpty(ontologyIdentifier)) {
            throw new BusinessException("基础查询节点缺少本体标识：" + PARAM_ONTOLOGY_IDENTIFIER,
                    HttpStatus.BAD_REQUEST);
        }
        Map<String, String> bindings = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            String key = entry.getKey();
            if (PARAM_ONTOLOGY_IDENTIFIER.equals(key) || PARAM_PAGE_NUM.equals(key) || PARAM_PAGE_SIZE.equals(key)) {
                continue;
            }
            if (entry.getValue() != null) {
                bindings.put(key, String.valueOf(entry.getValue()));
            }
        }
        Object raw = run(config, ontologyIdentifier, bindings, intOf(inputs.get(PARAM_PAGE_NUM)),
                intOf(inputs.get(PARAM_PAGE_SIZE)));
        return toPortValues(raw);
    }

    /** 把执行结果摊平成输出端口取值 */
    private Map<String, Object> toPortValues(Object raw) {
        Map<String, Object> ports = new LinkedHashMap<>();
        if (raw instanceof BasicQueryResultVO resultVO) {
            ports.put(PORT_RESULT, resultVO.getValue());
            ports.put(PORT_VALUE, resultVO.getValue());
            ports.put("aggFunc", resultVO.getAggFunc());
            ports.put("alias", resultVO.getAlias());
            ports.put("targetProperty", resultVO.getTargetProperty());
            return ports;
        }
        if (raw instanceof Page<?> page) {
            List<?> records = page.getRecords() == null ? new ArrayList<>() : page.getRecords();
            ports.put(PORT_RESULT, records);
            ports.put(PORT_RECORDS, records);
            ports.put(PORT_TOTAL, page.getTotal());
            return ports;
        }
        ports.put(PORT_RESULT, raw);
        return ports;
    }

    private Integer intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof CharSequence text && StringUtils.isNotBlank(text)) {
            try {
                return Integer.parseInt(text.toString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private void requireBinding(String value, String message) {
        if (StringUtils.isEmpty(value)) {
            throw new BusinessException(message, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 深拷贝过滤树并将变量名替换为实际属性 apiName。
     */
    private FilterGroupParam replaceVariablesInFilterGroup(FilterGroupParam group, Map<String, String> bindings) {
        if (group == null) {
            return null;
        }
        var newGroup = new FilterGroupParam();
        newGroup.setLogic(group.getLogic());
        if (CollectionUtils.isEmpty(group.getChildren())) {
            return newGroup;
        }
        List<FilterNodeParam> newChildren = new ArrayList<>();
        for (var node : group.getChildren()) {
            var newNode = new FilterNodeParam();
            newNode.setType(node.getType());
            if (node.getType() == FilterNodeTypeEnum.FILTER && node.getFilter() != null) {
                var newFilter = new PropertyFilterParam();
                String varName = node.getFilter().getPropertyApiName();
                String actualProp = bindings.get(varName);
                if (StringUtils.isEmpty(actualProp)) {
                    throw new BusinessException("过滤变量 " + varName + " 未绑定", HttpStatus.BAD_REQUEST);
                }
                newFilter.setPropertyApiName(actualProp);
                newFilter.setOp(node.getFilter().getOp());
                newFilter.setValue(node.getFilter().getValue());
                newFilter.setValues(node.getFilter().getValues());
                newFilter.setDataType(node.getFilter().getDataType());
                newNode.setFilter(newFilter);
            } else if (node.getType() == FilterNodeTypeEnum.GROUP && node.getGroup() != null) {
                newNode.setGroup(replaceVariablesInFilterGroup(node.getGroup(), bindings));
            }
            newChildren.add(newNode);
        }
        newGroup.setChildren(newChildren);
        return newGroup;
    }
}
