package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为测试请求")
public class ActionTestParam {

    @Schema(name = "ontologySpaceId", description = "对象空间id", example = "156", required = true)
    @NotNull(message = "ontologySpaceId is null")
    private Integer ontologySpaceId;

    @Schema(name = "actionApi", description = "行为api", example = "syncSpeed", required = true)
    @NotBlank(message = "actionApi is empty")
    private String actionApi;

    @Schema(name = "entityPrimaryKey", description = "行为所属对象的实例主键；只涉及一个对象时用它", example = "1")
    private Object entityPrimaryKey;

    @Schema(name = "entityPrimaryKeys", description = "各对象选中的实例主键：对象id -> 实例主键；"
            + "应用行为的入参/出参可能跨对象，映射涉及的对象都要提供；一个对象的入参与出参默认落在同一实例上，"
            + "要分开时改用 inputEntities/outputEntities；"
            + "行为绑定关系时用保留键 from、to 分别传关系两端的实例主键，两者互不混用",
            example = "{\"3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34\":1,\"a5e8c206-3f17-4d92-8b74-1c6f9d3e5028\":2}")
    private Map<String, Object> entityPrimaryKeys;

    @Schema(name = "inputEntities", description = "入参方向的数据位置：对象id -> {实例主键, 存储分组->行主键}。"
            + "只对该对象的属性/对象/List 入参生效，两个子字段可只给其一，都没给时沿用 entityPrimaryKeys/最新一行；"
            + "关系两端（from/to）与不绑算子的行为不支持按方向指定",
            example = "{\"3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34\":{\"entityPrimaryKey\":1,\"dataPrimaryKeys\":{\"weapon_load\":11}}}")
    private Map<String, ActionInstanceTargetParam> inputEntities;

    @Schema(name = "outputEntities", description = "出参方向的数据位置：对象id -> {实例主键, 存储分组->行主键}。"
            + "只对该对象的 WRITE_PROPERTY 出参生效，两个子字段都可以只给其一，都没给时沿用 entityPrimaryKeys/新增一行；"
            + "关系两端（from/to）与不绑算子的行为不支持按方向指定",
            example = "{\"a5e8c206-3f17-4d92-8b74-1c6f9d3e5028\":{\"entityPrimaryKey\":2,\"dataPrimaryKeys\":{\"duty_log\":12}}}")
    private Map<String, ActionInstanceTargetParam> outputEntities;

    @Schema(name = "dataPrimaryKey", description = "分组表行的主键：属性值-修改/删除必填", example = "1")
    private Object dataPrimaryKey;

    @Schema(name = "linkEntityPrimaryKey", description = "关系对端实体主键：兼容写法，行为测试建议在 entityPrimaryKeys 里用 from/to 传两端", example = "2")
    private Object linkEntityPrimaryKey;

    @Schema(name = "params", description = "来源为外部输入的参数取值（参数名 -> 值），固定值不在此传")
    private Map<String, Object> params;
}
