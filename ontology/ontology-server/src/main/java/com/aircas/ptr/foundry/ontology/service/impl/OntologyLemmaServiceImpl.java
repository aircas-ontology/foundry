package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.param.OntologyLemmaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLemmaUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyStatisticLemmaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyStatisticLemmaUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLemma;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLemmaTreeVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLemmaVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLemmaMapper;
import com.aircas.ptr.foundry.ontology.service.OntologyLemmaService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OntologyLemmaServiceImpl extends ServiceImpl<OntologyLemmaMapper, OntologyLemma> implements OntologyLemmaService {
    @Override
    public Integer createLemma(OntologyLemmaCreateParam param) {
        return null;
    }

    @Override
    public void updateLemma(List<OntologyLemmaUpdateParam> param) {

    }

    @Override
    public OntologyLemmaTreeVO queryLemmaByOntologyId(String ontologyUniqueIdentifier) {
        return null;
    }

    @Override
    public void deleteLemma(Integer lemmaId) {

    }

    @Override
    public Integer createStatisticLemma(OntologyStatisticLemmaCreateParam param) {
        return null;
    }

    @Override
    public void updateStatisticLemma(OntologyStatisticLemmaUpdateParam param) {

    }

    @Override
    public OntologyLemmaVO queryLemmaById(Integer lemmaId) {
        return null;
    }
}
