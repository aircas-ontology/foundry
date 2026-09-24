# ontology-server

## 模块概述

ontology-server 是 Foundry 平台的核心微服务模块，负责本体（Ontology）的全生命周期管理，包括本体建模、属性定义、关系链接、实体数据操作、行为编排、函数管理等功能。该模块基于 Spring Boot 构建，采用多数据源架构，支持 PostgreSQL（关系型存储）与 ArangoDB（图数据库）混合存储。

## 技术栈

| 技术 | 用途 |
|------|------|
| Spring Boot 2.x | 应用框架 |
| MyBatis-Plus + tk.mybatis | ORM 框架 |
| PostgreSQL | 主业务库 + 数据湖库 |
| ArangoDB | 图数据库，实体节点与关系存储 |
| RabbitMQ | 消息队列，数据源 Schema 变更通知 |
| MinIO | 对象存储，文件上传 |
| XXL-Job | 分布式任务调度 |
| Quartz | 定时任务调度 |
| Knife4j (Swagger) | API 文档 |
| Druid | 数据库连接池 |
| OWL API | 本体语义处理 |
| Orekit | 空间动力学计算 |
| Groovy | 动态脚本执行（函数运行时） |
| Lombok | 代码简化 |
| FastJSON / Jackson | JSON 序列化 |

## 项目结构

```
ontology-server/
├── src/main/java/com/aircas/ptr/foundry/ontology/
│   ├── OntologyServerApplication.java    # 启动类
│   ├── aspect/                           # AOP 切面（函数参数处理）
│   ├── client/                           # 外部客户端（XXL-Job）
│   ├── config/                           # 配置类
│   │   ├── ArangoConfig.java             # ArangoDB 配置
│   │   ├── DatalakeDataSourceConfiguration.java  # 数据湖数据源
│   │   ├── MainDataSourceConfiguration.java      # 主数据源
│   │   ├── MinioConfig.java              # MinIO 配置
│   │   ├── OrekitDataConfig.java         # Orekit 数据配置
│   │   ├── RabbitMQConfig.java           # RabbitMQ 配置
│   │   ├── ThreadPoolConfig.java         # 线程池配置
│   │   └── XxlJobConfig.java             # XXL-Job 配置
│   ├── controller/                       # REST 控制器
│   │   ├── validator/                    # 自定义校验注解
│   │   ├── FileController.java           # 文件管理
│   │   ├── OntologyActionController.java # 行为管理
│   │   ├── OntologyCategoryController.java # 分类管理
│   │   ├── OntologyDatasourceController.java # 数据源管理
│   │   ├── OntologyEntityController.java # 实体管理
│   │   ├── OntologyFunctionController.java # 函数管理
│   │   ├── OntologyGroupController.java  # 分组管理
│   │   ├── OntologyLemmaController.java  # 词条管理
│   │   ├── OntologyLinkController.java   # 链接关系管理
│   │   ├── OntologyMetaController.java   # 本体元数据管理
│   │   ├── OntologyOverviewController.java # 概览统计
│   │   ├── OntologyPropertyController.java # 属性管理
│   │   └── OntologySpaceController.java  # 空间管理
│   ├── converter/                        # 数据转换器
│   ├── exception/                        # 自定义异常
│   ├── job/                              # XXL-Job 任务处理器
│   ├── mq/producer/                      # RabbitMQ 生产者
│   ├── repository/                       # 数据访问层
│   │   ├── mainMapper/                   # 主库 Mapper
│   │   └── datalakeMapper/               # 数据湖 Mapper
│   ├── service/                          # 业务接口
│   │   └── impl/                         # 业务实现
│   └── model/                            # 数据模型
│       ├── bo/                           # 业务对象
│       ├── common/                       # 公共常量
│       ├── document/                     # 文档模型（ArangoDB 节点/关系）
│       ├── dto/                          # 数据传输对象
│       ├── enums/                        # 枚举类
│       ├── param/                        # 请求参数
│       └── vo/                           # 视图对象
├── src/main/resources/
│   ├── application.yml                   # 主配置文件
│   ├── bootstrap.properties              # 启动配置
│   ├── logback-spring.xml                # 日志配置
│   ├── publicCerts.keystore              # 证书文件
│   ├── mybatis-mapper/                   # MyBatis XML 映射文件
│   ├── ddl/                              # 数据库变更脚本
│   └── orekit-data/                      # Orekit 空间动力学数据
└── pom.xml
```

## 核心业务域

### 1. 空间管理（Space）
本体空间的顶层容器，一个空间下可包含多个本体、分组和分类。

### 2. 本体管理（Meta）
本体的核心定义，包括本体名称、API 名称、所属空间/分组等元数据。支持批量导入创建。

### 3. 属性管理（Property）
定义本体的属性字段，包括：
- 属性类型（基本类型、枚举等）
- 主键/标题键标记
- 存储分组（主表/附表）
- 属性分类与元数据 Schema
- 自动关联数据源（PostgreSQL 表列映射）
- 可见性窗口配置

### 4. 链接关系（Link）
定义本体之间的关联关系，支持：
- 多种链接类型（一对一、一对多、多对多）
- 链接方向（单向/双向）
- 链接映射配置

