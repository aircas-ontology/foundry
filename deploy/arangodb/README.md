##   启动命令：docker-compose up -d


# ArangoDB 初始化说明

对应配置见 `application.yml` → `spring.datasource.arangodb`，对应模型见 `EntityNode`、`EntityRelation`。

## 连接信息

| 项 | 值 |
|---|---|
| host | `127.0.0.1` |
| port | `8529` |
| user | `root` |
| password | `root` |
| database | `entity` |
| collections | `node`, `relation` |

## 1. 创建 Database

在 ArangoDB Web UI（默认 `http://127.0.0.1:8529`）或管理端创建数据库：

- **数据库名**：`entity`

也可使用 HTTP API：

```bash
curl -u root:root -H 'Content-Type: application/json' \
  -X POST http://127.0.0.1:8529/_api/database \
  -d '{"name":"entity"}'
```

## 2. 创建 Collection（Document / Edge）

切换到 `entity` 库后创建以下两个集合。

### 2.1 `node` — Document Collection

- **类型**：Document（文档集合）
- **对应模型**：`EntityNode`（`@Document("node")`）
- **用途**：存储本体实体节点

| 字段 | 类型 | 说明 |
|---|---|---|
| `_key` / `_id` / `_rev` | string | ArangoDB 系统字段 |
| `ontologyUniqIdentifier` | string | 本体唯一标识 |
| `tableName` | string | 数据源/表标识（datasourceId） |
| `primaryKey` | any | 实体主键值 |
| `displayName` | string | 展示名 |
| `createTime` | date | 创建时间 |
| `updateTime` | date | 更新时间 |

创建示例：

```bash
curl -u root:root -H 'Content-Type: application/json' \
  -X POST http://127.0.0.1:8529/_db/entity/_api/collection \
  -d '{"name":"node","type":2,"waitForSync":false}'
```

文档示例：

```json
{
  "ontologyUniqIdentifier": "satellite-ontology-001",
  "tableName": "ds_satellite",
  "primaryKey": 10001,
  "displayName": "卫星-A",
  "createTime": "2026-09-15T00:00:00.000Z",
  "updateTime": "2026-09-15T00:00:00.000Z"
}
```

### 2.2 `relation` — Edge Collection

- **类型**：Edge（边集合）
- **对应模型**：`EntityRelation`（`@Edge("relation")`）
- **用途**：存储实体之间的关系；`_from` / `_to` 指向 `node` 中的文档

| 字段 | 类型 | 说明 |
|---|---|---|
| `_key` / `_id` / `_rev` | string | ArangoDB 系统字段 |
| `_from` | string | 起点，格式 `node/<key>`，对应 `@From EntityNode` |
| `_to` | string | 终点，格式 `node/<key>`，对应 `@To EntityNode` |
| `ontologyLinkId` | string | 本体关系 id |
| `type` | enum | `COMPOSITION` / `RECONNAISSANCE` / `STRIKE` / `COORDINATION` / `OTHER` |
| `name` | string | 关系名称 |
| `status` | enum | `ENABLE` / `DISABLE` / `DELETE` |
| `startTime` | date | 可见窗口开始时间 |
| `endTime` | date | 可见窗口结束时间 |
| `timeWindows` | array | 可见窗口列表，元素为 `{ startTime, endTime }` |
| `createTime` | date | 创建时间 |
| `updateTime` | date | 更新时间 |

创建示例：

```bash
curl -u root:root -H 'Content-Type: application/json' \
  -X POST http://127.0.0.1:8529/_db/entity/_api/collection \
  -d '{"name":"relation","type":3,"waitForSync":false}'
```

边文档示例：

```json
{
  "_from": "node/10001",
  "_to": "node/10002",
  "ontologyLinkId": "link-001",
  "type": "COMPOSITION",
  "name": "归属",
  "status": "ENABLE",
  "startTime": null,
  "endTime": null,
  "timeWindows": [
    {
      "startTime": "2026-09-15T00:00:00.000Z",
      "endTime": "2026-09-22T00:00:00.000Z"
    }
  ],
  "createTime": "2026-09-15T00:00:00.000Z",
  "updateTime": "2026-09-15T00:00:00.000Z"
}
```

## 3. 索引

系统会自动创建 primary（`_key`）；`relation` 作为 Edge 集合还会自动创建 edge 索引（`_from`, `_to`）。  
以下为业务侧需手动创建的 persistent 索引。

### 3.1 `node`

| 名称 | 类型 | 字段 | Unique | Sparse | 说明 |
|---|---|---|---|---|---|
| `node_ontologyUniqIdentifier_idx` | persistent | `ontologyUniqIdentifier` | false | false | 按本体唯一标识查节点 |
| `node_primaryKey_idx` | persistent | `primaryKey` | false | false | 按实体主键查节点 |

```bash
curl -u root:root -H 'Content-Type: application/json' \
  -X POST 'http://127.0.0.1:8529/_db/entity/_api/index?collection=node' \
  -d '{"type":"persistent","fields":["ontologyUniqIdentifier"],"unique":false,"sparse":false,"name":"node_ontologyUniqIdentifier_idx"}'

curl -u root:root -H 'Content-Type: application/json' \
  -X POST 'http://127.0.0.1:8529/_db/entity/_api/index?collection=node' \
  -d '{"type":"persistent","fields":["primaryKey"],"unique":false,"sparse":false,"name":"node_primaryKey_idx"}'
```

### 3.2 `relation`

| 名称 | 类型 | 字段 | Unique | Sparse | 说明 |
|---|---|---|---|---|---|
| `relation_ontologyLinkId` | persistent | `ontologyLinkId` | false | false | 按本体关系 id 查询/删除 |
| `relation_startTime` | persistent | `startTime` | false | false | 可见窗口开始时间过滤 |
| `relation_endTime` | persistent | `endTime` | false | false | 可见窗口结束时间过滤 |

```bash
curl -u root:root -H 'Content-Type: application/json' \
  -X POST 'http://127.0.0.1:8529/_db/entity/_api/index?collection=relation' \
  -d '{"type":"persistent","fields":["ontologyLinkId"],"unique":false,"sparse":false,"name":"relation_ontologyLinkId"}'

curl -u root:root -H 'Content-Type: application/json' \
  -X POST 'http://127.0.0.1:8529/_db/entity/_api/index?collection=relation' \
  -d '{"type":"persistent","fields":["startTime"],"unique":false,"sparse":false,"name":"relation_startTime"}'

curl -u root:root -H 'Content-Type: application/json' \
  -X POST 'http://127.0.0.1:8529/_db/entity/_api/index?collection=relation' \
  -d '{"type":"persistent","fields":["endTime"],"unique":false,"sparse":false,"name":"relation_endTime"}'
```

## 4. 结构关系

```
database: entity
├── collection: node      (Document)  ← EntityNode
└── collection: relation  (Edge)      ← EntityRelation
       _from ──► node/_id
       _to   ──► node/_id
```
