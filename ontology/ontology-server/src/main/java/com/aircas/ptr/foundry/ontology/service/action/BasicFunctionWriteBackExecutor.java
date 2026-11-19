package com.aircas.ptr.foundry.ontology.service.action;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.enums.ActionOperationEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BasicFunctionModeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.ActionBasicFunctionConfig;
import com.aircas.ptr.foundry.ontology.model.param.EntityCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityPropertyInsertParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityPropertyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.ActionBasicFunctionWriteBackVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityPropertyGenericQueryVO;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableFieldMappingMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基础函数行为的结果回写：把这次查询/聚合的结果落到对象数据上。
 *
 * <p>两种落法各对应一种操作方式，不交叉：</p>
 * <ul>
 *   <li>查询方式：一条结果行 = 目标子表分组的一行新数据，只追加不删除。查询字段跨主子表时，
 *       通用查询按 LEFT JOIN 把子表行展开、主表值重复，这里就按行照写，重复也照写：
 *       落点只有一张子表，去重反而会丢掉子表自己的多行取值。</li>
 *   <li>聚合方式：改目标实例主表上已有的属性，主键属性不收（改了就把实例本身换掉了）。</li>
 * </ul>
 *
 * <p>落点的实例主键由执行请求按对象传（entityPrimaryKeys：对象id -&gt; 实例主键），创建时只建立字段绑定关系；
 * 配了回写却没给实例主键直接报错，不能让用户以为数据已经落下去。</p>
 */
@Slf4j
@Component
public class BasicFunctionWriteBackExecutor {

    /** 主表分组名，与属性值行为同一口径 */
    private static final String MAIN_STORAGE_GROUP = "main";

    private final OntologyMetaMapper metaMapper;

    private final OntologyPropertyMapper propertyMapper;

    private final TableFieldMappingMapper tableFieldMappingMapper;

    private final TableMetadataMapper tableMetadataMapper;

    private final EntityService entityService;

    public BasicFunctionWriteBackExecutor(OntologyMetaMapper metaMapper, OntologyPropertyMapper propertyMapper,
                                         TableFieldMappingMapper tableFieldMappingMapper,
                                         TableMetadataMapper tableMetadataMapper, @Lazy EntityService entityService) {
        this.metaMapper = metaMapper;
        this.propertyMapper = propertyMapper;
        this.tableFieldMappingMapper = tableFieldMappingMapper;
        this.tableMetadataMapper = tableMetadataMapper;
        this.entityService = entityService;
    }

    /** 行为是否配了结果回写；没配就维持只查不落库的原口径 */
    public boolean isEnabled(ActionBasicFunctionConfig config) {
        return config != null && config.getWriteBack() != null;
    }

    /**
     * 创建/编辑时校验回写配置：对象、分组、每条绑定的来源字段和目标属性都要能被写库路径认出来。
     * <p>校验用的分组匹配条件与 {@code insertEntityProperty}/{@code updateEntityProperty} 完全一致
     * （按存储分组名等值匹配、且属性必须已关联数据源列），配得过就一定写得进去。</p>
     */
    public void checkConfig(ActionBasicFunctionConfig config, Integer spaceId) {
        var writeBack = config.getWriteBack();
        if (writeBack == null) {
            return;
        }
        var bindings = listOrEmpty(writeBack.getBindings());
        PreconditionUtils.checkArgument(!bindings.isEmpty(),
                "配置了结果回写就要选回写绑定：说清哪个结果值写到目标对象的哪个属性", HttpStatus.BAD_REQUEST);
        var target = loadTarget(writeBack.getTargetOntologyIdentifier(), spaceId);
        var targetProps = propertiesOf(target.getUniqueIdentifier());
        var primaryKey = requirePrimaryKey(targetProps, target);
        if (config.getMode() == BasicFunctionModeEnum.QUERY) {
            checkQueryBindings(config, writeBack, target, targetProps, primaryKey, bindings);
        } else {
            checkAggregateBindings(writeBack, target, targetProps, bindings);
        }
    }