### 5. 实体管理（Entity）
基于 ArangoDB 图数据库的实体数据操作：
- 实体节点同步（从 PostgreSQL 到 ArangoDB）
- 实体关系创建与删除
- 实体查询与分页

### 6. 行为管理（Action）
本体的行为编排，包括：
- 行为规则配置（HandleRule）
- 行为任务调度（HandleTask）
- 参数映射（MappingIn）
- 调度配置（Scheduling）
- 支持 Groovy 脚本动态执行

### 7. 函数管理（Function）
可复用的函数定义，支持多种模型（Groovy 脚本等），为行为提供执行逻辑。

### 8. 数据源管理（Datasource）
本体属性与 PostgreSQL 表/列的映射关系管理，支持 Schema 变更事件通知（通过 RabbitMQ）。

### 9. 词条管理（Lemma）
本体相关的词条/术语管理。

### 10. RAG 流程
与 RAG（检索增强生成）相关的流程处理。

## 数据架构

```
┌─────────────────────────────────────────────────────┐
│                   PostgreSQL (Main)                  │
│  Schema: ontology                                    │
│  Tables: ontology_meta, ontology_space,              │
│          ontology_property, ontology_link_group,     │
│          ontology_action, function, ...              │
├─────────────────────────────────────────────────────┤
│                  PostgreSQL (Datalake)               │
│  Database: entity_datasource                         │
│  Tables: 动态生成的实体数据表（按存储分组）           │
├─────────────────────────────────────────────────────┤
│                     ArangoDB                         │
│  Database: entity                                    │
│  Collections: node, relation                         │
│  存储实体节点及关系图数据                             │
├─────────────────────────────────────────────────────┤
│                      MinIO                           │
│  Bucket: ptr                                         │
│  文件/图标等对象存储                                  │
└─────────────────────────────────────────────────────┘
```

## 配置说明

### 主配置与本地配置约定

| 文件 | 职责 | 是否提交 |
|------|------|----------|
| `application.yml` | 声明全部配置 **key**（结构模板），可含非敏感默认值 | 提交 |
| `application-local.yml` | 填写本机 / 环境相关 **value**（数据源、密码、中间件地址等） | **不提交**（已加入 `.gitignore`） |

约定：

1. **有新配置时，key 必须先写进 `application.yml`**，保证仓库内配置结构完整、其他人可知晓有哪些项。
2. **敏感或环境相关的 value 写在 `application-local.yml`**，本地保存并覆盖主配置；该文件不要提交。
3. 本地启动使用 `spring.profiles.active=local`（主配置中已默认开启），加载 `application-local.yml`。
4. Spring Boot 强类型属性（如 `spring.servlet.multipart.max-file-size`）不要在主配置中留空，应在 local 中给出合法值，或删除该 key 使用默认值。

首次本地开发：可将已有同事的 `application-local.yml` 拷贝到 `src/main/resources/`，或按 `application.yml` 中的 key 自行补全 value。

### 主要配置项

| 配置项 | 说明 |
|--------|------|
| `server.port` | 服务端口，默认 37002 |
| `server.servlet.context-path` | 上下文路径，`/ontology` |
| `spring.datasource.main` | 主业务数据库连接（PostgreSQL） |
| `spring.datasource.datalake` | 数据湖数据库连接（PostgreSQL） |
| `spring.datasource.arangodb` | ArangoDB 图数据库连接 |
| `spring.rabbitmq` | RabbitMQ 消息队列连接 |
| `rabbitmq.exchange/queue/routing-key` | 实体 Schema 变更消息配置 |
| `minio` | MinIO 对象存储配置 |
| `orekit.data.path` | Orekit 空间动力学数据路径 |
| `xxl.job` | XXL-Job 调度中心配置 |
| `ontology.swagger.enable` | Swagger 文档开关 |

## 构建与运行

### 环境要求
- JDK 1.8+
- Maven 3.6+
- PostgreSQL 12+
- ArangoDB 3.x
- RabbitMQ 3.x
- MinIO

### 构建命令

```bash
# 在 foundry 根目录下编译整个项目
mvn clean package -DskipTests

# 仅编译 ontology-server 模块
mvn clean package -pl ontology/ontology-server -am -DskipTests
```

### 运行

```bash
java -jar ontology/ontology-server/target/ontology.jar
```

### API 文档

启动后访问 Swagger 文档：
```
http://localhost:37002/ontology/doc.html
```

## 依赖模块

- **common**：公共工具类、基础配置、统一返回结果
- **license-generator**：许可证生成模块

## 主要 API 概览

| 路径 | 方法 | 说明 |
|------|------|------|
| `/meta` | POST | 创建本体 |
| `/meta` | GET | 查询本体列表 |
| `/meta/{id}` | DELETE | 删除本体 |
| `/meta/import` | POST | 批量导入本体 |
| `/space` | POST | 创建空间 |
| `/property` | POST | 创建属性 |
| `/link` | POST | 创建链接关系 |
| `/entity` | POST | 创建实体 |
| `/action` | POST | 创建行为 |
| `/function` | POST | 创建函数 |
| `/datasource` | POST | 管理数据源 |
| `/overview/count` | GET | 概览统计 |