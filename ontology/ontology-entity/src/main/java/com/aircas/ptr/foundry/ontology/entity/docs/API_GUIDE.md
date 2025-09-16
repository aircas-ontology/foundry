## 注意事项

1. 所有涉及ID的操作，需要使用实际的节点或关系ID
2. Excel导入时需要按照模板格式填写数据
3. PostgreSQL表名和字段名建议使用小写字母和下划线
4. 批量导入时，关系的节点名称必须与已导入的节点名称匹配

## 错误处理
- 404: 请求的资源不存在
- 400: 请求参数错误
- 500: 服务器内部错误

## 环境要求
- ArangoDB 3.x
- PostgreSQL 12+
- Java 8+
- Spring Boot 2.7.0

### 表结构查询返回

```json
{
    "tableName": "表名",
    "columns": [
        {
            "column_name": "字段名",
            "data_type": "数据类型",
            "is_nullable": "是否可空",
            "column_default": "默认值",
            "column_comment": "字段注释"
        }
    ],
    "primaryKeys": ["主键字段名列表"]
}
```

### 图遍历返回结构

```json
{
    "node": {
        // 节点信息
    },
    "edge": {
        // 关系信息
    },
    "path": {
        // 路径信息
    }
}
```

### 关系对象结构

```json
{
    "_id": "关系ID",
    "_from": "起始节点ID",
    "_to": "目标节点ID",
    "_key": "关系Key",
    "type": "关系类型",
    "description": "关系描述",
    "weight": 0.8,
    "properties": {
        "prop1": "值1",
        "prop2": "值2"
    },
    "createTime": "2024-01-01T00:00:00Z",
    "updateTime": "2024-01-01T00:00:00Z",
    "source": "数据来源",
    "confidence": 0.9,
    "status": "关系状态"
}
```

## 返回值说明

### 节点对象结构

```json
{
    "id": "节点ID",
    "key": "节点Key",
    "name": "节点名称",
    "description": "节点描述",
    "type": "节点类型",
    "category": "节点分类",
    "properties": {
        "customField1": "自定义字段1",
        "customField2": "自定义字段2"
    },
    "source": "数据来源",
    "status": "节点状态",
    "createTime": "2024-01-01T00:00:00Z",
    "updateTime": "2024-01-01T00:00:00Z",
    "createBy": "创建人",
    "updateBy": "更新人",
    "version": 1,
    "remarks": "备注信息",
    "isDeleted": false
}
```

### 高级搜索示例

```bash
curl -X POST http://localhost:8080/api/ontology/nodes/search \
-H "Content-Type: application/json" \
-d '{
    "name": "人工智能",
    "type": "技术",
    "category": "计算机科学",
    "status": "active",
    "createTimeStart": "2024-01-01T00:00:00Z",
    "createTimeEnd": "2024-12-31T23:59:59Z",
    "isDeleted": false
}'
```

### 5.5 检查表是否存在

```bash
curl http://localhost:8080/api/postgres/tables/{tableName}/exists
```

### 5.4 删除表

```bash
curl -X DELETE http://localhost:8080/api/postgres/tables/{tableName}
```

### 5.3 修改表结构

```bash
curl -X PUT http://localhost:8080/api/postgres/tables/{tableName} \
-H "Content-Type: application/json" \
-d '{
    "addColumns": [
        {
            "fieldName": "new_column",
            "fieldType": "VARCHAR(100)",
            "fieldComment": "新增列",
            "isNullable": true
        }
    ],
    "dropColumns": ["old_column"],
    "modifyColumns": [
        {
            "fieldName": "existing_column",
            "fieldType": "TEXT",
            "fieldComment": "修改后的注释",
            "isNullable": false
        }
    ]
}'
```

### 5.2 查询表结构

```bash
curl http://localhost:8080/api/postgres/tables/{tableName}
```

## 5. PostgreSQL表管理接口

### 5.1 创建表

```bash
curl -X POST http://localhost:8080/api/postgres/tables \
-H "Content-Type: application/json" \
-d '{
    "tableName": "example_table",
    "tableComment": "示例表",
    "fields": [
        {
            "fieldName": "id",
            "fieldType": "SERIAL",
            "fieldComment": "主键ID",
            "isPrimaryKey": true,
            "isNullable": false
        },
        {
            "fieldName": "name",
            "fieldType": "VARCHAR(100)",
            "fieldComment": "名称",
            "isPrimaryKey": false,
            "isNullable": false
        }
    ]
}'
```

### 3.3 获取节点及其关联关系

