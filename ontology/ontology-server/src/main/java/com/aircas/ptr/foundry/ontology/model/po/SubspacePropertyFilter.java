package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.common.util.DebeziumDateReader;
import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * 子空间属性筛选条件配置。
 *
 * <p>记录用户在创建子空间时为每个选中属性配置的筛选条件，供后续实例过滤或查询复用。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName("ontology_subspace_property_filter")
public class SubspacePropertyFilter implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 子空间 id（ontology_space.id）
     */
    private Integer spaceId;

    /**
     * 子空间中新本体的唯一标识
     */
    private String ontologyUniqueIdentifier;

    /**
     * 子空间中新属性的唯一标识
     */
    private String propertyUniqueIdentifier;

    /**
     * 源空间中被复制属性的唯一标识（冗余，便于追溯）
     */
    private String sourcePropertyUniqueIdentifier;

    /**
     * 筛选操作符，取值见 {@link com.aircas.ptr.foundry.ontology.model.enums.QueryOpEnum}
     */
    private String filterOp;

    /**
     * 筛选值：单值 op（EQ/LIKE/GT 等）直接存原始值（如 驱逐舰、10）；多值 op（IN/BETWEEN）以 JSON 数组存储（如 ["福特","通用"]、[10,20]）；读取时按 filter_op 区分，值类型由 data_type 决定
     */
    private String filterValue;

    /**
     * 属性数据类型，取值见 {@link com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum}
     */
    private String dataType;

    /**
     * 软删除状态位，1 有效，0 无效
     */
    private Integer status;

    /**
     * 记录创建时间
     */
    @JSONField(deserializeUsing = DebeziumDateReader.class)
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 记录修改时间
     */
    @JSONField(deserializeUsing = DebeziumDateReader.class)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
