# Foundry AI 代码规范（AI Coding Rules）

> 本文件面向 AI 编码助手（含代码生成、Review、重构），是本仓库生成/修改代码时**必须遵守**的硬性规则。
> 与 `CODING_CONVENTIONS.md` 冲突时，以本文件为准；本文件未覆盖的内容仍遵循 `CODING_CONVENTIONS.md`。

## 1. 适用范围

- 模块：`common`、`ontology-server`、`license-generator` 及后续新增业务模块。
- 语言：Java（Spring Boot 3.x）、MyBatis XML、SQL、YAML/Properties、前端调用示例。
- 场景：AI 生成新代码、AI 修改已有代码、AI 提交 Code Review 建议。

## 2. 接口路径命名规范（强制）

### 2.1 基本规则

1. **多单词之间统一使用下划线 `_` 连接**，禁止使用短横线 `-`、驼峰 `camelCase`、帕斯卡 `PascalCase`。
2. 路径段（path segment）全部使用**小写字母 + 数字 + 下划线**，正则约束：`^[a-z][a-z0-9_]*$`。
3. 路径**名词化**：使用资源名或"资源_动作"的形式；避免动词单独出现（`GET`/`POST`/`PUT`/`DELETE` 已经表达动作语义）。
4. 类级 `@RequestMapping` 使用**资源单数名**（如 `/space`、`/meta`、`/link`）；方法级路径描述子资源或具体动作。
5. 所有接口统一挂在 `server.servlet.context-path=/ontology` 之下，最终 URL 形如：`/ontology/{resource}/{sub_resource_or_action}`。

### 2.2 正例 / 反例

| 场景 | ✅ 正确 | ❌ 错误 |
|------|---------|---------|
| 类级路径 | `@RequestMapping("/ontology_wizard")` | `@RequestMapping("/ontology-wizard")`、`@RequestMapping("/ontologyWizard")` |
| 构建对象 | `@PostMapping("/build_object")` | `@PostMapping("/build-object")`、`@PostMapping("/buildObject")` |
| 构建属性 | `@PostMapping("/build_properties")` | `@PostMapping("/build-properties")` |
| 按本体查询 | `@GetMapping("/by_ontology")` | `@GetMapping("/byOntology")`、`@GetMapping("/by-ontology")` |
| 分组树 | `@GetMapping("/group_tree")` | `@GetMapping("/group/tree")`、`@GetMapping("/groupTree")` |
| 元数据搜索 | `@GetMapping("/meta_search")` | `@GetMapping("/metaSearch")` |
| 批量导入 | `@PostMapping("/batch_import")` | `@PostMapping("/batchImport")` |
| 关系发现 | `@PostMapping("/relation_discovery")` | `@PostMapping("/relation-discovery")` |

### 2.3 路径参数

- 路径变量命名沿用 Java 变量风格 **camelCase**（不受 §2.1 下划线规则约束），但**路径字面量段**仍必须符合下划线规范。
- 示例：

  ```java
  @GetMapping("/by_group/{groupId}")          // ✅ 段用下划线，变量用 camelCase
  @DeleteMapping("/{link_uniq_identifier}")    // ❌ 变量段禁止下划线
  @DeleteMapping("/{linkUniqIdentifier}")      // ✅
  ```

### 2.4 RESTful 语义与动作命名

推荐的动作词（多词一律下划线）：

| 语义 | 推荐路径 | HTTP 方法 |
|------|----------|-----------|
| 列表 | `/list` 或 `""`（类级资源名即列表） | GET |
| 详情 | `/{id}` | GET |
| 创建 | `""` 或 `/create` | POST |
| 更新 | `/{id}` 或 `/update` | PUT |
| 删除 | `/{id}` | DELETE |
| 批量删除 | `/batch_delete` | POST/DELETE |
| 导入 | `/import` 或 `/batch_import` | POST |
| 导出 | `/export` | GET/POST |
| 搜索 | `/search` | GET/POST |
| 统计 | `/statistics`、`/count_by_group` | GET |
| 校验 | `/check_exists`、`/validate_name` | GET/POST |

