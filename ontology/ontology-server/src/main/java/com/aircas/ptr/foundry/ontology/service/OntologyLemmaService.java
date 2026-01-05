package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.OntologyLemmaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLemmaUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyStatisticLemmaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyStatisticLemmaUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLemma;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLemmaTreeVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLemmaVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface OntologyLemmaService  extends IService<OntologyLemma>  {
    
    Integer createLemma(OntologyLemmaCreateParam param);

    void batchUpdateLemma(List<OntologyLemmaUpdateParam> param);

    OntologyLemmaTreeVO queryLemmaByOntologyId(String ontologyUniqueIdentifier);

    void deleteLemma(Integer lemmaId);

    Integer createStatisticLemma(OntologyStatisticLemmaCreateParam param);

    void updateStatisticLemma(OntologyStatisticLemmaUpdateParam param);

    OntologyLemmaVO queryLemmaById(Integer lemmaId);
}
