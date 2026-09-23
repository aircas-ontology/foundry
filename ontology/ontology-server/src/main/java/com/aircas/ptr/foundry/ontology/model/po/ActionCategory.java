package com.aircas.ptr.foundry.ontology.model.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 行为分类体系树节点。
 * <p>
 * 结构与 {@link OntologyCategory}、{@link PropertyCategory} 保持一致的「parent_id + path」邻接+物化路径模型：
 * parent_id 用于构建父子关系，path 保存根到当前节点的全路径（以 "/" 分隔），
 * 使子树查询可退化为一次 likeRight(path) 前缀扫描。
 * <p>
 * 作用域为「本体空间」：与 {@link OntologyCategory} 一致，同一空间共用一棵行为分类体系树，
 * 唯一约束为 (ontology_space_id, path)；行为（ontology_action）经其所属本体归属到空间。
 *
 * @author foundry
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@TableName("action_category")
public class ActionCategory {

    /**
     * 主键自增
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 名称
     */
    private String name;

    /**
     * 全路径
     */
    private String path;

    /**
     * 父节点id，根节点为 0
     */
    private Integer parentId;

    /**
     * 本体空间id
     */
    private Integer ontologySpaceId;

    /**
     * 记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 记录修改时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
