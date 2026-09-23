package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.ActionCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionCategoryDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionCategoryUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.ActionCategory;
import com.aircas.ptr.foundry.ontology.model.vo.ActionCategoryVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 行为分类体系树服务。
 */
public interface ActionCategoryService extends IService<ActionCategory> {

    /**
     * 创建行为分类体系树（支持一次性提交子树）。
     */
    void createActionCategory(ActionCategoryCreateParam param);

    /**
     * 修改行为分类名称，并级联重写整棵子树的 path。
     */
    void updateActionCategory(ActionCategoryUpdateParam param);

    /**
     * 删除行为分类节点及其整棵子树；节点下存在行为时拒绝删除。
     */
    void deleteActionCategory(ActionCategoryDeleteParam param);

    /**
     * 查询本空间的行为分类体系树，叶子上挂载该分类下的行为。
     */
    ActionCategoryVO getActionCategoryTree(Integer spaceId);
}
