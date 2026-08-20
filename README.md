# Foundry

Foundry 是中国科学院空天信息创新研究院（AIRCAS）的本体（Ontology）平台后端，负责本体建模、实体数据、行为编排、函数运行时与许可证管理。项目采用 Maven 多模块结构，核心服务基于 Spring Boot 构建，使用 PostgreSQL + ArangoDB 混合存储。

当前版本：`0.0.1-SNAPSHOT`  
坐标：`com.aircas.ptr:foundry`

## 模块结构

```
foundry                          # 父工程（packaging: pom）
├── common                       # 公共库：统一返回、异常、工具类、跨域与日志
└── ontology                     # 本体业务聚合模块
    ├── ontology-server          # 核心微服务（可运行 JAR：ontology.jar）
    └── license-generator        # 许可证生成与校验
```

| 模块 | 说明 | 文档 |
|------|------|------|
| **common** | 被各业务模块依赖的公共组件：`RestResult` / `ResultCode`、全局异常处理、CORS、请求日志、雪花 ID、加解密、OWL/GeoTools 等通用能力 | 见下文 [common](#common) |
| **ontology-server** | 本体全生命周期管理服务，对外提供 REST API | [ontology/ontology-server/README.md](ontology/ontology-server/README.md) |
| **license-generator** | 基于 TrueLicense 的证书生成工具；同时作为库被 ontology-server 依赖，用于启动时校验证书 | [ontology/license-generator/README.md](ontology/license-generator/README.md) |

模块依赖关系：

```
ontology-server ──► common
       │
       └──► license-generator
```

## 技术栈

| 技术 | 版本 / 说明 |
|------|-------------|
| JDK | 1.8 |
| Spring Boot | 2.3.12.RELEASE |
| Spring Cloud | Hoxton.SR12 |
| Maven | 3.6+ |
| MyBatis-Plus + tk.mybatis | ORM |
| PostgreSQL | 主业务库（schema: `ontology`）+ 数据湖库（`entity_datasource`） |
| ArangoDB | 图数据库，实体节点与关系 |
| RabbitMQ | 数据源 Schema 变更通知 |
| MinIO | 对象存储 |
| XXL-Job | 分布式任务调度 |
| Quartz | 定时任务 |
| Knife4j / Swagger 2 | API 文档 |
| Druid | 连接池 |
| OWL API | OWL 本体语义 |
| Orekit | 空间动力学计算 |
| Groovy | 函数 / 行为动态脚本 |
| JWT | 用户认证 |
| TrueLicense | 许可证签发与校验 |

## 系统架构

```
                         ┌──────────────────┐
                         │   前端 / 调用方   │
                         └────────┬─────────┘
                                  │  REST  /ontology
                                  ▼
                    ┌─────────────────────────────┐
                    │      ontology-server        │
                    │  建模 · 实体 · 行为 · 函数  │
                    │  JWT 鉴权 · Knife4j 文档    │
                    └──────┬──────┬──────┬────────┘
           ┌───────────────┼──────┼──────┼───────────────┐
           ▼               ▼      ▼      ▼               ▼
    PostgreSQL        PostgreSQL  ArangoDB  RabbitMQ    MinIO
    (Main/ontology)   (Datalake)  (entity)  (schema)    (ptr)
           │
           └──── XXL-Job / Quartz 调度执行 Groovy 函数与行为
```

### 数据分层

| 存储 | 用途 |
|------|------|
| PostgreSQL Main（`currentSchema=ontology`） | 元数据：空间、本体、属性、链接、行为、函数、用户等 |
| PostgreSQL Datalake（`entity_datasource`） | 按存储分组动态生成的实体业务表 |
| ArangoDB（database: `entity`，collections: `node`, `relation`） | 实体节点与关系图 |
| MinIO（bucket: `ptr`） | 文件、图标、缩略图 |
| RabbitMQ | 实体 Schema 变更事件（exchange: `entity.schema.exchange`） |

初始化 DDL 位于 `ontology/ontology-server/src/main/resources/ddl/`，其中 `ontology.sql` 为主库建表脚本，其余 `alter_table_*.sql` 为增量变更。

仓库根目录的 `ontology.owl` 为 OWL 本体样例/语义资源。

## 核心业务

ontology-server 覆盖本体从建模到运行的完整链路：

1. **空间 / 分组 / 分类**：顶层容器与组织方式
2. **本体元数据（Meta）**：本体定义、导入、检索
3. **属性（Property）**：类型、主键/标题键、存储分组、数据源映射、可见性窗口
4. **链接（Link）**：本体间一对一 / 一对多 / 多对多关系
5. **实体（Entity）**：PostgreSQL 行数据与 ArangoDB 图节点/关系同步、查询与生成
6. **行为（Action）**：规则、任务、参数映射、调度启停
7. **函数（Function）**：Groovy 等可复用执行逻辑
8. **数据源（Datasource）**：表/列映射与 Schema 变更通知
9. **词条（Lemma）**：术语管理
10. **RAG**：知识问答与本体构建流程对接
11. **用户**：注册、登录，JWT 访问令牌 / 刷新令牌

更细的包结构与接口说明见 [ontology-server README](ontology/ontology-server/README.md)。

## common

公共模块包路径：`com.aircas.ptr.foundry.common`

| 包 | 职责 |
|----|------|
| `base` | `RestResult`、`ResultCode` 统一响应 |
| `exception` | `BusinessException`、全局异常处理 |
| `config` | CORS |
| `filter` | 请求日志、可缓存 Request |
| `constant` | 本体数据类型、Postgres 类型、函数参数类型 |
| `util` | 日期、HTTP、加解密、雪花 ID、Bean 拷贝、前置校验等 |

## 环境要求

- JDK 1.8+
- Maven 3.6+
- PostgreSQL 12+（主库 schema `ontology`，数据湖库 `entity_datasource`）
- ArangoDB 3.x（库 `entity`，集合 `node`、`relation`）
- RabbitMQ 3.x
- MinIO
- XXL-Job Admin（行为调度需要）

GeoTools 依赖需要 OSGeo 仓库（已在 `common/pom.xml` 中配置）：

```
https://repo.osgeo.org/repository/release/
```

## 构建

在仓库根目录执行：

```bash
# 编译全部模块
mvn clean package -DskipTests

# 仅编译 ontology-server（会自动构建依赖的 common、license-generator）
mvn clean package -pl ontology/ontology-server -am -DskipTests

# 仅编译许可证生成器
mvn clean package -pl ontology/license-generator -am -DskipTests
```

产物：

| 模块 | 产物路径 |
|------|----------|
| ontology-server | `ontology/ontology-server/target/ontology.jar` |
| ontology-server（加密包） | `ontology/ontology-server/target/ontology-encrypted.jar`（classfinal 插件） |
| license-generator | `ontology/license-generator/target/license-generator-0.0.1-SNAPSHOT-exec.jar` |

## 运行

### ontology-server

1. 准备 PostgreSQL、ArangoDB、RabbitMQ、MinIO（以及按需的 XXL-Job）。
2. 执行 `ontology/ontology-server/src/main/resources/ddl/ontology.sql` 初始化主库。
3. 按环境修改 `ontology/ontology-server/src/main/resources/application.yml`（数据源、消息队列、对象存储、调度中心等）。
4. 启动：

```bash
java -jar ontology/ontology-server/target/ontology.jar
```

默认监听：

| 项 | 值 |
|----|----|
| 端口 | `37002` |
| 上下文路径 | `/ontology` |
| API 文档 | http://localhost:37002/ontology/doc.html |
| 应用名 | `ontology` |

认证：除 `/user/login`、`/user/create` 及 Swagger 相关路径外，其余接口需在请求头携带 Access Token（由登录接口写入响应头）。

许可证：`application.yml` 中 `license.enable` 默认为 `false`。开启后需配置 `license.lic` 与公钥库 `publicCerts.keystore`，启动时校验失败会退出进程。

### license-generator

```bash
# 绑定指定 IP / MAC
java -jar ontology/license-generator/target/license-generator-0.0.1-SNAPSHOT-exec.jar \
  ip=192.168.1.1,192.168.1.2 \
  mac=00:1A:2B:3C:4D:5E,00:1A:2B:3C:4D:5F

# 不传参数时读取本机网卡信息
java -jar ontology/license-generator/target/license-generator-0.0.1-SNAPSHOT-exec.jar
```

成功后在当前目录生成 `license.lic`。密钥生成与客户端校验步骤见 [license-generator README](ontology/license-generator/README.md)。

## 配置要点

主配置文件：`ontology/ontology-server/src/main/resources/application.yml`

| 配置项 | 说明 |
|--------|------|
| `server.port` / `server.servlet.context-path` | 服务端口与上下文路径 |
| `spring.datasource.main` | 主业务库（PostgreSQL） |
| `spring.datasource.datalake` | 数据湖库（PostgreSQL） |
| `spring.datasource.arangodb` | ArangoDB 连接 |
| `spring.rabbitmq` / `rabbitmq.*` | 消息队列及 Schema 变更路由 |
| `minio.*` | 对象存储 |
| `xxl.job.*` | XXL-Job 调度中心与执行器 |
| `jwt.*` | Access / Refresh Token 过期时间 |
| `license.*` | 许可证校验开关与证书路径 |
| `orekit.data.path` | Orekit 空间动力学数据目录 |
| `rag.*` | RAG 知识问答与构建服务地址 |
| `ontology.swagger.enable` | Knife4j 文档开关 |

`bootstrap.properties` 中保留了历史 PostgreSQL 环境变量占位（`PG_DB_HOST` 等），当前 `application.yml` 使用显式 JDBC URL。

## 主要 API

上下文路径为 `/ontology`，下列路径均相对于该前缀。

| 前缀 | 说明 |
|------|------|
| `/user` | 登录、注册 |
| `/space` | 空间 |
| `/group` | 分组 |
| `/category` | 分类树 |
| `/meta` | 本体元数据 |
| `/property` | 属性、分类、元数据 Schema、数据源绑定 |
| `/link` | 链接关系 |
| `/entity` | 实体查询、节点/关系、行为执行 |
| `/action` | 行为与调度 |
| `/function` | 函数定义与执行 |
| `/datasource` | 数据源表/列 |
| `/lemma` | 词条 |
| `/file` | 文件上传与预览 URL |
| `/rag` | RAG 知识问答与构建 |
| `/overview/count` | 概览统计 |

完整接口以启动后的 Knife4j 文档为准。

## 开发约定

- 统一响应体：`com.aircas.ptr.foundry.common.base.RestResult`
- 业务异常：`BusinessException` + `ResultCode`
- 主库 / 数据湖分 Mapper：`repository/mainMapper`、`repository/datalakeMapper`
- ArangoDB 文档模型：`model/document`
- 请求参数 / 视图对象：`model/param`、`model/vo`
- 扫描包：`com.aircas.ptr.foundry`（ontology-server 启动类排除了默认数据源自动配置，使用自定义双数据源）