```bash
curl http://localhost:8080/api/ontology/nodes/{nodeId}/with-relations
```

### 3.2 最短路径查询

```bash
curl "http://localhost:8080/api/ontology/shortest-path?startNodeId=起始节点ID&endNodeId=目标节点ID"
```

## 3. 图遍历查询接口

### 3.1 图遍历

```bash
# 从指定节点开始遍历，可指定深度和方向
curl "http://localhost:8080/api/ontology/traverse?startNodeId=节点ID&maxDepth=3&direction=OUTBOUND"

# direction可选值：
# - OUTBOUND：向外遍历
# - INBOUND：向内遍历
# - ANY：双向遍历
```

### 2.3 删除关系

```bash
curl -X DELETE http://localhost:8080/api/ontology/relations/{relationId}
```

### 2.2 获取关系

```bash
# 通过ID获取关系
curl http://localhost:8080/api/ontology/relations/{relationId}

# 获取从特定节点出发的关系
curl http://localhost:8080/api/ontology/relations/from/{fromNodeId}

# 获取指向特定节点的关系
curl http://localhost:8080/api/ontology/relations/to/{toNodeId}

# 获取特定类型的关系
curl http://localhost:8080/api/ontology/relations/type/包含
```

## 2. 关系管理接口

### 2.1 创建关系

```bash
curl -X POST http://localhost:8080/api/ontology/relations \
-H "Content-Type: application/json" \
-d '{
    "_from": "节点1的ID",
    "_to": "节点2的ID",
    "type": "包含",
    "description": "表示包含关系",
    "weight": 0.8,
    "properties": {
        "prop1": "值1",
        "prop2": "值2"
    },
    "source": "数据来源",
    "confidence": 0.9,
    "status": "active"
}'
```

### 1.3 删除节点

```bash
curl -X DELETE http://localhost:8080/api/ontology/nodes/{nodeId}
```

### 1.2 获取节点

```bash
# 通过ID获取节点
curl http://localhost:8080/api/ontology/nodes/{nodeId}

# 通过名称获取节点
curl http://localhost:8080/api/ontology/nodes/name/人工智能

# 获取所有节点
curl http://localhost:8080/api/ontology/nodes
```

# 本体关系管理系统 API 使用指南

## 1. 节点管理接口

### 1.1 创建节点
```bash
curl -X POST http://localhost:8080/api/ontology/nodes \
-H "Content-Type: application/json" \
-d '{
    "name": "人工智能",
    "description": "AI技术研究领域",
    "type": "技术",
    "category": "计算机科学",
    "properties": {
        "field1": "值1",
        "field2": "值2"
    },
    "source": "数据来源",
    "status": "active",
    "remarks": "备注信息"
}'
```

# 精确匹配示例
```bash
curl -X POST http://localhost:8080/api/postgres/tables/example_table/query \
-H "Content-Type: application/json" \
-d '{
    "conditions": [
        {
            "fieldName": "name",
            "value": "测试",
            "exactMatch": true,
            "queryType": "EQUAL"
        }
    ]
}'
```

# 模糊匹配示例
```bash
curl -X POST http://localhost:8080/api/postgres/tables/example_table/query \
-H "Content-Type: application/json" \
-d '{
    "conditions": [
        {
            "fieldName": "name",
            "value": "测试",
            "exactMatch": false,
            "queryType": "LIKE"
        }
    ]
}'
```

# 范围查询示例
```bash
curl -X POST http://localhost:8080/api/postgres/tables/example_table/query \
-H "Content-Type: application/json" \
-d '{
    "conditions": [
        {
            "fieldName": "create_time",
            "value": "2023-01-01",
            "endValue": "2023-12-31",
            "queryType": "RANGE"
        }
    ]
}'
```

# 多条件组合查询
```bash
curl -X POST http://localhost:8080/api/postgres/tables/example_table/query \
-H "Content-Type: application/json" \
-d '{
    "conditions": [
        {
            "fieldName": "name",
            "value": "测试",
            "exactMatch": false,
            "queryType": "LIKE"
        },
        {
            "fieldName": "age",
            "value": 18,
            "endValue": 30,
            "queryType": "RANGE"
        }
    ]
}'
```

# 5. 关系管理接口

## 5.1 创建关系


POST /api/satellite/visibility-window
参数：
line1=1 25544U 98067A   21086.02864292  .00001231  00000-0  30704-4 0  9993
line2=2 25544  51.6435 114.7748 0002060 264.0521 209.8348 15.48910485277246
latitude=40.7128
longitude=-74.0060
altitude=0.0
durationHours=24

