# 基础查询算子（BASIC_QUERY）测试用例（ApiPost 直接抄）

验证本轮修复：**创建/更新基础函数时，`function_param` 只落用户声明的变量（聚合目标 + 过滤条件），不再追加编排机制参数**（`ontologyIdentifier` 入参、`result/records/total` 等出参端口）。编排画布所需端口改为运行时按 `queryConfig` 动态推导，不受影响。

- **服务器 IP 请自行替换**：主应用 `172.16.18.62`（ontology-server）。
- **主应用端口/前缀**：`37002`，context-path `/ontology`。
- **鉴权头**：`Authorization: Bearer <access-token>`（token 用登录接口获取，必须带 `Bearer ` 前缀；若接口在白名单内可省略）。
- **核心断言（每个用例都要看）**：`GET /function/detail` 返回的 `params` 数组里 **绝不能出现** `paramName="ontologyIdentifier"`，也**绝不能出现** `result` / `records` / `total` / `value` / `aggFunc` / `alias` 这些出参端口。只应是你在 `queryConfig` 里写的变量名。

---

## 一、接口定义

### 1. 创建函数
- Method: `POST`
- URL: `http://172.16.18.62:37002/ontology/function`
- Header: `Content-Type: application/json`
- Body（`type` 固定 `BASIC_QUERY`，配置放 `queryConfig`）：
```json
{
  "functionApi": "bq_query_demo",
  "version": "1.0.0",
  "displayName": "基础查询-查询模式",
  "type": "BASIC_QUERY",
  "ontologySpaceId": 1,
  "queryConfig": {
    "aggFunc": null,
    "targetProperty": null,
    "filters": {
      "logic": "AND",
      "children": [
        { "type": "FILTER", "filter": { "propertyApiName": "status", "op": "EQ", "value": "1", "dataType": "STRING" } },
        { "type": "FILTER", "filter": { "propertyApiName": "length", "op": "GT", "value": 10, "dataType": "NUMBER" } }
      ]
    }
  }
}
```

### 2. 查询详情
- Method: `GET`
- URL: `http://172.16.18.62:37002/ontology/function/detail?functionApi=bq_query_demo&version=1.0.0`
- **`version` 必填**（格式 `x.y.z`）。

### `queryConfig` 字段
| 字段 | 类型 | 说明 |
|------|------|------|
| `aggFunc` | String | 聚合函数：`SUM`/`COUNT`/`AVG`/`MAX`/`MIN`/`DISTINCT`；`null` 表示查询模式（无聚合） |
| `targetProperty` | String | 聚合目标**变量名**；`aggFunc` 非空时**必填**，查询模式可省略 |
| `filters` | Object | 过滤树：`{logic:AND/OR, children:[{type:FILTER/GROUP, filter:{...}, group:{...}}]}` |

### `filter` 单条件字段
| 字段 | 说明 |
|------|------|
| `propertyApiName` | **这里放的是变量名**（执行时经 `variableBindings` 映射到实际属性） |
| `op` | `EQ,NE,LIKE,LIKE_LEFT,LIKE_RIGHT,IN,NOT_IN,BETWEEN,NOT_BETWEEN,GT,GE,LT,LE,IS_NULL,IS_NOT_NULL` |
| `value` / `values` | 单值 / 多值（`IN`/`BETWEEN` 用） |
| `dataType` | `STRING`/`NUMBER`/`BOOLEAN`，决定入库 `paramType` |

### 响应 `data.params[]`（`FunctionParameterVO`）
| 字段 | 说明 |
|------|------|
| `paramName` | 参数名（应 = 你声明的变量名） |
| `paramType` | `STRING`/`DOUBLE`/`BOOL`/`OBJECT`（由 `dataType` 映射，见用例4） |
| `category` | `INPUT`（基础函数的用户变量都是入参；**不应再有 OUTPUT**） |
| `paramOrder` | INPUT 内从 1 自增 |
| `description` | `聚合目标` / `过滤条件`（按角色区分） |
| `paramRole` | 查询模式：全部 `FILTER`；聚合模式：命中 `targetProperty` 的标 `AGGREGATION`，其余 `FILTER` |

---

## 二、用例

### 用例 1：查询模式（无聚合）→ 只落过滤变量 ★核心
- 用「一、1」的创建 Body（`aggFunc=null`，两个过滤变量 `status`、`length`）。
- 创建成功（`status=200`），再查详情。
- **期望 `params`（恰好 2 条，全是 INPUT）**：
  - `status` → `STRING`，`category=INPUT`，`description=过滤条件`，`paramRole=FILTER`
  - `length` → `DOUBLE`，`category=INPUT`，`description=过滤条件`，`paramRole=FILTER`
- **绝不能出现**：`ontologyIdentifier`、`result`、`records`、`total`（本轮修复点，旧版本会多这些）。

