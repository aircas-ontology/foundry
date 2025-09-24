package com.aircas.ptr.foundry.ontology.entity.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "实体记录创建请求")
public class EntityRecordParam {

    @ApiModelProperty(name = "tableName", value = "表名", required = true, example = "xtmb")
    @NotBlank(message = "tableName is empty")
    private String tableName;

    @ApiModelProperty(name = "columns", value = "列参数", required = true)
    @NotEmpty(message = "columns is empty")
    private List<EntityColumnParam> columns;
}
