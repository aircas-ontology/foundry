package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.OntologyLinkMappingEnum;
import com.aircas.ptr.foundry.ontology.controller.validator.GroupIdsVerify;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyApiNameVerify;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyDisplayNameVerify;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

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
    @OntologyDisplayNameVerify
    private String displayName;

    @ApiModelProperty(name = "primaryDataSource", value = "本体对应主数据源", dataType = "OntologyPrimaryDataSourceParam")
    //todo  数据源参数有效性校验
    @Valid
    private OntologyDatasourceParam primaryDataSource;

    @ApiModelProperty(name = "associateDataSources", value = "本体关联的其他数据源", dataType = "OntologyAssociateDataSourceParam")
    //todo  数据源参数有效性校验
    @Valid
    private List<OntologyDatasourceParam> associateDataSources;

    @ApiModelProperty(name = "description", value = "本体描述", dataType = "java.lang.String", example = "这是一架我方战斗机")
    private String description;

    @ApiModelProperty(name = "apiName", value = "在代码里用的本体名称", dataType = "java.lang.String", example = "airplane", required = true)
    @NotBlank(message = "apiName is empty")
    @OntologyApiNameVerify
    private String apiName;

    @ApiModelProperty(name = "groupIds", value = "分组ids", example = "[123,456]", required = true)
    @NotEmpty(message = "groupIds is empty")
    @GroupIdsVerify
    private Set<String> groupIds;

    @ApiModelProperty(name = "parentOntologyUniqueIdentifier", value = "继承的本体id", example = "8039c5f9-5579-4ee4-ba94-b2f25f785dd6")
    private String parentOntologyUniqueIdentifier;

    @ApiModelProperty(name = "linkCreateParam", value = "创建关系")
    @Valid
    private LinkCreateParam linkCreateParam;


    @Data
    @SuperBuilder
    @Accessors(chain = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @ApiModel(description = "link create param")
    public static class LinkCreateParam {

        /**
         * link的名称
         */
        @ApiModelProperty(name = "name", required = true, value = "本体关系名称")
        @NotBlank(message = "link name is empty")
        private String name;

        /**
         * 结束本体unique identifier
         */
        @ApiModelProperty(name = "ontologyUniqueIdentifierTo", required = true, value = "结束本体uniq id")
        @NotBlank(message = "ontologyUniqueIdentifierTo name is empty")
        @OntologyIdVerify
        private String ontologyUniqueIdentifierTo;

        /**
         * 开始本体的某个属性api name，作为连接键
         */
        @ApiModelProperty(name = "propertyApiNameFrom",  value = "开始本体的某个属性，作为连接键")
        private String propertyApiNameFrom;

        /**
         * 结束本体的某个属性，作为连接键
         */
        @ApiModelProperty(name = "propertyUniqueIdentifierTo",  value = "结束本体的某个属性，作为连接键")
        private String propertyUniqueIdentifierTo;


        /**
         * 1: 1对1
         * 2: 1对多
         * 3: 多对1
         * 4: 多对多
         */
        @ApiModelProperty(name = "mapping", required = true, value = "本体映射关系")
        @NotNull(message = "mapping is empty")
        private OntologyLinkMappingEnum mapping;

    }

}
