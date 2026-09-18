package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.dto.WizardObjectSpecDTO;
import com.aircas.ptr.foundry.ontology.model.dto.WizardPropertyDTO;
import com.aircas.ptr.foundry.ontology.model.param.WizardBuildObjectParam;
import com.aircas.ptr.foundry.ontology.model.param.WizardBuildPropertiesParam;
import com.aircas.ptr.foundry.ontology.model.param.WizardBuildRelationsParam;
import com.aircas.ptr.foundry.ontology.model.param.WizardFinalizeParam;
import com.aircas.ptr.foundry.ontology.model.vo.WizardFinalizeResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.WizardRelationVO;

import java.util.List;

/**
 * 本体构建向导服务
 * <p>
 * 5 步向导流程：
 * <ol>
 *   <li>前端：定义本体对象（输入描述）</li>
 *   <li>前端：导入资料（选择数据源）</li>
 *   <li>后端：{@link #buildObject} 构建对象规格</li>
 *   <li>后端：{@link #buildProperties} 构建属性列表</li>
 *   <li>后端：{@link #buildRelations} 构建关系列表</li>
 *   <li>后端：{@link #persist} 落库（前端点击"完成"后触发）</li>
 * </ol>
 * 每一步 LLM 输出都会带 reasoning（构建依据），前端只读展示但会回传给下一步作为上下文。
 */
public interface OntologyWizardService {

    /**
     * 步骤 3：构建本体对象规格
     */
    WizardObjectSpecDTO buildObject(WizardBuildObjectParam param);

    /**
     * 步骤 4：构建本体属性列表
     */
    List<WizardPropertyDTO> buildProperties(WizardBuildPropertiesParam param);

    /**
     * 步骤 5：构建本体关系列表（新对象总是 source，同空间已有对象是 target）
     */
    List<WizardRelationVO> buildRelations(WizardBuildRelationsParam param);

    /**
     * 落库：将步骤 3/4/5 的产出一次性持久化
     * <p>
     * 依次创建：
     * <ol>
     *   <li>本体对象（ontology_meta）</li>
     *   <li>本体属性（ontology_property）</li>
     *   <li>本体关系（ontology_link_group），关系分类归属到空间下的"默认分类"，不存在则自动创建</li>
     * </ol>
     * 整个方法在 chainedTransactionManager 事务中执行，任一步失败全部回滚。
     *
     * @param param 落库参数（spaceId + objectSpec + properties + relations）
     * @return 落库结果（含新本体 uniqueIdentifier、属性/关系创建数量）
     */
    WizardFinalizeResultVO persist(WizardFinalizeParam param);
}
