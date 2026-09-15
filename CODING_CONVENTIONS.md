# Foundry 代码开发规约

本文档基于当前仓库模块划分、包结构与既有代码风格整理，新增与修改代码时应遵循。

## 1. 模块与依赖

| 模块 | 职责 | 允许依赖 |
|------|------|----------|
| `common` | 统一响应、异常、工具、CORS、日志等 | 第三方库；**不得**依赖业务模块 |
| `license-generator` | 许可证签发；也可作为库被引用 | 尽量自洽，不依赖 ontology-server |
| `ontology-server` | 本体业务 REST 服务 | `common`、`license-generator` |

原则：

- 公共能力下沉到 `common`，避免在 ontology-server 重复造轮子。
- 业务逻辑只放在 `ontology-server`（及后续业务模块），`common` 不出现领域表/接口语义。
- 新增 Maven 依赖优先加在真正使用它的模块；跨模块复用再上提到父 POM / `common`。

## 2. 包结构（ontology-server）

根包：`com.aircas.ptr.foundry.ontology`

| 包 | 用途 |
|----|------|
| `controller` | REST 入口；参数校验与结果封装，**不写复杂业务** |
| `controller.validator` | 自定义校验注解 / Validator |
| `service` / `service.impl` | 业务接口与实现 |
| `repository.mainMapper` | 主库（schema `ontology`）MyBatis Mapper |
| `repository.datalakeMapper` | 数据湖库 MyBatis Mapper |
| `repository.arangodb` | ArangoDB Repository |
| `model.po` | 持久化实体（表映射） |
| `model.document` | ArangoDB 文档 / 边 |
| `model.param` | 入参（Controller `@RequestBody` / 查询参数） |
| `model.vo` | 出参视图对象 |
| `model.dto` | 服务层内部传输对象 |
| `model.bo` | 业务组装对象 |
| `model.view` | 统计/联查等视图映射 |
| `model.enums` | 枚举 |
| `config` | Spring 配置、Properties |
| `client` | 外部 HTTP / SDK 客户端（如 XXL-Job） |
| `mq.producer` / `mq.consumer` | 消息生产 / 消费 |
| `job` | XXL-Job Handler |
| `intercepter` | 拦截器（鉴权等） |
| `aspect` | AOP |
| `utils` / `util` | 本模块工具（优先复用 `common.util`） |
| `exception` | 本模块错误码映射等 |

新增类放入与职责匹配的包；不要在 `controller` 中直接注入 Mapper 绕过 Service（存量代码若有例外，新代码不要扩大）。

## 3. 命名约定

| 类型 | 命名 | 示例 |
|------|------|------|
| Controller | `Ontology{Domain}Controller` 或领域名 + `Controller` | `OntologySpaceController` |
| Service 接口 | `{Domain}Service` | `OntologySpaceService` |
| Service 实现 | `{Domain}ServiceImpl`，放在 `service.impl` | `OntologySpaceServiceImpl` |
| 主库 Mapper | `{Entity}Mapper`，包 `mainMapper` | `OntologySpaceMapper` |
| 数据湖 Mapper | 按用途命名，包 `datalakeMapper` | `ObjectMapper` |
| Arango Repository | `{Document}Repository` | `EntityNodeRepository` |
| 入参 | `{Action}Param` / `{Domain}{Action}Param` | `OntologySpaceCreateParam` |
| 出参 | `{Domain}VO` | `OntologySpaceVO` |
| 表实体 | 与表语义一致的名词，包 `model.po` | `OntologySpace` |
| 图文档 | `EntityNode` / `EntityRelation` 等，包 `model.document` | |
| 枚举 | `{Name}Enum` 或语义名（如 `Status`） | `OntologyLinkTypeEnum` |
| 配置类 | `{Component}Config` / `{Component}Properties` | `MinioConfig`、`JwtProperties` |

