package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.OntologyComponentEnum;
import com.aircas.ptr.foundry.common.constant.OntologyCreateModeEnum;
import com.aircas.ptr.foundry.ontology.controller.validator.DisplayNameVerify;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(description = "ontology create request param")
public class OntologyCreateParam {

    @ApiModelProperty(name = "icon", value = "本体图标", dataType = "java.lang.String", example = "飞机图标base64")
    private String icon;

    @ApiModelProperty(name = "displayName", value = "本体名称", dataType = "java.lang.String", example = "飞机", required = true)
    @NotBlank(message = "displayName is empty")
    @DisplayNameVerify
    private String displayName;

    @ApiModelProperty(name = "primaryDataSource", value = "本体对应主数据源", dataType = "OntologyPrimaryDataSourceParam")
    private OntologyPrimaryDataSourceParam primaryDataSource;

    @ApiModelProperty(name = "associateDataSources", value = "本体关联的其他数据源", dataType = "OntologyAssociateDataSourceParam")
    private List<OntologyAssociateDataSourceParam> associateDataSources;

    @ApiModelProperty(name = "description", value = "本体描述", dataType = "java.lang.String", example = "这是一架我方战斗机")
    private String description;

    @ApiModelProperty(name = "apiName", value = "在代码里用的本体名称", dataType = "java.lang.String", example = "airplane", required = true)
    @NotBlank(message = "apiName is empty")
    private String apiName;

    @ApiModelProperty(name = "groupIds", value = "分组ids", example = "[123,456]", required = true)
    @NotEmpty(message = "groupIds is empty")
    private List<String> groupIds;

    @ApiModelProperty(name = "parentOntologyUniqueIdentifier", value = "继承的本体id", example = "8039c5f9-5579-4ee4-ba94-b2f25f785dd6")
    private String parentOntologyUniqueIdentifier;

    @ApiModelProperty(name = "createMode", value = "本体创建模式：DATASOURCE，INHERIT，NONE", example = "DATASOURCE", required = true)
    @NotNull(message = "createMode is null")
    private OntologyCreateModeEnum createMode;
}