### 2.5 查询参数与请求体字段

- Query 参数、`@RequestBody` JSON 字段沿用 **camelCase**（Java / Jackson 默认风格），**不使用下划线**。
- 该规则仅约束 URL 路径段，不改变字段命名习惯。

  ```java
  // ✅ 路径下划线 + 字段 camelCase
  @GetMapping("/by_category")
  public RestResult<List<OntologyLinkVO>> listByCategory(
          @RequestParam("categoryId") Long categoryId,
          @RequestParam(value = "spaceUniqIdentifier", required = false) String spaceUniqIdentifier) { ... }
  ```

### 2.6 存量代码处理策略

- 已有使用短横线/驼峰的路径（如 `/ontology-wizard`、`/build-object`）：
  - **AI 不得主动"顺手重命名"**，避免破坏前端契约。
  - 新增同类子路径时，**优先使用下划线**；如与类级短横线路径强绑定，允许在该 Controller 内部保持短横线一致，但需在 PR 描述中标注"存量风格保留"。
  - 涉及接口整体重构或大版本升级时，统一迁移到 `_` 规范并同步更新前端与文档。

## 3. 接口返回数据类型规范（强制）

### 3.1 基本规则

1. **返回值如果是"对象/结构体"，泛型必须是 `VO`**（`com.aircas.ptr.foundry.ontology.model.vo.*VO`）；**禁止**使用 `DTO`、`BO`、`PO`、`Document`、`View`、`Entity`、`Map<String,Object>`、`JSONObject`、`JsonNode`、`Object` 等作为对外返回类型。
2. **简单类型豁免**：以下类型允许直接作为 `RestResult<T>` 的泛型，**不强制包装 VO**：
   - 基本类型 / 包装类型：`String`、`Integer`、`Long`、`Boolean`、`Double`、`BigDecimal`
   - 上述简单类型的集合：`List<String>`、`List<Integer>`、`List<Long>` 等
   - 空返回：`Void`、无泛型 `RestResult`（纯操作类接口 create/update/delete）
   - 典型场景：返回主键 ID、URL 字符串、成功标记、名称列表等
3. 对象/结构体返回统一包装为 `RestResult<XxxVO>` 或 `RestResult<List<XxxVO>>` / `RestResult<Page<XxxVO>>`；**禁止**直接返回裸对象。
4. VO 命名：`{Domain}{Purpose}VO`，放在 `model.vo` 包下，例如 `OntologySpaceVO`、`OntologyLinkDetailVO`、`RelationDiscoveryVO`。
5. VO 字段命名沿用 **camelCase**，与前端 JSON 契约一致；使用 `@ApiModelProperty` 标注含义。
6. 分页返回统一使用 `RestResult<Page<XxxVO>>` 或项目已有的分页 VO 结构，禁止自造 `Map` 分页体。
7. **判定原则**：只要返回值需要"字段名"来表达语义（即 JSON 是对象 `{...}` 或对象数组 `[{...}]`），就必须用 VO；如果 JSON 是纯标量（`"abc"`、`123`、`true`）或标量数组（`["a","b"]`），允许简单类型。

### 3.2 DTO / BO / PO 的正确用法（内部使用，不外泄）

| 类型 | 位置 | 用途 | 是否允许出现在 Controller 返回值 |
|------|------|------|-----------------------------------|
| `Param` | `model.param` | 入参（`@RequestBody` / `@RequestParam`） | 否（仅入参） |
| `VO`    | `model.vo`    | 出参视图对象（对象/结构体） | **是（对象返回唯一允许）** |
| 简单类型 | JDK | `String`/`Integer`/`Long`/`Boolean`/`List<String>` 等 | **是（豁免）** |
| `Void` / 无泛型 `RestResult` | — | 纯操作类接口 | **是（豁免）** |
| `DTO`   | `model.dto`   | Service / Client / MQ 之间内部传输 | ❌ 禁止 |
| `BO`    | `model.bo`    | 业务组装中间态 | ❌ 禁止 |
| `PO`    | `model.po`    | 数据库表映射 | ❌ 禁止 |
| `Document` | `model.document` | ArangoDB 存储模型 | ❌ 禁止 |
| `View`  | `model.view`  | 联查/统计视图映射 | ❌ 禁止 |
| `Map` / `JSONObject` / `JsonNode` / `Object` | — | 临时/无类型结构 | ❌ 禁止（除极少数动态元数据场景，需在 PR 说明） |

