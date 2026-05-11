package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.OntologyGroupCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyGroupUpdatedParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyGroup;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupInfoVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:02
 */


public interface OntologyGroupService extends IService<OntologyGroup> {

    void createGroup(OntologyGroupCreateParam param);

    void deleteGroupById(String groupId);

    List<OntologyGroupInfoVO> searchByKeyword(String keyword);

    void updateGroup(OntologyGroupUpdatedParam param);

    OntologyGroupInfoVO getGroupById(String groupId);
}
