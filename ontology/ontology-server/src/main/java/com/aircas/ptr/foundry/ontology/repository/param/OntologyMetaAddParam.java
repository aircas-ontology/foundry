package com.aircas.ptr.foundry.ontology.repository.param;

import com.aircas.ptr.foundry.common.constant.OntologyComponentEnum;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyMetaBO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

import static com.aircas.ptr.foundry.common.constant.OntologyComponentEnum.*;

@Getter
@Setter
@AllArgsConstructor
@ApiModel(description = "本体新增参数")
public class OntologyMetaAddParam {

    @ApiModelProperty(name = "icon", value = "本体图标", dataType = "java.lang.String", example = "飞机图标base64")
    private String icon;

    @ApiModelProperty(name = "displayName", value = "本体名称", dataType = "java.lang.String", example = "飞机", required = true)
    private String displayName;

    @ApiModelProperty(name = "pluralDisplayName", value = "本体名称复数", dataType = "java.lang.String", example = "飞机群")
    private String pluralDisplayName;

    @ApiModelProperty(name = "backingDatasourceId", value = "本体对应的数据源id", dataType = "java.lang.String", example = "123456789")
    private String backingDatasourceId;

    @ApiModelProperty(name = "description", value = "本体描述", dataType = "java.lang.String", example = "这是一架我方战斗机")
    private String description;

    @ApiModelProperty(name = "apiName", value = "在代码里用的本体名称", dataType = "java.lang.String", example = "airplane", required = true)
    private String apiName;

    @ApiModelProperty(name = "visibility", value = "可见性，1正常、2隐藏、3突出显示", dataType = "java.lang.Integer", example = "")
    private Integer visibility;

    @ApiModelProperty(name = "experimentalStatus", value = "实验状态，1激活、2测试中、3废弃", dataType = "java.lang.Integer", example = "")
    private Integer experimentalStatus;

    @ApiModelProperty(name = "indexStatus", value = "本体及数据源的索引状态，1成功、2失败、3未开始", dataType = "java.lang.Integer", example = "")
    private Integer indexStatus;

    @ApiModelProperty(name = "writebackFlag", value = "本体修改是否可写回数据源，1是，0否", dataType = "java.lang.Integer", example = "")
    private Integer writebackFlag;

    @ApiModelProperty(name = "nickname", value = "本体别名", dataType = "java.lang.String", example = "威龙")
    private String nickname;

    @ApiModelProperty(name = "isMapAllParam", value = "是否映射datasource的列为属性，在backingDatasourceId存在时生效", example = "true")
    private Boolean isMapAllParam;

    @ApiModelProperty(name = "titleKey", value = "标题列名，在backingDatasourceId存在时生效", example = "name")
    private String titleKey;

    @ApiModelProperty(name = "primaryKey", value = "主键列名，在backingDatasourceId存在时生效", example = "id")
    private String primaryKey;

    @ApiModelProperty(name = "metaGroupId", value = "分组列表，以”,“分割", example = "fdfsa,feaf")
    private String metaGroupId;

    @ApiModelProperty(value = "继承的本体id", example = "fdsafdsagf")
    private String parentUniqueIdentifier;

    @ApiModelProperty(value = "继承的父本体，都继承的组件列表", example = "[\"FUNCTION\",\"PROPERTY\"]")
    private List<OntologyComponentEnum> parentComponents;
}