### 3.3 转换约定

- **Service 层职责**：Service 可以接收 `Param` / `DTO`，但**对外（返回给 Controller）必须返回 `VO` 或 VO 集合**。
- **转换位置**：`PO → VO`、`DTO → VO`、`Document → VO` 的转换发生在 **Service 实现类内部**（`service.impl`），使用：
  - `BeanUtil.copyProperties`（`common.util.BeanUtil`）；或
  - MapStruct `converter`（放在 `converter` 包）；或
  - 显式手写 setter（字段较少或有派生字段时优先）。
- **Controller 层禁止**出现 `BeanUtil.copyProperties(po, vo)` 之类转换代码；Controller 只做"调用 Service + 包装 `RestResult`"。
- **禁止**把 PO 直接塞进 `RestResult.ofData(po)` 返回给前端。

### 3.4 正例 / 反例

```java
// ✅ 正确：对象返回用 VO
@GetMapping("/{spaceId}")
@ApiOperation("查询空间详情")
public RestResult<OntologySpaceVO> detail(@PathVariable("spaceId") Long spaceId) {
    return RestResult.ofData(ontologySpaceService.detail(spaceId));
}

// ✅ 正确：对象列表返回用 VO
@GetMapping("/list")
public RestResult<List<OntologyLinkVO>> list(@Valid OntologyLinkQueryParam param) {
    return RestResult.ofData(ontologyLinkService.listVO(param));
}

// ✅ 正确：简单类型豁免（返回主键 ID）
@PostMapping
public RestResult<Integer> create(@RequestBody @Valid OntologySpaceCreateParam param) {
    return RestResult.ofData(ontologySpaceService.createSpace(param));
}

// ✅ 正确：简单类型豁免（返回 URL 字符串）
@PostMapping("/url")
public RestResult<String> uploadUrl(@RequestParam("image") MultipartFile image) { ... }

// ✅ 正确：简单类型集合豁免（返回名称列表）
@GetMapping("/storage_group")
public RestResult<List<String>> getStorageGroup(@RequestParam String ontologyUniqueIdentifier) { ... }

// ✅ 正确：纯操作类接口，无泛型 RestResult
@DeleteMapping("/{spaceId}")
public RestResult delete(@PathVariable("spaceId") Integer spaceId) { ... }

// ❌ 错误：对象返回用 DTO
public RestResult<OntologySpaceDTO> detail(Long spaceId) { ... }

// ❌ 错误：对象返回用 PO
public RestResult<OntologySpace> detail(Long spaceId) { ... }

// ❌ 错误：对象返回用 Map / JsonNode
public RestResult<Map<String, Object>> detail(Long spaceId) { ... }
public RestResult<JsonNode> getMetadataSchema(...) { ... }

// ❌ 错误：对象返回用 Object
public RestResult<List<Object>> createEntities(...) { ... }

// ❌ 错误：Service 直接返回 PO，Controller 里再做转换
OntologySpace po = ontologySpaceService.getById(spaceId);
OntologySpaceVO vo = new OntologySpaceVO();
BeanUtil.copyProperties(po, vo);
return RestResult.ofData(vo);
```

### 3.5 存量代码处理策略

- 已有接口返回 `DTO` / `PO` / `Map` 的：
  - **AI 不得顺手改动**返回类型（会破坏前端契约）。
  - 新增接口一律按 VO 规范实现。
  - 涉及该接口的功能迭代时，**同一次改动内**顺带迁移到 VO，并在 PR 说明中标注"返回类型迁移：`XxxDTO` → `XxxVO`，字段保持一致"。

