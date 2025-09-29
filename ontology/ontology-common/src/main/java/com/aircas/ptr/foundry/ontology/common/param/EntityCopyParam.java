package com.aircas.ptr.foundry.ontology.common.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(description = "实体复制请求")
public class EntityCopyParam {

    @ApiModelProperty(name = "sourceTableName", value = "源目标实体表（主表）")
    @NotBlank(message = "sourceTableName is empty")
    private String sourceTableName;

    @ApiModelProperty(name = "newTableName", value = "新的实体表（主表）")
    @NotBlank(message = "newTableName is empty")
    private String newTableName;



}
