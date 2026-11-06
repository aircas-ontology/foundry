package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.dto.EntityDatasourceColumnDTO;
import com.aircas.ptr.foundry.ontology.model.dto.EntityDatasourceDTO;
import com.aircas.ptr.foundry.ontology.model.dto.EntityDatasourceSchemaChangeEventDTO;
import com.aircas.ptr.foundry.ontology.model.enums.DatasourceEventTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologyBuildFromDatasourceParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.po.TableColumnDesc;
import com.aircas.ptr.foundry.ontology.model.po.TableDesc;
import com.aircas.ptr.foundry.ontology.model.po.TableFieldMapping;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyBuildFromDatasourceResultVO;
import com.aircas.ptr.foundry.ontology.mq.producer.RabbitMQProducer;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableFieldMappingMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologySpaceMapper;
import com.aircas.ptr.foundry.ontology.service.DatasourceConnectionService;
import com.aircas.ptr.foundry.ontology.service.OntologyBuildFromDatasourceService;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 「数据源构建对象」第④步实现。
 *
 * <p>三段式：先在内存里把整单校验干净（任何 400 都发生在建表之前），再建主库侧的对象与属性，
 * 最后在数据湖空间 schema 下建实体表、写主子表映射并回填属性的数据源字段。
 * 走链式事务是因为主库（ontology）与数据湖（entity_datasource）是两个独立的 DataSource，
 * 先例见 {@code AgentOntologyBuildServiceImpl#buildOntology}。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OntologyBuildFromDatasourceServiceImpl implements OntologyBuildFromDatasourceService {

    /** 平台主键列名；主表没勾这一列时由后台补出来，实体表的 {@code id SERIAL PRIMARY KEY} 本来就是建表即有。 */
    private static final String PK_COLUMN_NAME = "id";
    /** 主表属性所属存储分组，与画布、{@code autoBindDatasource} 保持一致。 */
    private static final String MAIN_STORAGE_GROUP = "main";
    /** 可作为表名 / 列名 / 存储分组名的数据库标识符。 */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$");

    private final OntologySpaceMapper ontologySpaceMapper;
    private final OntologyMetaService ontologyMetaService;
    private final OntologyPropertyService ontologyPropertyService;
    private final DatasourceConnectionService datasourceConnectionService;
    private final TableMetadataMapper tableMetadataMapper;
    private final TableFieldMappingMapper tableFieldMappingMapper;
    private final RabbitMQProducer producer;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Transactional(transactionManager = "chainedTransactionManager", rollbackFor = Exception.class)
    @Override
    public OntologyBuildFromDatasourceResultVO buildObject(OntologyBuildFromDatasourceParam param) {
        PreconditionUtils.checkNotNull(param, "入参不能为空", HttpStatus.BAD_REQUEST);

        // ---------- Phase 0：只读校验，任何失败都不留下半截状态 ----------
        OntologySpace space = requireSpace(param.getSpaceId());
        String schema = space.getApiName();
        String objectApiName = StringUtils.trim(param.getApiName());
        PreconditionUtils.checkArgument(IDENTIFIER_PATTERN.matcher(objectApiName).matches(),
                "对象apiName不合法，无法作为实体表名：" + objectApiName, HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(!MAIN_STORAGE_GROUP.equals(objectApiName),
                "对象apiName不能为" + MAIN_STORAGE_GROUP + "：主表属性的存储分组就叫" + MAIN_STORAGE_GROUP,
                HttpStatus.BAD_REQUEST);

        Map<String, OntologyBuildFromDatasourceParam.SelectedTable> tables = normalizeTables(param.getTables());
        OntologyBuildFromDatasourceParam.SelectedTable master = requireMaster(tables);
        // 子表勾选的 id 列直接丢弃：实体表建表自带 id SERIAL PRIMARY KEY，属性 apiName 又要全对象唯一，
        // 子表的 id 注定建不成属性；主表的 id 仍作为平台主键属性保留
        List<String> droppedChildIds = dropChildPlatformPkColumns(tables, master);
        List<String> childTables = tables.keySet().stream().filter(name -> !name.equals(master.getTableName())).toList();
        PreconditionUtils.checkArgument(!childTables.contains(objectApiName),
                "子表名不能与对象apiName相同：" + objectApiName + "（属性存储分组不允许与对象同名）", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(!childTables.contains(MAIN_STORAGE_GROUP),
                "子表名不能为" + MAIN_STORAGE_GROUP + "：子表的存储分组名即实体表名，会与主表分组冲突",
                HttpStatus.BAD_REQUEST);

        List<OntologyBuildFromDatasourceParam.ForeignKeyParam> foreignKeys =
                validateForeignKeys(master, childTables, param.getForeignKeys());

        // 定案：属性 apiName 在一个对象内必须唯一，跨主子表重名整单 400
        validateColumnNamesUnique(tables, master);

        // 类型与列注释以现场扫描为准：向导页打开后外部库可能已经改过表
        Map<String, Map<String, DatasourceTableDetailVO.Column>> liveColumns =
                scanLiveColumns(param.getSpaceId(), param.getSchemaName(), new ArrayList<>(tables.keySet()));
        List<PropertySpec> specs = buildPropertySpecs(master, childTables, tables, liveColumns);
        // 建表列清单同样在 Phase 0 算完：外键引用的列不存在时，报错要早于任何写库动作
        Map<String, EntityColumns> entityColumns = buildEntityColumns(master, tables, specs, foreignKeys, liveColumns);

        // ---------- Phase 1：主库 ontology ----------
        var metaParam = new OntologyMetaCreateParam()
                .setDisplayName(StringUtils.trim(param.getDisplayName()))
                .setApiName(objectApiName)
                .setDescription(param.getDescription())
                .setCategoryId(param.getCategoryId());
        metaParam.setSpaceId(param.getSpaceId());
        String uniqueIdentifier = ontologyMetaService.createOntology(metaParam);

        ontologyPropertyService.batchCreateProperties(toCreateParams(uniqueIdentifier, specs));

        // ---------- Phase 2：数据湖 entity_datasource 的 {空间apiName} schema ----------
        tableMetadataMapper.initSpaceSchema(schema);
        List<EntityTable> entityTables = createEntityTables(schema, objectApiName, param.getDisplayName(), entityColumns);
        int mappingCount = writeMappings(schema, objectApiName, foreignKeys);

        // ---------- Phase 3：属性回填数据源，让 genericQuery 能解析出 "schema"."table"."column" ----------
        bindProperties(uniqueIdentifier, schema, objectApiName, specs);

        // 通知数据组织层建数据管道；消息格式与 autoBindDatasource 一致，额外带上对象来源（数据源 / 外部 schema / 源表 / 源列）
        notifyPipelineBuild(param, uniqueIdentifier, schema, entityColumns, entityTables);

        int pipeColumnCount = entityColumns.values().stream()
                .mapToInt(columns -> columns.ddlColumns().size()).sum() - specs.size();
        List<String> entityTableNames = entityTables.stream().map(EntityTable::tableName).toList();
        String droppedNote = droppedChildIds.isEmpty()
                ? ""
                : "，已跳过子表 id 列：" + String.join("、", droppedChildIds);
        String message = String.format("对象【%s】已创建：%d 个属性、%d 张实体表（%s）、%d 条主子表列映射、%d 个仅作关联用的管道列%s；",
                StringUtils.trim(param.getDisplayName()), specs.size(), entityTables.size(),
                String.join("、", entityTableNames), mappingCount, pipeColumnCount, droppedNote);
        log.info("数据源构建对象完成 spaceId={}, schemaName={}, uid={}, {}",
                param.getSpaceId(), param.getSchemaName(), uniqueIdentifier, message);

        return OntologyBuildFromDatasourceResultVO.builder()
                .uniqueIdentifier(uniqueIdentifier)
                .apiName(objectApiName)
                .displayName(StringUtils.trim(param.getDisplayName()))
                .propertyCount(specs.size())
                .entityTables(entityTableNames)
                .mappingCount(mappingCount)
                .message(message)
                .build();
    }

    /**
     * 取空间并校验其 apiName：空间 apiName 就是数据湖里该空间的 schema 名，缺了整条链路都落不了。
     */
    private OntologySpace requireSpace(Integer spaceId) {
        OntologySpace space = ontologySpaceMapper.selectById(spaceId);
        PreconditionUtils.checkNotNull(space, "对象空间不存在：spaceId=" + spaceId, HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(space.getApiName()),
                "对象空间apiName为空，无法确定数据湖schema：spaceId=" + spaceId, HttpStatus.BAD_REQUEST);
        return space;
    }

    /**
     * 表名去空格、去重，返回保持请求顺序的表集合。
     */
    private Map<String, OntologyBuildFromDatasourceParam.SelectedTable> normalizeTables(
            List<OntologyBuildFromDatasourceParam.SelectedTable> requested) {
        Map<String, OntologyBuildFromDatasourceParam.SelectedTable> tables = new LinkedHashMap<>();
        for (var table : requested) {
            String tableName = StringUtils.trim(table.getTableName());
            table.setTableName(tableName);
            table.setSourceTableName(StringUtils.defaultIfBlank(StringUtils.trim(table.getSourceTableName()), tableName));
            PreconditionUtils.checkArgument(IDENTIFIER_PATTERN.matcher(tableName).matches(),
                    "表名不合法，无法作为属性存储分组：" + tableName, HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(!tables.containsKey(tableName),
                    "表重复选择：" + tableName, HttpStatus.BAD_REQUEST);
            List<String> columns = table.getColumns().stream()
                    .map(column -> {
                        column.setColumnName(StringUtils.trim(column.getColumnName()));
                        column.setSourceColumnName(StringUtils.defaultIfBlank(
                                StringUtils.trim(column.getSourceColumnName()), column.getColumnName()));
                        return column.getColumnName();
                    })
                    .toList();
            // 列名既是属性 apiName 又是湖内物理列名，@Pattern 在编程式调用里不生效，这里必须自己拦
            PreconditionUtils.checkArgument(columns.stream().allMatch(IDENTIFIER_PATTERN.asPredicate()),
                    "表" + tableName + "存在不合法的列名（只能由字母、数字、下划线、$ 组成且不以数字开头）",
                    HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(columns.stream().distinct().count() == columns.size(),
                    "表" + tableName + "内列重复勾选", HttpStatus.BAD_REQUEST);
            tables.put(tableName, table);
        }
        return tables;
    }

    /**
     * 只选一张表时它即主表（请求里不必带 master）；多张表时必须且只能指定一张主表。
     */
    private OntologyBuildFromDatasourceParam.SelectedTable requireMaster(
            Map<String, OntologyBuildFromDatasourceParam.SelectedTable> tables) {
        if (tables.size() == 1) {
            return tables.values().iterator().next();
        }
        List<OntologyBuildFromDatasourceParam.SelectedTable> masters = tables.values().stream()
                .filter(table -> Boolean.TRUE.equals(table.getMaster()))
                .toList();
        PreconditionUtils.checkArgument(masters.size() == 1,
                "选择多张表时必须且只能指定一张主表，当前指定了" + masters.size() + "张", HttpStatus.BAD_REQUEST);
        return masters.get(0);
    }

    /**
     * 主子表外键校验：多张表时每张子表恰好一条，主表侧必须是本次的主表。
     *
     * <p>外键列<b>不要求</b>勾成属性：属性 apiName 在对象内必须唯一，而 {@code ship.ship_id ↔ ship_weapon.ship_id}
     * 这种主子表同名外键一旦两侧都勾成属性就必然撞名。没勾的一侧由 {@code buildEntityColumns} 只建物理列当管道列，
     * {@code genericQuery} 的 JOIN 只看 {@code table_field_mapping} 里的列名，不需要属性。</p>
     */
    private List<OntologyBuildFromDatasourceParam.ForeignKeyParam> validateForeignKeys(
            OntologyBuildFromDatasourceParam.SelectedTable master, List<String> childTables,
            List<OntologyBuildFromDatasourceParam.ForeignKeyParam> requested) {
        if (childTables.isEmpty()) {
            PreconditionUtils.checkArgument(CollectionUtils.isEmpty(requested),
                    "只选了一张表，不需要配置主子表外键", HttpStatus.BAD_REQUEST);
            return List.of();
        }
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(requested),
                "选择多张表时必须配置每张子表指向主表的外键列对", HttpStatus.BAD_REQUEST);

        List<OntologyBuildFromDatasourceParam.ForeignKeyParam> validated = new ArrayList<>();
        Map<String, OntologyBuildFromDatasourceParam.ForeignKeyParam> byChild = new HashMap<>();
        for (var foreignKey : requested) {
            String masterTable = StringUtils.trim(foreignKey.getMasterTable());
            String childTable = StringUtils.trim(foreignKey.getChildTable());
            String masterColumn = StringUtils.trim(foreignKey.getMasterColumn());
            String childColumn = StringUtils.trim(foreignKey.getChildColumn());
            PreconditionUtils.checkArgument(masterTable.equals(master.getTableName()),
                    "外键的主表必须是本次的主表" + master.getTableName() + "，收到：" + masterTable, HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(childTables.contains(childTable),
                    "外键的子表不在本次勾选的表里：" + childTable, HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(!childTable.equals(masterTable),
                    "主子表不能是同一张表：" + childTable, HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(!byChild.containsKey(childTable),
                    "子表" + childTable + "配置了多条外键；实体表映射每对主子表只能存一条", HttpStatus.BAD_REQUEST);
            // 外键列没勾成属性时会被当成管道列拼进建表 DDL，字符集必须在这里拦住
            PreconditionUtils.checkArgument(IDENTIFIER_PATTERN.matcher(masterColumn).matches()
                            && IDENTIFIER_PATTERN.matcher(childColumn).matches(),
                    "外键列名不合法，无法作为实体表列名：" + masterColumn + " / " + childColumn, HttpStatus.BAD_REQUEST);

            foreignKey.setMasterTable(masterTable);
            foreignKey.setChildTable(childTable);
            foreignKey.setMasterColumn(masterColumn);
            foreignKey.setChildColumn(childColumn);
            byChild.put(childTable, foreignKey);
            validated.add(foreignKey);
        }
        List<String> missing = childTables.stream().filter(child -> !byChild.containsKey(child)).toList();
        PreconditionUtils.checkArgument(missing.isEmpty(),
                "以下子表没有配置指向主表的外键：" + String.join("、", missing), HttpStatus.BAD_REQUEST);
        return validated;
    }

    /**
     * 丢弃子表勾选的 {@code id} 列。实体表建表时每张表都自带 {@code id SERIAL PRIMARY KEY}，
     * 属性 apiName 又要在一个对象内唯一，子表的 id 无法再落成属性；主表的 id 不受影响。
     *
     * @return 被丢弃的「子表.id」清单，供结果消息提示
     */
    private List<String> dropChildPlatformPkColumns(
            Map<String, OntologyBuildFromDatasourceParam.SelectedTable> tables,
            OntologyBuildFromDatasourceParam.SelectedTable master) {
        List<String> dropped = new ArrayList<>();
        for (var table : tables.values()) {
            if (table.getTableName().equals(master.getTableName())) {
                continue;
            }
            if (table.getColumns().stream().noneMatch(column -> PK_COLUMN_NAME.equals(column.getColumnName()))) {
                continue;
            }
            table.setColumns(table.getColumns().stream()
                    .filter(column -> !PK_COLUMN_NAME.equals(column.getColumnName()))
                    .collect(Collectors.toCollection(ArrayList::new)));
            dropped.add(table.getTableName() + "." + PK_COLUMN_NAME);
            log.info("子表{}勾选了 id 列，与平台主键同名，已跳过该属性", table.getTableName());
        }
        return dropped;
    }

    /**
     * 列名查重：属性 apiName 在一个对象内必须唯一（跨主子表也不能重名），重名整单 400。
     * 主表未勾 id 时后台要补的平台主键也占这个名字，一并纳入查重。
     */
    private void validateColumnNamesUnique(Map<String, OntologyBuildFromDatasourceParam.SelectedTable> tables,
                                           OntologyBuildFromDatasourceParam.SelectedTable master) {
        Map<String, String> ownerTableByColumn = new LinkedHashMap<>();
        for (var table : tables.values()) {
            for (var column : table.getColumns()) {
                String columnName = column.getColumnName();
                String existing = ownerTableByColumn.get(columnName);
                PreconditionUtils.checkArgument(existing == null,
                        String.format("列名【%s】在表%s与表%s中重复；属性标识在一个对象内必须唯一，请取消其中一张表的勾选",
                                columnName, existing, table.getTableName()),
                        HttpStatus.BAD_REQUEST);
                ownerTableByColumn.put(columnName, table.getTableName());
            }
        }
        if (!selectedColumns(master).contains(PK_COLUMN_NAME)) {
            String owner = ownerTableByColumn.get(PK_COLUMN_NAME);
            PreconditionUtils.checkArgument(owner == null,
                    "列名【id】已被表" + owner + "勾选；主表需要平台主键id，请取消该列勾选或改用其他列名",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 现场重扫外部库，返回「表名 → 列名 → 列信息」；勾选的表已经不存在时直接 400。
     */
    private Map<String, Map<String, DatasourceTableDetailVO.Column>> scanLiveColumns(Integer spaceId,
                                                                                     String schemaName,
                                                                                     List<String> tableNames) {
        Map<String, List<DatasourceTableDetailVO.Column>> scanned = datasourceConnectionService
                .listTablesDetail(spaceId, schemaName, tableNames).stream()
                .collect(Collectors.toMap(DatasourceTableDetailVO::getTableName,
                        DatasourceTableDetailVO::getColumns, (a, b) -> a, LinkedHashMap::new));
        Map<String, Map<String, DatasourceTableDetailVO.Column>> live = new LinkedHashMap<>();
        for (String tableName : tableNames) {
            List<DatasourceTableDetailVO.Column> columns = scanned.get(tableName);
            // 区分大小写：MySQL/Linux 上表名大小写敏感，扫不到就是不存在
            PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(columns),
                    "外部库不存在表：" + tableName, HttpStatus.BAD_REQUEST);
            live.put(tableName, columns.stream()
                    .collect(Collectors.toMap(DatasourceTableDetailVO.Column::getColumnName,
                            column -> column, (a, b) -> a, LinkedHashMap::new)));
        }
        return live;
    }

    /**
     * 把勾选列换成属性定义：主表归 main、子表归各自表名，类型取现场扫描的原生类型归一化结果。
     */
    private List<PropertySpec> buildPropertySpecs(OntologyBuildFromDatasourceParam.SelectedTable master,
                                                  List<String> childTables,
                                                  Map<String, OntologyBuildFromDatasourceParam.SelectedTable> tables,
                                                  Map<String, Map<String, DatasourceTableDetailVO.Column>> liveColumns) {
        List<PropertySpec> specs = new ArrayList<>();
        appendTableSpecs(specs, master, MAIN_STORAGE_GROUP, liveColumns);
        childTables.forEach(child -> appendTableSpecs(specs, tables.get(child), child, liveColumns));

        // 主表没勾 id 时补平台主键：实体表的 id 列是建表语句自带的，属性不补就绑不上、genericQuery 直接 400
        boolean masterHasId = specs.stream()
                .anyMatch(spec -> MAIN_STORAGE_GROUP.equals(spec.storageGroup()) && PK_COLUMN_NAME.equals(spec.apiName()));
        if (!masterHasId) {
            specs.add(new PropertySpec(PK_COLUMN_NAME, "主键", "主键", OntologyDataTypeEnum.Int,
                    MAIN_STORAGE_GROUP, true, null));
        }
        return specs;
    }

    private void appendTableSpecs(List<PropertySpec> specs, OntologyBuildFromDatasourceParam.SelectedTable table,
                                  String storageGroup,
                                  Map<String, Map<String, DatasourceTableDetailVO.Column>> liveColumns) {
        Map<String, DatasourceTableDetailVO.Column> columns = liveColumns.get(table.getTableName());
        for (var selected : table.getColumns()) {
            DatasourceTableDetailVO.Column live = columns.get(selected.getColumnName());
            PreconditionUtils.checkNotNull(live, String.format("外部库表%s不存在列%s（可能已改名或删除）",
                    table.getTableName(), selected.getColumnName()), HttpStatus.BAD_REQUEST);
            String comment = StringUtils.trimToNull(live.getDescription());
            String displayName = StringUtils.defaultIfBlank(StringUtils.trim(selected.getDisplayName()),
                    StringUtils.defaultIfBlank(comment, selected.getColumnName()));
            String description = StringUtils.defaultIfBlank(StringUtils.trim(selected.getDescription()), comment);
            boolean platformPk = PK_COLUMN_NAME.equals(selected.getColumnName())
                    && MAIN_STORAGE_GROUP.equals(storageGroup);
            // 平台主键 id 在湖里是 id SERIAL PRIMARY KEY，外部库那列是什么类型都不跟
            OntologyDataTypeEnum dataType = platformPk
                    ? OntologyDataTypeEnum.Int
                    : OntologyDataTypeEnum.valueOfJdbcType(live.getNativeType());
            specs.add(new PropertySpec(selected.getColumnName(), displayName, description, dataType,
                    storageGroup, platformPk, selected.getSourceColumnName()));
        }
    }

    /**
     * 组装每张实体表的列：勾成属性的列 + 外键两侧没勾成属性的「管道列」。
     * 管道列不建属性，但必须建物理列，否则 genericQuery 按映射里的列名 JOIN 会找不到列。
     *
     * <p>每张表同时产出两份列清单：建表 DDL 用的（描述已按 SQL 转义）和拼数据管道消息用的
     * （描述保持原文，并带上外部库原始列名 sourceColumnName）。</p>
     */
    private Map<String, EntityColumns> buildEntityColumns(
            OntologyBuildFromDatasourceParam.SelectedTable master,
            Map<String, OntologyBuildFromDatasourceParam.SelectedTable> tables,
            List<PropertySpec> specs,
            List<OntologyBuildFromDatasourceParam.ForeignKeyParam> foreignKeys,
            Map<String, Map<String, DatasourceTableDetailVO.Column>> liveColumns) {
        Map<String, EntityColumns> columnsByGroup = new LinkedHashMap<>();
        for (var spec : specs) {
            columnsByGroup.computeIfAbsent(spec.storageGroup(), group -> new EntityColumns(
                            sourceTableName(group, master, tables), new ArrayList<>(), new ArrayList<>()))
                    .ddlColumns().add(toColumnDesc(spec.apiName(), spec.dataType(), spec.displayName()));
            columnsByGroup.get(spec.storageGroup()).eventColumns().add(toEventColumn(spec));
        }
        for (var foreignKey : foreignKeys) {
            addPipeColumn(columnsByGroup.computeIfAbsent(MAIN_STORAGE_GROUP, group -> new EntityColumns(
                            sourceTableName(MAIN_STORAGE_GROUP, master, tables), new ArrayList<>(), new ArrayList<>())),
                    master.getTableName(), foreignKey.getMasterColumn(), liveColumns, null);
            addPipeColumn(columnsByGroup.computeIfAbsent(foreignKey.getChildTable(), group -> new EntityColumns(
                            sourceTableName(foreignKey.getChildTable(), master, tables), new ArrayList<>(), new ArrayList<>())),
                    foreignKey.getChildTable(), foreignKey.getChildColumn(), liveColumns, foreignKey.getMasterColumn());
        }
        return columnsByGroup;
    }

    /**
     * 存储分组对应的外部库原始表名：主表取主表的 sourceTableName，其余分组就是子表名。
     */
    private String sourceTableName(String storageGroup,
                                   OntologyBuildFromDatasourceParam.SelectedTable master,
                                   Map<String, OntologyBuildFromDatasourceParam.SelectedTable> tables) {
        return MAIN_STORAGE_GROUP.equals(storageGroup)
                ? master.getSourceTableName()
                : tables.get(storageGroup).getSourceTableName();
    }

    private EntityDatasourceColumnDTO toEventColumn(PropertySpec spec) {
        return EntityDatasourceColumnDTO.builder()
                .columnName(spec.apiName())
                .sourceColumnName(spec.sourceColumnName())
                .datasourceColumnType(OntologyDataTypeEnum.transfer2Pg(spec.dataType()))
                .description(spec.displayName())
                .isPrimaryKey(spec.primaryKey())
                .isAssociateKey(false)
                .build();
    }

    /**
     * 追加一个只做关联用的管道列：已经是属性列的跳过，平台主键 id 由建表语句自带所以也跳过。
     *
     * @param associateColumnName 非空时把该列标记为关联列，指向主表被引用的列名（子表外键侧）
     */
    private void addPipeColumn(EntityColumns columns, String tableName, String columnName,
                               Map<String, Map<String, DatasourceTableDetailVO.Column>> liveColumns,
                               String associateColumnName) {
        if (PK_COLUMN_NAME.equals(columnName)
                || columns.ddlColumns().stream().anyMatch(column -> columnName.equalsIgnoreCase(column.getColumnName()))) {
            return;
        }
        DatasourceTableDetailVO.Column live = liveColumns.getOrDefault(tableName, Map.of()).get(columnName);
        PreconditionUtils.checkNotNull(live, String.format("外键引用的列%s.%s在外部库不存在（可能已改名或删除）",
                tableName, columnName), HttpStatus.BAD_REQUEST);
        OntologyDataTypeEnum dataType = OntologyDataTypeEnum.valueOfJdbcType(live.getNativeType());
        String description = StringUtils.trimToNull(live.getDescription());
        columns.ddlColumns().add(toColumnDesc(columnName, dataType, description));
        columns.eventColumns().add(EntityDatasourceColumnDTO.builder()
                .columnName(columnName)
                .sourceColumnName(columnName)
                .datasourceColumnType(OntologyDataTypeEnum.transfer2Pg(dataType))
                .description(description)
                .isPrimaryKey(false)
                .isAssociateKey(associateColumnName != null)
                .associateColumnName(associateColumnName)
                .build());
    }

    private TableColumnDesc toColumnDesc(String columnName, OntologyDataTypeEnum dataType, String description) {
        return TableColumnDesc.builder()
                .columnName(columnName)
                .type(OntologyDataTypeEnum.transfer2Pg(dataType))
                // 建表 DDL 里注释是 '${col.description}' 直接拼进去的，单引号必须成对转义
                .description(escapeSqlText(description))
                .isPrimaryKey(PK_COLUMN_NAME.equals(columnName))
                .build();
    }

    /**
     * 组装属性创建入参。数据源三字段此时留空——实体表还没建，先建表再回填，顺序与画布手工「建属性 → 关联数据源」一致。
     */
    private List<OntologyPropertyCreateParam> toCreateParams(String uniqueIdentifier, List<PropertySpec> specs) {
        List<OntologyPropertyCreateParam> params = new ArrayList<>();
        for (var spec : specs) {
            var propertyParam = new OntologyPropertyCreateParam()
                    .setDisplayName(spec.displayName())
                    .setApiName(spec.apiName())
                    .setDataType(spec.dataType())
                    .setDescription(spec.description())
                    .setIsPrimaryKey(spec.primaryKey())
                    .setIsTitleKey(false)
                    .setStorageGroup(spec.storageGroup());
            propertyParam.setOntologyIdentifier(uniqueIdentifier);
            params.add(propertyParam);
        }
        return params;
    }

    /**
     * 在数据湖空间 schema 下建实体表：主表名取对象 apiName，子表名取子表原名（即存储分组名）。
     * 表已存在时走 addColumns，与 {@code autoBindDatasource} 的处置一致。
     */
    private List<EntityTable> createEntityTables(String schema, String objectApiName, String objectDisplayName,
                                                 Map<String, EntityColumns> columnsByGroup) {
        // 主表先建，子表后建：子表的列映射引用的是主表
        List<String> groups = new ArrayList<>();
        if (columnsByGroup.containsKey(MAIN_STORAGE_GROUP)) {
            groups.add(MAIN_STORAGE_GROUP);
        }
        columnsByGroup.keySet().stream().filter(group -> !MAIN_STORAGE_GROUP.equals(group)).forEach(groups::add);

        String displayName = StringUtils.trim(objectDisplayName);
        List<EntityTable> entityTables = new ArrayList<>();
        for (String group : groups) {
            String tableName = entityTableName(group, objectApiName);
            TableDesc table = TableDesc.builder()
                    .tableName(tableName)
                    .description(escapeSqlText(MAIN_STORAGE_GROUP.equals(group)
                            ? displayName : displayName + "_" + group))
                    .build();
            List<TableColumnDesc> columns = columnsByGroup.get(group).ddlColumns();
            boolean created = !Boolean.TRUE.equals(tableMetadataMapper.isTableExist(schema, tableName));
            if (!created) {
                tableMetadataMapper.addColumns(schema, tableName, columns);
                log.info("实体表{}已存在，按本次勾选列与管道列补齐", tableName);
            } else {
                // createTable 自带 id SERIAL PRIMARY KEY 并跳过任何名为 id 的列，无需在这里重复声明主键
                tableMetadataMapper.createTable(schema, table, columns);
            }
            entityTables.add(new EntityTable(group, tableName, created));
        }
        return entityTables;
    }

    /**
     * 建对象完成后通知数据组织层建数据管道。消息结构与
     * {@code autoBindDatasource} / {@code notify_build_pipeline} 完全一致，额外带上对象来源：
     * 外部数据源（id / 名称 / dbType）、外部 schema、每张实体表的原始表名与每列的原始列名。
     */
    @SneakyThrows
    private void notifyPipelineBuild(OntologyBuildFromDatasourceParam param, String uniqueIdentifier,
                                     String lakeSchema, Map<String, EntityColumns> columnsByGroup,
                                     List<EntityTable> entityTables) {
        DatasourceConnection connection = datasourceConnectionService.getBySpaceId(param.getSpaceId());
        for (var entityTable : entityTables) {
            EntityColumns columns = columnsByGroup.get(entityTable.storageGroup());
            List<EntityDatasourceColumnDTO> eventColumns = new ArrayList<>(columns.eventColumns());
            // 湖内建表语句给每张表都自带 id SERIAL PRIMARY KEY；新建表的消息里补齐这一列，来源列留空（平台生成）
            if (entityTable.created() && eventColumns.stream()
                    .noneMatch(column -> Boolean.TRUE.equals(column.getIsPrimaryKey()))) {
                eventColumns.add(EntityDatasourceColumnDTO.builder()
                        .columnName(PK_COLUMN_NAME)
                        .datasourceColumnType("int4")
                        .description("主键")
                        .isPrimaryKey(true)
                        .isAssociateKey(false)
                        .build());
            }
            var datasourceDTO = EntityDatasourceDTO.builder()
                    .tableName("entity_datasource." + lakeSchema + "." + entityTable.tableName())
                    .isMainDatasource(MAIN_STORAGE_GROUP.equals(entityTable.storageGroup()))
                    .sourceTableName(columns.sourceTableName())
                    .columns(eventColumns)
                    .build();
            var event = EntityDatasourceSchemaChangeEventDTO.builder()
                    .ontologyUniqueIdentifier(uniqueIdentifier)
                    .type(entityTable.created() ? DatasourceEventTypeEnum.CREATE_TABLE : DatasourceEventTypeEnum.ADD_COLUMN)
                    .sourceDatasourceId(connection.getId())
                    .sourceDatasourceName(connection.getName())
                    .sourceDatasourceDbType(connection.getDbType())
                    .sourceSchema(param.getSchemaName())
                    .datasource(List.of(datasourceDTO))
                    .build();
            producer.sendMessage(routingKey, jsonMapper.writeValueAsString(event));
        }
    }

    /**
     * 写主子表列映射。关联列就是用户勾选的那一列，不再额外造 {@code {主表名}_id}；
     * 索引在子表的关联列上，与 {@code autoBindDatasource} 给关联列建索引的做法一致。
     */
    private int writeMappings(String schema, String objectApiName,
                              List<OntologyBuildFromDatasourceParam.ForeignKeyParam> foreignKeys) {
        int mappingCount = 0;
        for (var foreignKey : foreignKeys) {
            tableMetadataMapper.createIndex(schema, foreignKey.getChildTable(), foreignKey.getChildColumn());
            TableFieldMapping existing = tableFieldMappingMapper.selectBySourceAndTarget(
                    schema, objectApiName, foreignKey.getChildTable());
            if (existing != null) {
                boolean same = foreignKey.getMasterColumn().equals(existing.getSourceColumnName())
                        && foreignKey.getChildColumn().equals(existing.getTargetColumnName());
                PreconditionUtils.checkArgument(same,
                        String.format("数据湖里主表%s与子表%s已有列映射（%s → %s），与本次配置不同；"
                                        + "请清理该映射或改用其他对象apiName", objectApiName, foreignKey.getChildTable(),
                                existing.getSourceColumnName(), existing.getTargetColumnName()),
                        HttpStatus.BAD_REQUEST);
                log.info("主表{}与子表{}的列映射已存在且一致，跳过插入", objectApiName, foreignKey.getChildTable());
                continue;
            }
            tableFieldMappingMapper.insertMapping(schema, TableFieldMapping.builder()
                    .sourceTableName(objectApiName)
                    .sourceColumnName(foreignKey.getMasterColumn())
                    .targetTableName(foreignKey.getChildTable())
                    .targetColumnName(foreignKey.getChildColumn())
                    .build());
            mappingCount++;
        }
        return mappingCount;
    }

    /**
     * 把实体表名与列名回填到属性上：{@code datasourceId}=物理表名、{@code datasourceColumnName}=属性 apiName、
     * {@code datasourceSchema}=空间 apiName，字段口径与 {@code autoBindDatasource} 完全一致。
     */
    private void bindProperties(String uniqueIdentifier, String schema, String objectApiName, List<PropertySpec> specs) {
        Map<String, PropertySpec> specByApiName = specs.stream()
                .collect(Collectors.toMap(PropertySpec::apiName, spec -> spec, (a, b) -> a));
        List<OntologyProperty> properties = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, uniqueIdentifier));
        properties.forEach(property -> {
            PropertySpec spec = specByApiName.get(property.getApiName());
            if (spec != null) {
                property.setDatasourceId(entityTableName(spec.storageGroup(), objectApiName))
                        .setDatasourceColumnName(spec.apiName())
                        .setDatasourceSchema(schema);
            }
        });
        ontologyPropertyService.updateBatchById(properties);
    }

    private String entityTableName(String storageGroup, String objectApiName) {
        return MAIN_STORAGE_GROUP.equals(storageGroup) ? objectApiName : storageGroup;
    }

    private Set<String> selectedColumns(OntologyBuildFromDatasourceParam.SelectedTable table) {
        return table.getColumns().stream()
                .map(OntologyBuildFromDatasourceParam.SelectedColumn::getColumnName)
                .collect(Collectors.toSet());
    }

    /**
     * 湖内 DDL 里的注释与表描述都是 MyBatis {@code ${}} 直拼进单引号字面量的，按 SQL 规则把单引号翻倍。
     */
    private String escapeSqlText(String text) {
        return text == null ? null : text.replace("'", "''");
    }

    /**
     * 一条待落库的属性定义；实体表名不在这里存，统一由 {@code storageGroup} 经
     * {@link #entityTableName(String, String)} 推导，避免两处口径不一致。
     */
    private record PropertySpec(String apiName,
                                String displayName,
                                String description,
                                OntologyDataTypeEnum dataType,
                                String storageGroup,
                                boolean primaryKey,
                                String sourceColumnName) { }

    /**
     * 一张实体表要用的列清单：建表 DDL 用的（描述已按 SQL 转义）与拼数据管道消息用的
     * （描述保持原文并带 sourceColumnName），外加这张实体表对应的外部库原始表名。
     */
    private record EntityColumns(String sourceTableName,
                                 List<TableColumnDesc> ddlColumns,
                                 List<EntityDatasourceColumnDTO> eventColumns) { }

    /**
     * 建完的一张实体表：存储分组（main / 子表名）、湖内物理表名，以及本次是否新建
     * （已存在的表只补列，消息类型要发 ADD_COLUMN）。
     */
    private record EntityTable(String storageGroup, String tableName, boolean created) { }
}