## 4. 持久层 SQL 编写规范（强制）

### 4.1 基本规则

1. **所有 SQL 必须写在 MyBatis XML 映射文件中**（`resources/mybatis-mapper/{main|datalake}/*.xml`），**禁止在 Java 代码里编写 SQL**。
2. **禁止注解式 SQL**：Mapper 接口上不得出现 `@Select`、`@Insert`、`@Update`、`@Delete`、`@SelectProvider`、`@Results` 等 MyBatis SQL 注解。
3. **单表简单条件查询允许用 `QueryWrapper` / `LambdaQueryWrapper`**（等值 `eq`、`in`、`like`、排序、分页等，不含 SQL 字符串、不联表）；但**多表联查、动态 SQL、分组统计、复杂子查询**必须落到 XML 的 `<select>` / `<insert>` / `<update>` / `<delete>`，Mapper 接口只声明方法签名。
   - 说明：MyBatis-Plus `BaseMapper` 内置 CRUD（`selectById`、`insert`、`updateById` 等）与 `LambdaQueryWrapper` 单表条件查询均属类型安全、非手写 SQL，可继续使用。
   - 红线（不因放宽而松动）：Java 里**不得出现 SQL 字符串**（§4.1-1）、不得用 `@Select` 等注解式 SQL（§4.1-2）；写操作不要用 `UpdateWrapper` 拼接，更新一律走 XML 或 `updateById`。
4. Mapper 方法多参数用 `@Param("xxx")` 显式命名，XML 内以 `#{xxx}` 引用。
5. XML `namespace` 必须等于 Mapper 接口全限定名；`resultType` / `resultMap` 指向 PO 或 VO，**禁止用 `Map` 承接结果集**。
6. **多数据源对应关系**：主库（`postgres?currentSchema=ontology`）的 SQL 放 `mybatis-mapper/main/`、Mapper 接口放 `repository.mainMapper`；数据湖库（`entity_datasource`，默认 `public` schema）的 SQL 放 `mybatis-mapper/datalake/`、Mapper 接口放 `repository.datalakeMapper`。二者不可混放。

### 4.2 正例 / 反例

```java
// ❌ 错误：注解式 SQL
@Select("SELECT * FROM datasource_connection WHERE status = 1 AND name LIKE #{kw}")
List<DatasourceConnection> search(String kw);

// ✅ 允许：单表简单条件查询用 LambdaQueryWrapper（类型安全、无 SQL 字符串、不联表）
LambdaQueryWrapper<DatasourceConnection> w = new LambdaQueryWrapper<>();
w.eq(DatasourceConnection::getStatus, 1).like(DatasourceConnection::getName, kw);
return baseMapper.selectList(w);

// ❌ 错误：多表联查 / 动态 SQL / 分组统计用 Wrapper 硬拼，应落 XML

// ✅ 正确：复杂查询 Mapper 只声明方法签名，SQL 落 XML
List<DatasourceConnection> searchByKeyword(@Param("keyword") String keyword);
```

```xml
<!-- ✅ 正确：SQL 落在 mybatis-mapper/datalake/DatasourceConnectionMapper.xml -->
<select id="searchByKeyword" resultType="com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection">
    SELECT id, name, db_type, host, port, db_name, schema_name, description, status, create_time, update_time
    FROM db_connection.datasource_connection
    WHERE status = 1
    <if test="keyword != null and keyword != ''">
        AND (name LIKE CONCAT('%', #{keyword}, '%') OR db_type LIKE CONCAT('%', #{keyword}, '%'))
    </if>
    ORDER BY update_time DESC
</select>
```

## 5. 命名规范（驼峰命名法，强制）

### 5.1 Java 标识符命名

