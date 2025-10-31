package com.aircas.ptr.foundry.ontology.model.vo;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(description = "行为调度执行结果")
public class ActionResultVO {


    @ApiModelProperty(name = "entityPrimaryKey",value = "实体主键id")
    private String entityPrimaryKey;

    @ApiModelProperty(name = "data",value = "执行结果数据")
    private String data;

}
