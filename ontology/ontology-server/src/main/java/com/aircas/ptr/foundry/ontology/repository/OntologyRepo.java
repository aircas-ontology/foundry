package com.aircas.ptr.foundry.ontology.repository;

import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.model.po.Ontology;
import com.aircas.ptr.foundry.model.repo.BaseRepo;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyBO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class OntologyRepo extends BaseRepo<Ontology, OntologyBO> {

    @Resource
    OntologyMapper ontologyMapper;

    public OntologyRepo() {
        super(Ontology.class, OntologyBO.class);
    }

    public List<OntologyBO> queryAll() {
        return toBOs(ontologyMapper.selectAll());
    }

    public List<OntologyBO> queryOntologyByDomain(String domain, String name) {
        return toBOs(ontologyMapper.queryOntologyByDomain(domain, name));
    }

    public OntologyBO queryById(Long id) {
        return toBO(ontologyMapper.selectByPrimaryKey(id));
    }

    public Long queryCountByStatus(Status status) {
        return ontologyMapper.queryCountByStatus(status.getValue());
    }

    public Long queryCountByDomain(String domain, Status status) {
        return ontologyMapper.queryCountByDomain(domain, status.getValue());
    }

    public void insert(OntologyBO ontology) {
        ontologyMapper.insertSelective(ontology);
    }

    public void deleteOntology(Long id) {
        ontologyMapper.deleteByPrimaryKey(id);
    }

}
