package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.common.exception.DuplicatedDataException;
import com.aircas.ptr.foundry.model.po.OntologyChildLink;
import com.aircas.ptr.foundry.model.po.OntologyLink;
import com.aircas.ptr.foundry.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.application.service.OntologyLinkService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyLinkBO;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyLinkGroupBo;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyLinkVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyChildLinkMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyLinkMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:03
 */

@Service
public class OntologyLinkServiceImpl implements OntologyLinkService {

    @Resource
    private OntologyLinkMapper ontologyLinkMapper;

    @Resource
    private OntologyLinkGroupMapper ontologyLinkGroupMapper;

    @Resource
    private OntologyChildLinkMapper ontologyChildLinkMapper;

    @Override
    public Integer add(OntologyLinkGroupBo ontologyLinkGroupBo) {
        OntologyChildLink forwardLink = new OntologyChildLink();
        BeanUtils.copyProperties(ontologyLinkGroupBo.getForwardLink(), forwardLink);
        int count = ontologyChildLinkMapper.insert(forwardLink);
        if (count == 0) {
            return 0;
        }

        OntologyChildLink backwardLink = new OntologyChildLink();
        BeanUtils.copyProperties(ontologyLinkGroupBo.getBackwardLink(), backwardLink);
        count = ontologyChildLinkMapper.insert(backwardLink);
        if (count == 0) {
            return 0;
        }

        OntologyLinkGroup ontologyLinkGroup = new OntologyLinkGroup();
        BeanUtils.copyProperties(ontologyLinkGroupBo, ontologyLinkGroup);
        ontologyLinkGroup.setStatus(1);
        ontologyLinkGroup.setCreateTime(new Date());

        count = ontologyLinkGroupMapper.insert(ontologyLinkGroup);
        return 0;
    }


//    @Override
//    public Integer add(OntologyLinkGroupBo ontologyLinkGroupBo) {
//        int count = ontologyLinkMapper.selectByDisplayName(ontologyLinkBO.getOntologyUniqueIdentifierFrom(), ontologyLinkBO.getOntologyUniqueIdentifierTo(), ontologyLinkBO.getDisplayName());
//        if (count != 0) {
//            throw new DuplicatedDataException("本体间关系名称已存在");
//        }
//
//        OntologyLink ontologyLink = new OntologyLink();
//        BeanUtils.copyProperties(ontologyLinkBO, ontologyLink);
//        ontologyLink.setStatus(1);
//        ontologyLink.setCreateTime(new Date());
//        count = ontologyLinkMapper.insertSelective(ontologyLink);
//        return count;
//    }

    @Override
    public Integer delete(List<Long> ids) {
        return ontologyLinkMapper.deleteByIds(ids);
    }

    @Override
    public Integer update(OntologyLinkBO ontologyLinkBO) {
        if (ontologyLinkBO.getId() == null) {
            throw new RuntimeException("id必传");
        }
        OntologyLink ontologyLink = ontologyLinkMapper.selectByPrimaryKey(ontologyLinkBO.getId());
        BeanUtils.copyProperties(ontologyLinkBO, ontologyLink);
        ontologyLink.setUpdateTime(new Date());
        return ontologyLinkMapper.updateByPrimaryKeySelective(ontologyLink);
    }

    @Override
    public OntologyLinkVO getOntologyLinkById(Long id) {
        OntologyLink ontologyLink = ontologyLinkMapper.selectByPrimaryKey(id);
        OntologyLinkVO ontologyLinkVO = new OntologyLinkVO();
        BeanUtils.copyProperties(ontologyLink, ontologyLinkVO);
        return ontologyLinkVO;
    }
}