### 用例 2：聚合模式（COUNT + targetProperty）→ 目标 + 过滤变量，无出参端口
- Body:
```json
{
  "functionApi": "bq_agg_demo",
  "version": "1.0.0",
  "displayName": "基础查询-聚合模式",
  "type": "BASIC_QUERY",
  "ontologySpaceId": 1,
  "queryConfig": {
    "aggFunc": "COUNT",
    "targetProperty": "amount",
    "filters": {
      "logic": "AND",
      "children": [
        { "type": "FILTER", "filter": { "propertyApiName": "status", "op": "EQ", "value": "1", "dataType": "STRING" } }
      ]
    }
  }
}
```
- **期望 `params`（恰好 2 条，全是 INPUT）**：
  - `amount` → `description=聚合目标`，`paramRole=AGGREGATION`
  - `status` → `description=过滤条件`，`paramRole=FILTER`
- **绝不能出现**：`ontologyIdentifier`、以及聚合出参端口 `result`/`value`/`aggFunc`/`alias`/`targetProperty`。

### 用例 3：聚合缺 targetProperty → 400
- Body 同用例2，但 `"aggFunc":"COUNT"`, `"targetProperty": null`（或不传）。
- **期望**：创建失败，提示 `聚合函数必须提供 targetProperty（聚合目标字段）`。

### 用例 4：dataType → paramType 映射
- Body: `queryConfig.aggFunc=null`，`filters` 放三个变量分别 `dataType` 为 `STRING`/`NUMBER`/`BOOLEAN`，再各放一个不写 `dataType` 的。
- **期望 `params.paramType`**：`STRING→STRING`、`NUMBER→DOUBLE`、`BOOLEAN→BOOL`、未声明→`OBJECT`。

### 用例 5：嵌套过滤组（GROUP）也仅收变量
- Body: `filters.children` 里放一个 `type=GROUP` 的子组，子组内再放两个 `FILTER`（变量 `city`、`age`）。
- **期望**：递归收集到的每个叶子变量各一条 INPUT，无机制参数。

### 用例 6：更新（PUT）后旧脏数据被清理 ★验证先删后建
- 若某个 `functionApi` 是**修复前**创建的（详情里带着 `ontologyIdentifier`/出参端口），对它调用 `PUT http://172.16.18.62:37002/ontology/function`（body 用 `FunctionUpdateParam`：`functionApi` + `version` + 新 `queryConfig`）。
- 更新逻辑对 BASIC_QUERY 是**先删旧 `function_param` 再按新配置重建**。
- **期望**：更新后详情 `params` 变干净（只剩用户变量），旧机制参数消失。
- 说明：**存量已创建函数**若不重新编辑保存，其 `function_param` 里的旧脏数据不会被自动清（本次改动只影响新建/更新写入路径）。如需批量清干净见文末「清理 SQL」。

---

## 三、编排回归（确认端口没因这次改动丢失）

> 这次把 BASIC_QUERY 的 `ontologyIdentifier`/出参端口从「落库」改成「运行时推导」。要确认编排画布上把基础算子当节点时端口仍齐全。

- Method: `POST`
- URL: `http://172.16.18.62:37002/ontology/function/composite/validate`
- 在编排配置里引用用例2的 `bq_agg_demo`，节点连线需要用到入参 `ontologyIdentifier` 与出参 `result`/`value`/`aggFunc`/`alias`/`targetProperty`。
- **期望**：校验通过、能取到这些端口（尽管它们在详情 `params` 里看不到——它们是动态合成的）。若报「缺少本体标识：ontologyIdentifier」或端口找不到，说明回归失败。

---

## 四、清理 SQL（可选，仅清存量脏数据）

先查表核对，再删。删除范围严格限定到 BASIC_QUERY 函数 + 这些机制参数名：
```sql
-- 先看有哪些脏数据（function 表 type='BASIC_QUERY'）
SELECT fp.function_id, fp.param_name, fp.category, f.api, f.type
FROM function_param fp
JOIN function f ON f.id = fp.function_id
WHERE f.type = 'BASIC_QUERY'
  AND (
    (fp.category = 'INPUT'  AND fp.param_name = 'ontologyIdentifier')
    OR (fp.category = 'OUTPUT' AND fp.param_name IN
        ('result','records','total','value','aggFunc','alias','targetProperty'))
  );
```
> ⚠️ 删除前务必确认：`OUTPUT` 类里 `targetProperty` 只有作为机制出参时才删；若你的真实变量恰好也叫 `targetProperty`/`result` 等，请先用 `function_id` 逐个核对再删。建议优先用「用例6 重新保存」来清理，避免误删用户数据。

---

## 五、通过标准汇总
1. 用例1-2 详情 `params` **无任何机制参数**（无 `ontologyIdentifier`、无 OUTPUT 行）。
2. 用例3 正确 400。
3. 用例4-5 `paramType` 映射与递归收集正确。
4. 用例6 重新保存后脏数据被清干净。
5. 编排校验（三）端口齐全、节点能跑通 —— 证明动态推导生效、未影响其他类型函数。