HTTP 路径：短横或资源名词，与现有风格一致（如 `/space`、`/meta`、`/entity`），统一挂在 context-path `/ontology` 下。

## 4. Controller 层

参考现有写法：

```java
@Api(tags = "本体空间管理")
@RestController
@RequestMapping("/space")
@RequiredArgsConstructor
@Validated
public class OntologySpaceController {

    private final OntologySpaceService ontologySpaceService;

    @PostMapping
    @ApiOperation(value = "创建本体空间")
    public RestResult<Integer> createSpace(@RequestBody @Valid OntologySpaceCreateParam param) {
        Integer id = ontologySpaceService.createSpace(param);
        return RestResult.ofData(id);
    }
}
```

要求：

- 返回类型统一为 `RestResult` / `RestResult<T>`（`com.aircas.ptr.foundry.common.base`）。
- 成功：`RestResult.success()` 或 `RestResult.ofData(data)`。
- 入参使用 `@Valid` / `@Validated`；校验注解写在 `param` 上，并提供明确 `message`。
- 使用 Swagger：`@Api`、`@ApiOperation`；Param/VO 使用 `@ApiModel`、`ApiModelProperty`。
- 依赖注入优先构造器注入（`@RequiredArgsConstructor` + `final` 字段）。
- Controller 只做：参数接收、调用 Service、组装 `RestResult`；不写事务与多表编排。

## 5. Service 层

- 接口放在 `service`，实现放在 `service.impl`，实现类加 `@Service`。
- 基于 MyBatis-Plus 的 CRUD 可 `extends IService<PO>` / `ServiceImpl<Mapper, PO>`（与现有一致）。
- 写操作按需加 `@Transactional`（注意主库 / 数据湖分库事务边界，勿假设跨库同一事务）。
- 业务校验优先使用 `PreconditionUtils` 或抛出 `BusinessException`，避免吞异常后返回含糊成功。
- 日志使用 `@Slf4j`；关键路径打 info，异常打 error（带上下文，避免只打堆栈无业务键）。

## 6. 统一响应与异常

| 机制 | 类 | 说明 |
|------|-----|------|
| 成功/失败体 | `RestResult`、`ResultCode` | 对外唯一响应包装 |
| 业务异常 | `BusinessException` | 携带 `msg`、`ResultCode`、可选 `HttpStatus` |
| 全局处理 | `GlobalExceptionHandler`（common） | 统一转换为 `RestResult` |

约定：

- 可预期的业务失败抛 `BusinessException`，不要随意 `return new RestResult<>(ResultCode.ERROR, ...)` 散落魔法字符串（已有写法可沿用，新增优先异常 + 全局处理）。
- 新增错误语义时扩展 `ResultCode` 或模块内错误映射（如 `ErrorCodeMap`），保持 code/message 稳定。
- **不要**把堆栈或内部 SQL 直接返回给前端。

## 7. 数据访问

### 7.1 PostgreSQL 双数据源

- 主库 Mapper → `repository.mainMapper` + `resources/mybatis-mapper/main/`
- 数据湖 Mapper → `repository.datalakeMapper` + `resources/mybatis-mapper/datalake/`
- 配置分别由 `MainDataSourceConfiguration`、`DatalakeDataSourceConfiguration` 管理；禁止混用错误的 SqlSession / 事务管理器。

### 7.2 表结构变更

- 全量/基准脚本：`resources/ddl/ontology.sql`
- 增量变更：`resources/ddl/alter_table_yyyyMMdd.sql`（与现有命名一致）
- 变更需可重复执行或附带清晰手工步骤说明；与代码同一需求一并提交。

### 7.3 ArangoDB

- 文档模型：`model.document`，使用 `@Document` / `@Edge`、`@From` / `@To`。
- 访问：`repository.arangodb`，继承 `ArangoRepository`；复杂查询用 `@Query` AQL。
- 库与集合约定：database `entity`，collections `node`、`relation`；初始化见 `deploy/arangodb`。
- 批量写入参考现有 `batchSave`（分批，避免一次过大）。

