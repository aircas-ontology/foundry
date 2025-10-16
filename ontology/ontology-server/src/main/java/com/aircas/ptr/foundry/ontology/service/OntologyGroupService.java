package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.bo.OntologyGroupBO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyGroup;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkGraphVO;
import com.aircas.ptr.foundry.ontology.model.param.OntologyGroupAddParam;
import com.baomidou.mybatisplus.extension.service.IService;
import com.github.pagehelper.PageInfo;

import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:02
 */


public interface OntologyGroupService extends IService<OntologyGroup> {

    List<OntologyGroupInfoVO> searchByKeyword(String keyword);

    Integer add(OntologyGroupAddParam param);

    Integer delete(List<Long> ids);

    Integer update(OntologyGroupBO ontologyGroupBO);

    OntologyGroupVO getOntologyGroupById(Long id);

    PageInfo<OntologyGroupVO> list(Integer page, Integer size);

    OntologyLinkGraphVO getOntologyGroupLinks(String id);

    PageInfo<OntologyGroupVO> search(String keyword, Integer page, Integer size);
}
