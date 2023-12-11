package com.aircas.ptr.foundry.ontology.entity.bo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/11 17:28
 */


@Data
public class OntologyMetaBO {
    /**
     * 主键自增
     */
    private Long id;

    /**
     * 图标
     */
    private Object icon;

    /**
     * 本体名称
     */
    @ApiModelProperty(name = "displayName", value = "本体名称", example = "飞机")
    private String displayName;

    /**
     * 本体复数名称
     */
    private String pluralDisplayName;

    /**
     * 本体对应的数据源id，这里的数据源由上层应用指导、治理好的表的访问方式
     */
    private Long backingDatasourceId;

    /**
     * 本体描述
     */
    private String description;

    /**
     * 在代码里用的本体名称
     */
    private String apiName;

    /**
     * 可见性，1正常、2隐藏、3突出显示
     */
    private Integer visibility;

    /**
     * 实验状态，1激活、2测试中、3废弃
     */
    private Integer experimentalStatus;

    /**
     * 本体及数据源的索引状态，1成功、2失败、3未开始
     */
    private Integer indexStatus;

    /**
     * 本体修改是否可写回数据源，1是，0否
     */
    private Integer writebackFlag;
}
