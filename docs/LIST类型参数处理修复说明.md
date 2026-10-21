# LIST 类型参数处理修复说明

## 问题描述

当外部函数（EXTERNAL）或自定义函数（CUSTOMIZE）的输入参数为 **LIST 类型**且包含嵌套对象子字段时，后端在测试和执行时错误地将数组结构组装成对象结构。

### 问题示例

假设定义了一个 LIST 类型参数 `items`，包含多个对象：

```json
{
  "paramName": "items",
  "paramType": "LIST",
  "fields": [
    {
      "paramName": "item1",
      "paramValue": {"name": "Product A", "price": 100}
    },
    {
      "paramName": "item2", 
      "paramValue": {"name": "Product B", "price": 200}
    }
  ]
}
```

**修复前**：后端组装成 `Map<String, Object>`（错误）
```json
{
  "items": {
    "item1": {"name": "Product A", "price": 100},
    "item2": {"name": "Product B", "price": 200}
  }
}
```

**修复后**：后端组装成 `List<Object>`（正确）
```json
{
  "items": [
    {"name": "Product A", "price": 100},
    {"name": "Product B", "price": 200}
  ]
}
```

## 修复方案

### 1. 修改 `FunctionParameter` 请求 DTO

**文件**：`ontology/ontology-server/src/main/java/com/aircas/ptr/foundry/ontology/model/param/FunctionParameter.java`

**改动**：新增 `paramType` 字段，使前端可以传递参数类型信息

```java
@Schema(name = "paramType", description = "参数类型（LIST 类型时后端据此把 fields 组装为数组而非对象）")
private FunctionParamTypeEnum paramType;
```

### 2. 修改 `resolveParamValue()` 方法

**文件**：`ontology/ontology-server/src/main/java/com/aircas/ptr/foundry/ontology/service/impl/FunctionServiceImpl.java`

**位置**：第 1264-1295 行

**改动**：根据 `paramType` 判断是 LIST 还是 OBJECT 类型，分别组装成数组或对象

```java
static Object resolveParamValue(FunctionParameter p) {
    if (p.getParamValue() != null) {
        return p.getParamValue();
    }
    List<FunctionParameter> fields = p.getFields();
    if (CollectionUtils.isEmpty(fields)) {
        return null;
    }
    // LIST 类型：把 fields 组装为数组
    if (p.getParamType() == FunctionParamTypeEnum.LIST) {
        List<Object> list = new ArrayList<>();
        for (FunctionParameter f : fields) {
            Object v = resolveParamValue(f);
            if (v != null) {
                list.add(v);
            }
        }
        return list.isEmpty() ? null : list;
    }
    // OBJECT 类型或其他类型：把 fields 组装为对象（默认行为）
    Map<String, Object> nested = new LinkedHashMap<>();
    for (FunctionParameter f : fields) {
        Object v = resolveParamValue(f);
        if (v != null) {
            nested.put(f.getParamName(), v);
        }
    }
    return nested.isEmpty() ? null : nested;
}
```

## 全场景覆盖验证

### ✓ 场景 1：落库（创建/更新函数）

**流程**：
1. 前端创建函数时，`FunctionParamDTO.paramType` 携带类型信息
2. `saveParams()` 方法（第 1192-1225 行）将 `paramType` 保存到 `FunctionParamPO`
3. `fields` 序列化为 JSON 存储到数据库

**代码位置**：
- `FunctionServiceImpl.java:1213` - 保存 paramType
- `FunctionServiceImpl.java:1205` - 序列化 fields

**状态**：✅ 已支持（原本就支持，无需修改）

---

### ✓ 场景 2：详情查看

**流程**：
1. 调用 `/function/detail` 接口
2. `getFunctionDetailByApi()` 方法（第 195-274 行）从数据库读取 `FunctionParamPO`
3. 返回 `FunctionParameterVO`，包含 `paramType` 和反序列化的 `fields`

**代码位置**：
- `FunctionServiceImpl.java:207` - 返回 paramType
- `FunctionServiceImpl.java:212-214` - 反序列化 fields

**状态**：✅ 已支持（原本就支持，无需修改）

---

### ✓ 场景 3：测试函数（草稿测试）

**流程**：
1. 前端调用 `/function/test` 接口，传递 `FunctionTestParam`
2. `testDraftFunction()` 方法（第 1350-1400 行）调用 `resolveParamValue()`
3. **修复后**：`FunctionParameter` 携带 `paramType`，`resolveParamValue()` 根据类型正确组装

**代码位置**：
- `FunctionServiceImpl.java:1356` - 调用 resolveParamValue
- `FunctionParameter.java:24` - 新增 paramType 字段
- `FunctionServiceImpl.java:1277-1285` - LIST 类型处理逻辑

**状态**：✅ 已修复

**测试示例**：
```bash
POST /function/test
{
  "type": "EXTERNAL",
  "apiConfig": {...},
  "parameters": [
    {
      "paramName": "items",
      "paramType": "LIST",
      "fields": [
        {"paramName": "item1", "paramValue": {"name": "A"}},
        {"paramName": "item2", "paramValue": {"name": "B"}}
      ]
    }
  ]
}
```

---

### ✓ 场景 4：执行函数

**流程**：
1. 前端调用 `/function/execute` 接口，传递 `FunctionExecuteParam`
2. `executeFunction()` 方法（第 750-800 行）调用 `resolveParamValue()`
3. **修复后**：同测试场景，根据 `paramType` 正确组装

**代码位置**：
- `FunctionServiceImpl.java:762` - 调用 resolveParamValue
- 修复点同场景 3

**状态**：✅ 已修复

