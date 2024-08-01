package com.aircas.ptr.foundry.model.po;

import java.io.Serializable;
import java.util.Date;

import lombok.Builder;
import lombok.Data;

/**
 * ontology_meta
 * @author 
 */
@Data
public class OntologyMeta implements Serializable {
    /**
     * 主键自增
     */
    private Long id;

    /**
     * 唯一标识
     */
    private String uniqueIdentifier;

    /**
     * 软删除状态位，1有效，0无效
     */
    private Integer status;

    /**
     * 记录创建时间
     */
    private Date createTime;

    /**
     * 记录修改时间
     */
    private Date updateTime;

    /**
     * 图标
     */
    private String icon;

    /**
     * 本体名称
     */
    private String displayName;

    /**
     * 別名
     * @Auther:liuyang
     */
    private String nickname;

    /**
     * 本体复数名称
     */
    private String pluralDisplayName;

    /**
     * 本体对应的数据源id，这里的数据源由上层应用指导、治理好的表的访问方式
     */
    private String backingDatasourceId;

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

    private static final long serialVersionUID = 1L;

    /**
     * 分组ids，以“,”隔开
     */
    private String metaGroupId;
}