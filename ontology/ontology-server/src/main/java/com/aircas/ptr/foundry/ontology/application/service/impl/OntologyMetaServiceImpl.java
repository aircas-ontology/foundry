package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.common.exception.DuplicatedDataException;
import com.aircas.ptr.foundry.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.application.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyMetaBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyMetaVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/11 16:15
 */

@Service
public class OntologyMetaServiceImpl implements OntologyMetaService {

    @Resource
    private OntologyMetaMapper ontologyMetaMapper;

    @Override
    public Integer add(OntologyMetaBO ontologyMetaBO) {
        int count = ontologyMetaMapper.selectByDisplayName(ontologyMetaBO.getDisplayName());
        if (count != 0) {
            throw new DuplicatedDataException("本体名称已存在");
        }

        OntologyMeta ontologyMeta = new OntologyMeta();
        BeanUtils.copyProperties(ontologyMetaBO, ontologyMeta);
        ontologyMeta.setStatus(1);
        ontologyMeta.setCreateTime(new Date());
        count = ontologyMetaMapper.insertSelective(ontologyMeta);
        return count;
    }

    @Override
    public Integer delete(List<Long> ids) {
        return ontologyMetaMapper.deleteByIds(ids);
    }

    @Override
    public Integer update(OntologyMetaBO ontologyMetaBO) {
        if (ontologyMetaBO.getId() == null) {
            throw new RuntimeException("id必传");
        }
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByPrimaryKey(ontologyMetaBO.getId());
        BeanUtils.copyProperties(ontologyMetaBO, ontologyMeta);
        ontologyMeta.setUpdateTime(new Date());
        return ontologyMetaMapper.updateByPrimaryKeySelective(ontologyMeta);
    }

    @Override
    public OntologyMetaVO getOntologyById(Long id) {
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByPrimaryKey(id);
        OntologyMetaVO ontologyMetaVO = new OntologyMetaVO();
        BeanUtils.copyProperties(ontologyMeta, ontologyMetaVO);
        return ontologyMetaVO;
    }

    @Override
    public OntologyMetaVO getOntologyByUniqueIdentifier(String uniqueIdentifier) {
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByUniqueIdentifier(uniqueIdentifier);
        OntologyMetaVO ontologyMetaVO = new OntologyMetaVO();
        BeanUtils.copyProperties(ontologyMeta, ontologyMetaVO);
        return ontologyMetaVO;
    }

    @Override
    public List<OntologyMetaVO> getAllOntologies() {
        List <OntologyMeta> result = ontologyMetaMapper.selectAllOntologies();
        List<OntologyMetaVO> retResult = new ArrayList();
        for (OntologyMeta meta: result) {
            OntologyMetaVO ontologyMetaVO = new OntologyMetaVO();
            BeanUtils.copyProperties(meta, ontologyMetaVO);
            retResult.add(ontologyMetaVO);
        }
        return retResult;
    }
}
