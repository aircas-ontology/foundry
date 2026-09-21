package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName("ontology_link_group")
public class OntologyLinkGroup implements Serializable {
    /**
     * 主键自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * link的unique identifier
     */
    private String uniqueIdentifier;

    /**
     * link的名称
     */
    private String name;

    /**
     * 软删除状态位，1有效，0无效
     */
    private Integer status;

    /**
     * 记录创建时间
     */
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 记录修改时间
     */
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * 开始本体unique identifier
     */
    private String ontologyUniqueIdentifierFrom;

    /**
     * 结束本体unique identifier
     */
    private String ontologyUniqueIdentifierTo;

    /**
     * 关系类型
     */
    private OntologyLinkTypeEnum type;

    /**
     * 本体空间id
     */
    private Integer ontologySpaceId;

    /**
     * 关系分类id（ontology_link_category.id），关系可归属到关系分类树
     */
    private Integer categoryId;

}