**测试示例**：
```bash
POST /function/execute
{
  "functionApi": "test_api",
  "version": "1.0.0",
  "parameters": [
    {
      "paramName": "items",
      "paramType": "LIST",
      "fields": [...]
    }
  ]
}
```

---

## 前端适配说明

### 必须修改的地方

前端在**测试**和**执行**函数时，需要在 `FunctionParameter` 中传递 `paramType` 字段：

```typescript
interface FunctionParameter {
  paramName: string;
  paramType?: FunctionParamTypeEnum; // 新增：LIST | OBJECT | STRING | NUMBER 等
  paramValue?: any;
  fields?: FunctionParameter[];
}
```

### 示例代码

```typescript
// 测试函数时
const testParams: FunctionParameter[] = [
  {
    paramName: 'items',
    paramType: 'LIST', // 必须传递
    fields: [
      {
        paramName: 'item1',
        paramValue: { name: 'Product A', price: 100 }
      },
      {
        paramName: 'item2',
        paramValue: { name: 'Product B', price: 200 }
      }
    ]
  }
];

// 执行函数时
const executeParams: FunctionParameter[] = [
  {
    paramName: 'users',
    paramType: 'LIST', // 必须传递
    fields: [
      {
        paramName: 'user1',
        paramValue: { id: 1, name: 'Alice' }
      },
      {
        paramName: 'user2',
        paramValue: { id: 2, name: 'Bob' }
      }
    ]
  }
];
```

### 不需要修改的地方

- **创建/更新函数**：`FunctionParamDTO` 已经有 `paramType` 字段，无需修改
- **查看详情**：`FunctionParameterVO` 已经返回 `paramType` 字段，无需修改

---

## 向后兼容性

### 兼容性分析

1. **paramType 字段可选**：
   - 如果前端不传 `paramType`，`resolveParamValue()` 会走默认逻辑（组装成 Map）
   - 这与修复前的行为一致，不会破坏现有功能

2. **已有函数不受影响**：
   - 数据库中已保存的 `paramType` 信息仍然有效
   - 详情接口仍然正确返回类型信息

3. **标量参数不受影响**：
   - STRING、NUMBER、BOOLEAN 等标量类型直接使用 `paramValue`，不经过 fields 组装

### 建议

虽然向后兼容，但建议前端尽快适配，在测试和执行时传递 `paramType`，以启用正确的 LIST 类型处理。

---

## 测试验证

### 测试用例 1：LIST 类型参数（嵌套对象）

**函数定义**：
```json
{
  "functionApi": "test_list_param",
  "type": "CUSTOMIZE",
  "code": "def handle(input):\n    return input['items']",
  "parameters": [
    {
      "paramName": "items",
      "paramType": "LIST",
      "category": "INPUT"
    }
  ]
}
```

**测试请求**：
```json
{
  "type": "CUSTOMIZE",
  "code": "def handle(input):\n    return input['items']",
  "language": "PYTHON",
  "parameters": [
    {
      "paramName": "items",
      "paramType": "LIST",
      "fields": [
        {
          "paramName": "item1",
          "paramValue": {"name": "A", "value": 10}
        },
        {
          "paramName": "item2",
          "paramValue": {"name": "B", "value": 20}
        }
      ]
    }
  ]
}
```

**期望结果**：
```json
[
  {"name": "A", "value": 10},
  {"name": "B", "value": 20}
]
```

### 测试用例 2：OBJECT 类型参数（嵌套对象）

**测试请求**：
```json
{
  "parameters": [
    {
      "paramName": "user",
      "paramType": "OBJECT",
      "fields": [
        {
          "paramName": "name",
          "paramValue": "Alice"
        },
        {
          "paramName": "age",
          "paramValue": 30
        }
      ]
    }
  ]
}
```

**期望结果**：
```json
{
  "user": {
    "name": "Alice",
    "age": 30
  }
}
```

### 测试用例 3：向后兼容（不传 paramType）

**测试请求**：
```json
{
  "parameters": [
    {
      "paramName": "data",
      "fields": [
        {"paramName": "key1", "paramValue": "value1"},
        {"paramName": "key2", "paramValue": "value2"}
      ]
    }
  ]
}
```

**期望结果**（走默认逻辑，组装成 Map）：
```json
{
  "data": {
    "key1": "value1",
    "key2": "value2"
  }
}
```

---

## 修改文件清单

1. ✅ `ontology/ontology-server/src/main/java/com/aircas/ptr/foundry/ontology/model/param/FunctionParameter.java`
   - 新增 `paramType` 字段

2. ✅ `ontology/ontology-server/src/main/java/com/aircas/ptr/foundry/ontology/service/impl/FunctionServiceImpl.java`
   - 修改 `resolveParamValue()` 方法，支持 LIST 类型处理

---

## 编译验证

```bash
mvn clean compile -pl ontology/ontology-server -am -DskipTests
```

**结果**：✅ BUILD SUCCESS

---

## 后续工作

1. **前端适配**：在测试和执行接口调用时，传递 `paramType` 字段
2. **集成测试**：验证 LIST 类型参数在真实场景下的表现
3. **文档更新**：更新 API 文档，说明 `paramType` 字段的用途

---

## 总结

本次修复通过以下两个关键改动，解决了 LIST 类型参数的处理问题：

1. **请求 DTO 增加类型信息**：`FunctionParameter` 新增 `paramType` 字段
2. **解析逻辑类型感知**：`resolveParamValue()` 根据类型选择数组或对象组装方式

修复覆盖了**落库、详情、测试、执行**四个核心场景，确保 LIST 类型参数在整个生命周期中都能被正确处理。同时保持了向后兼容性，现有功能不受影响。
