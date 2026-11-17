package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkDirectionEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkGraphVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkObjectsVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;



public interface OntologyLinkGroupService extends IService<OntologyLinkGroup> {

    /**
     * 创建对象关系及实体关系边
     *
     * @return 新建关系的 uniqueIdentifier
     */
    String createLink(OntologyLinkCreateParam linkCreateParam);

    void updateLink(OntologyLinkUpdateParam linkUpdateParam);

    List<OntologyMetaInfoVO> getLinkedOntology(String ontologyUniqueIdentifier);

    OntologyLinkInfoVO getLinkByUniqueIdentifier(String uniqueIdentifier);

    OntologyLinkObjectsVO getLinkObjectsByUniqueIdentifier(String uniqueIdentifier);

    /**
     * 分页查询空间内的关系，按关系名称模糊匹配
     *
     * @param spaceId      对象空间id，必填
     * @param relationName 关系名称，模糊匹配，为空查全部
     */
    Page<OntologyLinkObjectsVO> pageGetLinkObjects(Integer spaceId, String relationName, Integer pageNum, Integer pageSize);

    List<OntologyLinkInfoVO> getLinksByGroupId(String groupId);

    List<OntologyLinkInfoVO> getLinksByOntologyUniqueIdentifier(String ontologyUniqueIdentifier, OntologyLinkDirectionEnum direction);

    List<OntologyLinkInfoVO> getByCategoryId(Integer categoryId);

    OntologyLinkGraphVO getLinkGraph(Integer spaceId, String ontologyUniqueIdentifier);

    void deleteLinkByLinkUniqueIdentifier(String linkUniqIdentifier);

}