1. **类 / 接口 / 枚举 / 记录（record）**：大驼峰 PascalCase（UpperCamelCase），如 `DatasourceToolVO`、`HttpDatasourceQueryTools`、`DatasourceConnectionMapper`。
2. **方法名 / 变量名 / 字段名**（含 record 组件、VO/DTO/PO 字段、Query 参数、`@RequestBody` 字段）：小驼峰 camelCase（lowerCamelCase），如 `searchDatasources`、`searchByKeyword`、`dbType`、`schemaName`、`createTime`。
3. **常量**（`static final`）：全大写 + 下划线 UPPER_SNAKE_CASE，如 `STATUS_ENABLED`、`DEFAULT_SYSTEM_PROMPT`。
4. **包名**：全小写，不用下划线也不用驼峰，如 `com.aircas.ptr.foundry.ontology.model.vo`。
5. **泛型参数**：单个大写字母，如 `T`、`E`、`K`、`V`。
6. **禁止**拼音命名、无意义缩写；多单词缩写按单词处理（如 `HttpUrl` 而非 `HTTPURL`），但既有分层后缀 `VO`/`DTO`/`PO`/`BO` 约定保留。

### 5.2 边界（不受驼峰规则约束的部分）

| 对象 | 命名风格 | 依据 |
|------|----------|------|
| URL 路径段（`@RequestMapping`/`@GetMapping` 字面量） | **下划线** `snake_case` | 见 §2（如 `/tool/datasource_search`） |
| 数据库表名 / 列名 | **下划线** `snake_case` | 如 `datasource_connection`、`db_type`、`create_time` |
| MyBatis XML `<result column=...>` | `column` 对应 DB 列用 snake_case；`property` 对应 Java 字段用 camelCase | 如 `column="db_type" property="dbType"` |

> 驼峰规则仅约束 **Java 标识符**；URL 路径与数据库命名有各自的 snake_case 约定，二者不冲突。

### 5.3 正例 / 反例

```java
// ✅ 正确
public class DatasourceQueryToolsImpl { }
private String dbType;
private Date createTime;
public List<DatasourceToolVO> searchDatasources(String keyword) { }
private static final Integer STATUS_ENABLED = 1;

// ❌ 错误
public class datasourceQueryToolsImpl { }      // 类名用了小驼峰
private String db_type;                         // 字段用了下划线
private String DbType;                          // 字段用了大驼峰
public List<DatasourceToolVO> SearchDatasources(String keyword) { }  // 方法名用了大驼峰
private static final Integer statusEnabled = 1; // 常量未用全大写
```

## 6. AI 生成代码检查清单

生成或修改任何 Controller / 接口相关代码时，AI 必须自检：

- [ ] 类级 `@RequestMapping` 路径符合 `^[a-z][a-z0-9_]*$`（或存量短横线保留策略）。
- [ ] 方法级 `@GetMapping/@PostMapping/...` 路径多单词使用 `_`。
- [ ] 未出现 `-`、驼峰段（如 `/buildObject`）作为新增路径。
- [ ] 路径变量使用 camelCase，未与路径段规则混淆。
- [ ] Query 参数、Body 字段仍为 camelCase。
- [ ] 统一挂在 `context-path=/ontology` 下，未硬编码 `/ontology` 前缀到 `@RequestMapping`。
- [ ] Knife4j 注解（`@Api`、`@ApiOperation`）齐全，`/doc.html` 可正常展示。
- [ ] 返回值：对象/结构体统一 `RestResult<XxxVO>` / `RestResult<List<XxxVO>>` / `RestResult<Page<XxxVO>>`，未出现 DTO / BO / PO / Map / JsonNode / Object / Document。
- [ ] 简单类型（`String`/`Integer`/`Long`/`Boolean`/`List<String>` 等）与无泛型 `RestResult` 已豁免，无需强制包装 VO。
- [ ] `PO → VO`、`DTO → VO` 转换发生在 `service.impl` 内部，Controller 不含转换代码。
- [ ] VO 类位于 `model.vo` 包，命名为 `{Domain}{Purpose}VO`，字段带 `@ApiModelProperty`。
- [ ] 所有 SQL 写在 MyBatis XML（`mybatis-mapper/{main|datalake}/*.xml`），Java 中无 `@Select/@Insert/@Update/@Delete` 注解 SQL。
- [ ] 单表简单条件查询可用 `LambdaQueryWrapper`；多表联查/动态 SQL/分组统计落 XML；Java 里无 SQL 字符串、无注解式 SQL。
- [ ] Mapper 多参数用 `@Param` 命名；XML `namespace` 与接口全限定名一致，`resultType` 指向 PO/VO 而非 `Map`。
- [ ] 主库 SQL 在 `main/` + `mainMapper` 包；数据湖 SQL 在 `datalake/` + `datalakeMapper` 包，未混放。
- [ ] 类/接口/枚举/record 名用大驼峰 PascalCase；方法/变量/字段（含 record 组件、Query/Body 字段）用小驼峰 camelCase。
- [ ] 常量（`static final`）用全大写下划线 UPPER_SNAKE_CASE；包名全小写。
- [ ] URL 路径段仍为下划线（§2）、DB 表/列名仍为 snake_case，未与 Java 驼峰规则混淆。

