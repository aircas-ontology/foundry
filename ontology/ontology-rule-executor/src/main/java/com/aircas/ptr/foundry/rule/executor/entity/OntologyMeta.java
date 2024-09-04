package com.aircas.ptr.foundry.rule.executor.entity;

import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class OntologyMeta {
    /**
     * 主键自增
     */
    @ApiModelProperty(name = "id", value = "自增id，新增时不传，更新时必传", dataType = "java.lang.Long", required = false, example = "5")
    private Long id;


    /**
     * 唯一标识
     */
    @ApiModelProperty(name = "uniqueIdentifier", value = "本体的唯一标识", dataType = "java.lang.String", required = true, example = "5")
    private String uniqueIdentifier;

    /**
     * 图标
     */
    @ApiModelProperty(name = "icon", value = "本体图标", dataType = "java.lang.String", example = "飞机图标base64")
    private String icon;

    /**
     * 本体名称
     */
    @ApiModelProperty(name = "displayName", value = "本体名称", dataType = "java.lang.String", example = "飞机")
    private String displayName;

    /**
     * 本体复数名称
     */
    @ApiModelProperty(name = "pluralDisplayName", value = "本体名称复数", dataType = "java.lang.String", example = "飞机群")
    private String pluralDisplayName;

    /**
     * 本体对应的数据源id，这里的数据源由上层应用指导、治理好的表的访问方式
     */
    @ApiModelProperty(name = "backingDatasourceId", value = "本体对应的数据源id", dataType = "java.lang.String", example = "123456789")
    private String backingDatasourceId;

    /**
     * 本体描述
     */
    @ApiModelProperty(name = "description", value = "本体描述", dataType = "java.lang.String", example = "这是一架我方战斗机")
    private String description;

    /**
     * 在代码里用的本体名称
     */
    @ApiModelProperty(name = "apiName", value = "在代码里用的本体名称", dataType = "java.lang.String", example = "airplane")
    private String apiName;

    /**
     * 可见性，1正常、2隐藏、3突出显示
     */
    @ApiModelProperty(name = "visibility", value = "可见性，1正常、2隐藏、3突出显示", dataType = "java.lang.Integer", example = "")
    private Integer visibility;

    /**
     * 实验状态，1激活、2测试中、3废弃
     */
    @ApiModelProperty(name = "experimentalStatus", value = "实验状态，1激活、2测试中、3废弃", dataType = "java.lang.Integer", example = "")
    private Integer experimentalStatus;

    /**
     * 本体及数据源的索引状态，1成功、2失败、3未开始
     */
    @ApiModelProperty(name = "indexStatus", value = "本体及数据源的索引状态，1成功、2失败、3未开始", dataType = "java.lang.Integer", example = "")
    private Integer indexStatus;

    /**
     * 本体修改是否可写回数据源，1是，0否
     */
    @ApiModelProperty(name = "writebackFlag", value = "本体修改是否可写回数据源，1是，0否", dataType = "java.lang.Integer", example = "")
    private Integer writebackFlag;

    /**
     * 本体别名
     * @Auther: liuyang
     */
    @ApiModelProperty(name = "nickname", value = "本体别名", dataType = "java.lang.String", example = "威龙")
    private String nickname;

//    /**
//     * 分组id
//     * @Auther:liuyang
//     */
//    @ApiModelProperty(name = "ontologyGroupId", value = "本体所属组别id", dataType = "java.lang.String", example = "")
//    private String ontologyGroupId;
//
//    /**
//     * 本体的属性值对
//     * @Explain：Map<String,String> 前面的字段为属性的displayname，后面的字段为description</>
//     * @Auther:liuyang
//     */
//    @ApiModelProperty(name = "PropertyDisplayNameAndDescription", value = "本体所具有的属性", dataType = "java.utils.Map", example = "")
//    private Map<String,String> PropertyDisplayNameAndDescription;
//
//    /**
//     * 本体的关系值对
//     * @Explain：Map<String,List<String> Map的key字段为序列号，List<String>字段中依次存储property_unique_identifiter_from，property_unique_identifiter_to，ontology_unique_identifiter_from和ontology_unique_identifiter_to，name五个字段
//     * @Auther:liuyang
//     */
//    @ApiModelProperty(name = "OntologyLinkGroup", value = "本体所有的关系值对", dataType = "java.utils.Map", example = "")
//    private Map<String,List<String>> OntologyLinkGroup;

    /**
     *
     */

}