    /** 查询方式：落点必须是一个已绑定数据源、且与主表建了关联列的子表分组 */
    private void checkQueryBindings(ActionBasicFunctionConfig config, ActionBasicFunctionConfig.WriteBack writeBack,
                                    OntologyMeta target, List<OntologyProperty> targetProps,
                                    OntologyProperty primaryKey, List<ActionBasicFunctionConfig.FieldBinding> bindings) {
        var storageGroup = StringUtils.trimToEmpty(writeBack.getTargetStorageGroup());
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(storageGroup),
                "查询方式的回写要选存储分组：结果行只能追加到子表分组，main 主表的行就是实例本身", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(!MAIN_STORAGE_GROUP.equals(storageGroup),
                "查询方式的回写不能选 main 分组：往主表写就是改实例，请改用聚合方式或选一个子表分组", HttpStatus.BAD_REQUEST);
        var groupProps = targetProps.stream()
                .filter(prop -> storageGroup.equals(prop.getStorageGroup()))
                .collect(Collectors.toList());
        PreconditionUtils.checkArgument(!groupProps.isEmpty(),
                "对象[" + target.getDisplayName() + "]没有存储分组[" + storageGroup + "]，回写落不下去", HttpStatus.BAD_REQUEST);
        checkGroupWritableByInsert(target, primaryKey, storageGroup, groupProps);
        var groupPropById = groupProps.stream().collect(Collectors.toMap(
                OntologyProperty::getUniqueIdentifier, prop -> prop, (existing, ignored) -> existing));
        var selectFields = distinct(config.getSelectFields());
        var boundFields = new LinkedHashSet<String>();
        for (var binding : bindings) {
            var sourceField = StringUtils.trimToEmpty(binding.getSourceField());
            PreconditionUtils.checkArgument(StringUtils.isNotEmpty(sourceField),
                    "查询方式的回写绑定要选查询字段", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(selectFields.contains(sourceField),
                    "回写绑定的来源字段[" + sourceField + "]不在本行为的查询字段里，请先把它加进查询字段", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(boundFields.add(sourceField),
                    "查询字段[" + sourceField + "]配了多条回写绑定，一个字段只能写一个目标属性", HttpStatus.BAD_REQUEST);
            checkTargetProperty(binding.getTargetPropertyIdentifier(), groupPropById, storageGroup, true);
        }
    }

    /** 聚合方式：落点固定是目标实例的主表属性，主键不收 */
    private void checkAggregateBindings(ActionBasicFunctionConfig.WriteBack writeBack,
                                        OntologyMeta target, List<OntologyProperty> targetProps,
                                        List<ActionBasicFunctionConfig.FieldBinding> bindings) {
        PreconditionUtils.checkArgument(StringUtils.isBlank(writeBack.getTargetStorageGroup())
                        || MAIN_STORAGE_GROUP.equals(writeBack.getTargetStorageGroup()),
                "聚合方式的回写固定改[" + target.getDisplayName() + "]主表的属性，不需要选存储分组", HttpStatus.BAD_REQUEST);
        var mainPropById = targetProps.stream()
                .filter(prop -> MAIN_STORAGE_GROUP.equals(prop.getStorageGroup()))
                .collect(Collectors.toMap(OntologyProperty::getUniqueIdentifier, prop -> prop,
                        (existing, ignored) -> existing));
        PreconditionUtils.checkArgument(!mainPropById.isEmpty(),
                "对象[" + target.getDisplayName() + "]的 main 分组没有已关联数据源的属性，聚合结果没有可改的主表属性",
                HttpStatus.BAD_REQUEST);
        var boundProps = new LinkedHashSet<String>();
        for (var binding : bindings) {
            PreconditionUtils.checkArgument(StringUtils.isBlank(binding.getSourceField()),
                    "聚合方式的回写不需要选来源字段：本次只有一个聚合值", HttpStatus.BAD_REQUEST);
            var property = checkTargetProperty(binding.getTargetPropertyIdentifier(), mainPropById,
                    MAIN_STORAGE_GROUP, true);
            PreconditionUtils.checkArgument(property.getIsPrimaryKey() == null || property.getIsPrimaryKey() == 0,
                    "聚合结果的回写属性[" + property.getDisplayName() + "]是主键，主键不能被改成聚合值", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(boundProps.add(property.getUniqueIdentifier()),
                    "属性[" + property.getDisplayName() + "]配了多条回写绑定，一个属性只能接一个聚合值", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 子表分组能不能插行：分组表要已关联数据源、和主表之间有外键映射、且没有配不出来的必填列。
     * <p>缺关联列时插库那一步会拿到空的映射关系，报错点是数据库而不是行为配置，先在这里拦住。</p>
     */
    private void checkGroupWritableByInsert(OntologyMeta target, OntologyProperty primaryKey, String storageGroup,
                                            List<OntologyProperty> groupProps) {
        var boundProps = groupProps.stream()
                .filter(prop -> StringUtils.isNotEmpty(prop.getDatasourceId())
                        && StringUtils.isNotEmpty(prop.getDatasourceColumnName()))
                .collect(Collectors.toList());
        PreconditionUtils.checkArgument(!boundProps.isEmpty(),
                "对象[" + target.getDisplayName() + "]的存储分组[" + storageGroup + "]没有已关联数据源字段的属性，回写落不下去",
                HttpStatus.BAD_REQUEST);
        var groupTable = boundProps.get(0).getDatasourceId();
        PreconditionUtils.checkArgument(boundProps.stream().allMatch(prop -> groupTable.equals(prop.getDatasourceId())),
                "对象[" + target.getDisplayName() + "]的存储分组[" + storageGroup + "]跨了多张数据表，回写只能落到一张表",
                HttpStatus.BAD_REQUEST);
        var mapping = tableFieldMappingMapper.selectBySourceAndTarget(primaryKey.getDatasourceSchema(),
                primaryKey.getDatasourceId(), groupTable);
        PreconditionUtils.checkArgument(mapping != null,
                "对象[" + target.getDisplayName() + "]的存储分组[" + storageGroup + "]与主表没有建立关联列，"
                        + "新增出来的行挂不到实例上，请先在对象里配好分组关联", HttpStatus.BAD_REQUEST);
    }

    /** 取回写绑定指向的目标属性，并确认它已关联数据源列 */
    private OntologyProperty checkTargetProperty(String propertyIdentifier, Map<String, OntologyProperty> candidates,
                                                 String storageGroup, boolean requireDatasource) {
        var identifier = StringUtils.trimToNull(propertyIdentifier);
        PreconditionUtils.checkArgument(identifier != null, "回写绑定没有选目标属性", HttpStatus.BAD_REQUEST);
        var property = candidates.get(identifier);
        PreconditionUtils.checkArgument(property != null,
                "回写属性不在存储分组[" + storageGroup + "]里，或该属性还没有关联数据源字段，请重新选择", HttpStatus.BAD_REQUEST);
        if (requireDatasource) {
            PreconditionUtils.checkArgument(StringUtils.isNotEmpty(property.getDatasourceId())
                            && StringUtils.isNotEmpty(property.getDatasourceColumnName()),
                    "回写属性[" + property.getDisplayName() + "]还没有关联数据源字段，值落不到列上", HttpStatus.BAD_REQUEST);
        }
        return property;
    }

    /**
     * 查询方式回写：把结果行逐行追加到目标子表分组。
     *
     * @param rows 通用查询取到的全部数据行（回写按全量取，出参仍只回首页）
     */
    public ActionBasicFunctionWriteBackVO writeQueryRows(ActionBasicFunctionConfig config,
                                                         Map<String, Object> instanceKeysByObject,
                                                         List<List<EntityPropertyGenericQueryVO>> rows) {
        var writeBack = config.getWriteBack();
        var target = resolveTarget(config, instanceKeysByObject);
        var storageGroup = StringUtils.trimToEmpty(writeBack.getTargetStorageGroup());
        var groupProps = target.properties().stream()
                .filter(prop -> storageGroup.equals(prop.getStorageGroup())
                        && StringUtils.isNotEmpty(prop.getDatasourceId())
                        && StringUtils.isNotEmpty(prop.getDatasourceColumnName()))
                .collect(Collectors.toList());
        checkGroupWritableByInsert(target.meta(), target.primaryKey(), storageGroup, groupProps);
        var groupPropById = groupProps.stream().collect(Collectors.toMap(
                OntologyProperty::getUniqueIdentifier, prop -> prop, (existing, ignored) -> existing));

        var bindings = listOrEmpty(writeBack.getBindings());
        var writeFields = new ArrayList<WriteField>();
        for (var binding : bindings) {
            var property = checkTargetProperty(binding.getTargetPropertyIdentifier(), groupPropById, storageGroup, true);
            writeFields.add(new WriteField(StringUtils.trimToEmpty(binding.getSourceField()), property));
        }
        checkRequiredColumnsFilled(target.meta(), storageGroup, groupProps, writeFields);

        var summary = baseSummary(target, storageGroup, ActionOperationEnum.CREATE, writeFields,
                notWrittenFields(config, writeFields));
        if (CollectionUtils.isEmpty(rows)) {
            summary.setRowCount(0);
            log.info("基础函数回写：对象[{}]查询没有命中数据，分组[{}]不新增行", target.meta().getDisplayName(), storageGroup);
            return summary;
        }
        List<List<EntityCreateParam.PropertyInfo>> propsByRow = new ArrayList<>(rows.size());
        for (List<EntityPropertyGenericQueryVO> row : rows) {
            List<EntityCreateParam.PropertyInfo> rowProps = new ArrayList<>(writeFields.size());
            for (WriteField field : writeFields) {
                Object value = entityService.convertWriteValue(cellValue(row, field.sourceField()), field.property());
                rowProps.add(EntityCreateParam.PropertyInfo.builder()
                        .propertyApiName(field.property().getApiName())
                        .propertyValue(value)
                        .build());
            }
            propsByRow.add(rowProps);
        }
        entityService.insertEntityProperty(EntityPropertyInsertParam.builder()
                .ontologyIdentifier(target.meta().getUniqueIdentifier())
                .entityPrimaryKey(target.instancePrimaryKey())
                .entityProperties(List.of(EntityCreateParam.EntityProperty.builder()
                        .storageGroup(storageGroup)
                        .props(propsByRow)
                        .build()))
                .build());
        summary.setRowCount(propsByRow.size());
        return summary;
    }

    /**
     * 聚合方式回写：把聚合值改成目标实例主表上的属性值。
     *
     * @param aggregateValue 本次的统一聚合值（多对象时是各对象的行合批算出来的那一个）；可以是 null——空集上的 MAX/SUM 就是没值
     */
    public ActionBasicFunctionWriteBackVO writeAggregateValue(ActionBasicFunctionConfig config,
                                                              Map<String, Object> instanceKeysByObject,
                                                              Object aggregateValue) {
        var writeBack = config.getWriteBack();
        var target = resolveTarget(config, instanceKeysByObject);
        var mainPropById = target.properties().stream()
                .filter(prop -> MAIN_STORAGE_GROUP.equals(prop.getStorageGroup())
                        && StringUtils.isNotEmpty(prop.getDatasourceId())
                        && StringUtils.isNotEmpty(prop.getDatasourceColumnName()))
                .collect(Collectors.toMap(OntologyProperty::getUniqueIdentifier, prop -> prop,
                        (existing, ignored) -> existing));
        var bindings = listOrEmpty(writeBack.getBindings());
        var sourceLabel = config.getAggFunc() == null ? "聚合值" : config.getAggFunc().getValue();
        var props = new ArrayList<EntityCreateParam.PropertyInfo>();
        var writeFields = new ArrayList<WriteField>();
        for (var binding : bindings) {
            var property = checkTargetProperty(binding.getTargetPropertyIdentifier(), mainPropById,
                    MAIN_STORAGE_GROUP, true);
            props.add(EntityCreateParam.PropertyInfo.builder()
                    .propertyApiName(property.getApiName())
                    .propertyValue(entityService.convertWriteValue(aggregateValue, property))
                    .build());
            writeFields.add(new WriteField(sourceLabel, property));
        }
        var summary = baseSummary(target, MAIN_STORAGE_GROUP, ActionOperationEnum.UPDATE, writeFields, List.of());
        summary.setRowCount(props.size());
        entityService.updateEntityProperty(EntityPropertyUpdateParam.builder()
                .ontologyIdentifier(target.meta().getUniqueIdentifier())
                .entityPrimaryKey(target.instancePrimaryKey())
                .storageGroup(MAIN_STORAGE_GROUP)
                .dataPrimaryKey(target.instancePrimaryKey())
                .props(props)
                .build());
        return summary;
    }

    /**
     * 定位回写落点：目标对象 + 该对象本次执行指定的实例主键。
     * <p>实例主键一律由执行请求按对象给（行为测试、手动执行、行为树节点选实例），创建时不固定：
     * 回写的行要挂在哪个实例上只有调用方知道。</p>
     */
    private WriteTarget resolveTarget(ActionBasicFunctionConfig config, Map<String, Object> instanceKeysByObject) {
        var writeBack = config.getWriteBack();
        var target = loadTarget(writeBack.getTargetOntologyIdentifier(), null);
        var properties = propertiesOf(target.getUniqueIdentifier());
        var primaryKey = requirePrimaryKey(properties, target);
        var instancePrimaryKey = instanceKeysByObject == null ? null
                : instanceKeysByObject.get(target.getUniqueIdentifier());
        PreconditionUtils.checkNotNull(instancePrimaryKey, "行为[" + target.getDisplayName()
                + "]是本次回写的目标对象，执行时要按对象传它的实例主键（entityPrimaryKeys：对象id -> 实例主键），"
                + "否则回写结果没有归属，请重新执行并选择实例", HttpStatus.BAD_REQUEST);
        var exists = tableMetadataMapper.queryPrimaryKeyColumnName(primaryKey.getDatasourceSchema(),
                primaryKey.getDatasourceId());
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(exists),
                "对象[" + target.getDisplayName() + "]的主表没有主键列，回写无法定位实例数据", HttpStatus.BAD_REQUEST);
        return new WriteTarget(target, primaryKey, instancePrimaryKey, properties);
    }

    /** 分组里没被任何绑定覆盖的必填列：插库会被非空约束拒掉，提前点名让人补绑定 */
    private void checkRequiredColumnsFilled(OntologyMeta target, String storageGroup,
                                            List<OntologyProperty> groupProps, List<WriteField> writeFields) {
        var notNullColumns = tableMetadataMapper.queryNotNullColumns(groupProps.get(0).getDatasourceSchema(),
                groupProps.get(0).getDatasourceId());
        if (CollectionUtils.isEmpty(notNullColumns)) {
            return;
        }
        var writtenColumns = writeFields.stream()
                .map(field -> field.property().getDatasourceColumnName())
                .collect(Collectors.toSet());
        var blocking = groupProps.stream()
                .filter(prop -> prop.getIsPrimaryKey() == null || prop.getIsPrimaryKey() == 0)
                .filter(prop -> !writtenColumns.contains(prop.getDatasourceColumnName()))
                .filter(prop -> notNullColumns.contains(prop.getDatasourceColumnName()))
                .map(OntologyProperty::getDisplayName)
                .collect(Collectors.joining("、"));
        PreconditionUtils.checkArgument(StringUtils.isEmpty(blocking),
                "对象[" + target.getDisplayName() + "]的存储分组[" + storageGroup + "]有必填属性没有配回写绑定，"
                        + "新增出来的行落不下去：" + blocking + "。请给这些字段补上绑定，或在对象里放开它们的非空约束",
                HttpStatus.BAD_REQUEST);
    }

    /** 回写摘要的目标对象、分组、落点实例和写入属性；行数由各自的落库分支补 */
    private ActionBasicFunctionWriteBackVO baseSummary(WriteTarget target, String storageGroup,
                                                       ActionOperationEnum operation, List<WriteField> writeFields,
                                                       List<String> notWrittenFields) {
        return ActionBasicFunctionWriteBackVO.builder()
                .targetOntologyIdentifier(target.meta().getUniqueIdentifier())
                .targetOntologyDisplayName(target.meta().getDisplayName())
                .targetStorageGroup(storageGroup)
                .operation(operation)
                .entityPrimaryKey(target.instancePrimaryKey())
                .writtenProperties(writeFields.stream().map(field -> field.property().getDisplayName())
                        .distinct().collect(Collectors.toList()))
                .notWrittenFields(notWrittenFields)
                .build();
    }

    /** 查询字段里没配到目标属性的那些：查得到但没落库，得让页面说清楚 */
    private List<String> notWrittenFields(ActionBasicFunctionConfig config, List<WriteField> writeFields) {
        var bound = writeFields.stream().map(WriteField::sourceField).collect(Collectors.toSet());
        var unbound = distinct(config.getSelectFields()).stream()
                .filter(field -> !bound.contains(field))
                .collect(Collectors.toList());
        if (unbound.isEmpty()) {
            return List.of();
        }
        var identifiers = distinct(config.getOntologyIdentifiers());
        var displayNames = identifiers.isEmpty() ? Map.<String, OntologyProperty>of()
                : propertiesOf(identifiers.get(0)).stream()
                .collect(Collectors.toMap(OntologyProperty::getApiName, prop -> prop, (existing, ignored) -> existing));
        return unbound.stream()
                .map(field -> {
                    var property = displayNames.get(field);
                    return property == null ? field : property.getDisplayName();
                })
                .collect(Collectors.toList());
    }

    /** 一行结果里按属性 apiName 取查询值；同一个字段理论上只出现一次，取第一个 */
    private Object cellValue(List<EntityPropertyGenericQueryVO> row, String propertyApiName) {
        if (CollectionUtils.isEmpty(row) || StringUtils.isEmpty(propertyApiName)) {
            return null;
        }
        return row.stream()
                .filter(cell -> cell != null && StringUtils.equals(propertyApiName, cell.getPropertyApiName()))
                .map(EntityPropertyGenericQueryVO::getValue)
                .findFirst()
                .orElse(null);
    }

    /** 回写目标对象；spaceId 非空时同时核对归属空间 */
    private OntologyMeta loadTarget(String ontologyIdentifier, Integer spaceId) {
        var identifier = StringUtils.trimToNull(ontologyIdentifier);
        PreconditionUtils.checkArgument(identifier != null, "结果回写要选目标对象", HttpStatus.BAD_REQUEST);
        var meta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, identifier), false);
        PreconditionUtils.checkArgument(meta != null, "结果回写的目标对象不存在：" + identifier, HttpStatus.BAD_REQUEST);
        if (spaceId != null) {
            PreconditionUtils.checkArgument(spaceId.equals(meta.getOntologySpaceId()),
                    "结果回写的目标对象[" + meta.getDisplayName() + "]不属于该对象空间", HttpStatus.BAD_REQUEST);
        }
        return meta;
    }

    /** 对象的有效属性（未删除且已关联数据源），回写只认这批 */
    private List<OntologyProperty> propertiesOf(String ontologyIdentifier) {
        return propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier)
                        .eq(OntologyProperty::getStatus, Status.ENABLE.getValue()))
                .stream()
                .filter(prop -> StringUtils.isNotEmpty(prop.getDatasourceId()))
                .collect(Collectors.toList());
    }

    /** 对象主键属性，回写要靠它定位主表 schema 和主表列 */
    private OntologyProperty requirePrimaryKey(List<OntologyProperty> properties, OntologyMeta meta) {
        var primaryKey = properties.stream()
                .filter(prop -> prop.getIsPrimaryKey() != null && prop.getIsPrimaryKey() == 1)
                .filter(prop -> StringUtils.isNotEmpty(prop.getDatasourceColumnName()))
                .findFirst()
                .orElse(null);
        PreconditionUtils.checkNotNull(primaryKey, "对象[" + meta.getDisplayName()
                + "]没有主键属性或主键未关联数据源字段，无法回写", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(primaryKey.getDatasourceSchema()),
                "对象[" + meta.getDisplayName() + "]的主键属性没有数据源 schema，无法回写", HttpStatus.BAD_REQUEST);
        return primaryKey;
    }

    private List<String> distinct(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        return values.stream().filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
    }

    private <T> List<T> listOrEmpty(List<T> values) {
        return values == null ? List.<T>of() : values;
    }

    /** 本次回写的落点 */
    private record WriteTarget(OntologyMeta meta, OntologyProperty primaryKey, Object instancePrimaryKey,
                              List<OntologyProperty> properties) {
    }

    /** 一条生效的绑定：来源字段 + 目标属性 */
    private record WriteField(String sourceField, OntologyProperty property) {
    }
}