## 7. 违规示例（AI 必须拒绝输出）

```java
// ❌ 短横线路径
@RequestMapping("/ontology-wizard")
@PostMapping("/build-object")

// ❌ 驼峰路径
@GetMapping("/byCategory")
@PostMapping("/batchImport")

// ❌ 多级斜杠替代下划线（语义应为一个复合动作时）
@GetMapping("/group/tree")     // 应写作 /group_tree
@GetMapping("/meta/search")    // 应写作 /meta_search（或独立到 MetaController 的 /search）

// ❌ Controller 对象返回非 VO 类型
public RestResult<OntologySpaceDTO> detail(Long id) { ... }
public RestResult<OntologySpace>    detail(Long id) { ... }
public RestResult<Map<String, ?>>   detail(Long id) { ... }
public RestResult<JsonNode>         detail(Long id) { ... }
public RestResult<List<Object>>     create(...)     { ... }

// ✅ 简单类型豁免（允许）
public RestResult<String>        uploadUrl(...)  { ... }
public RestResult<Integer>       create(...)     { ... }
public RestResult<List<String>>  listNames(...)  { ... }
public RestResult                delete(...)     { ... }
```

## 8. 合规示例

```java
@Api(tags = "本体关系发现")
@RestController
@RequestMapping("/relation_discovery")
@RequiredArgsConstructor
@Validated
public class OntologyRelationDiscoveryController {

    private final OntologyRelationDiscoveryService relationDiscoveryService;

    @PostMapping("/analyze")
    @ApiOperation("按空间执行关系发现")
    public RestResult<RelationDiscoveryVO> analyze(@RequestBody @Valid RelationDiscoveryParam param) {
        return RestResult.ofData(relationDiscoveryService.analyze(param));
    }

    @GetMapping("/by_ontology/{ontologyUniqIdentifier}")
    @ApiOperation("查询指定本体下已发现的关系")
    public RestResult<List<RelationDiscoveryVO>> listByOntology(
            @PathVariable("ontologyUniqIdentifier") String ontologyUniqIdentifier) {
        return RestResult.ofData(relationDiscoveryService.listByOntology(ontologyUniqIdentifier));
    }

    @PostMapping("/batch_persist")
    @ApiOperation("批量持久化关系发现结果")
    public RestResult<Integer> batchPersist(@RequestBody @Valid BatchPersistParam param) {
        return RestResult.ofData(relationDiscoveryService.batchPersist(param));
    }
}
```

---

**规则版本**：v1.5（放宽 §4.1-3：允许 `LambdaQueryWrapper` 做单表简单条件查询；多表联查/动态 SQL/统计仍须落 XML；SQL 字符串与注解式 SQL 仍全面禁止。v1.4：新增 §5 命名规范——Java 标识符驼峰、URL 路径与 DB 列名 snake_case）
**维护位置**：`AI_CODING_RULES.md`（仓库根目录）
**变更要求**：调整本文件需同步在 PR 说明中列出影响的 Controller 与前端契约。