## 8. 模型分层

| 类型 | 是否暴露给 API | 说明 |
|------|----------------|------|
| `param` | 入 | 只含请求字段 + 校验 |
| `vo` | 出 | 只含前端需要的字段 |
| `po` | 否 | 表结构映射，不直接作为 API 契约 |
| `document` | 否（一般） | Arango 存储模型 |
| `dto` / `bo` / `view` | 否 | 服务内部或联查映射 |

禁止把 `po` 直接作为 `@RequestBody`；必要时在 Service 内做 Param → PO / PO → VO 转换。

## 9. 配置与外部依赖

- 业务配置集中在 `application.yml`；结构化配置用 `@ConfigurationProperties`（如 `JwtProperties`、`XxlJobProperties`）。
- 中间件客户端放在 `config` / `client`，不要在业务方法里 new 连接。
- 本地中间件端口、账号与 `deploy/`、`application.yml` 保持一致；文档变更时同步更新。
- 密钥、密码禁止提交到仓库明文生产值；示例可用本地默认值并在 README/deploy 中说明。

## 10. 鉴权与安全

- 除登录、注册及 Swagger 白名单外，接口经 `AuthInterceptor` 校验 JWT。
- 用户上下文通过 `UserContextHolder` 获取；不要在业务里解析 Token 重复造轮子。
- 文件上传限制已在配置中声明（大小上限）；新增上传接口需校验类型与路径，避免任意文件覆盖。

## 11. 消息、任务与脚本

- RabbitMQ：生产者/消费者分目录；exchange / queue / routing-key 与配置项 `rabbitmq.*` 一致。
- XXL-Job：Handler 放在 `job`；调度中心地址走 `xxl.job.*`。
- Groovy 脚本执行经既有 `GroovyService` 等封装，注意输入校验与超时，避免任意代码执行面扩大。

## 12. 并发与事务

- 异步任务使用 `ThreadPoolConfig` 中的线程池，不要随意 `new Thread`。
- 长耗时导入/同步考虑分批与进度日志。
- 跨 PostgreSQL 与 ArangoDB 的一致性：明确主从顺序与失败补偿；无法单事务时在注释或文档中写清最终一致性策略。

## 13. 日志与可观测

- 使用 `logback-spring.xml`；业务日志带关键业务键（如 `ontologyUniqIdentifier`、`linkId`）。
- 过滤器 `LoggingFilter` 已记录请求；不要在 Controller 重复打印完整大 body（尤其文件、脚本）。

## 14. API 与兼容性

- 新增接口补充 Knife4j 注解，保证 `/doc.html` 可测。
- 变更已有字段或错误码时评估前端兼容；破坏性变更需在 PR/提交说明中标明。
- JSON：`spring.jackson.default-property-inclusion: non_null`，VO 中无意义的 null 可省略。

## 15. 提交与文档

- 功能涉及中间件或表结构时，同步更新：
  - `README.md`（若影响环境/端口概要）
  - `deploy/<service>/README.md`
  - `resources/ddl` 或 `deploy/arangodb` 说明
- 代码风格与邻域文件保持一致（Lombok、导入顺序、构造器注入等），避免无关大范围格式化。
- 不提交 `.DS_Store`、本地 `data/` 卷目录、密钥与许可证私钥。

## 16. 快速检查清单

开发完成前自检：

- [ ] 类放在正确包，命名符合上表
- [ ] Controller 返回 `RestResult`，入参 `@Valid`
- [ ] 业务失败使用 `BusinessException` / `ResultCode`
- [ ] Mapper 落到正确数据源包与 XML 目录
- [ ] 需要时补充 `ddl/alter_table_*.sql` 或 Arango 说明
- [ ] Swagger 注解完整
- [ ] 未引入 `common` ↔ 业务模块反向依赖
- [ ] 配置项与 `application.yml` / `deploy` 文档一致
