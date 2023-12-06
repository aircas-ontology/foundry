package com.aircas.ptr.foundry.ontology.application.service;

import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyBO;
import com.aircas.ptr.foundry.ontology.repository.CatalogRepo;
import com.aircas.ptr.foundry.ontology.repository.OntologyRepo;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class OntologyService {

    @Resource
    private OntologyRepo ontologyRepo;

    public void deleteOntologyById(Long id) {
        ontologyRepo.deleteOntology(id);
    }

    public void insertOntology(OntologyBO ontologyBO) {
        ontologyRepo.insert(ontologyBO);
    }

    public OntologyBO getOntologyById(Long id) {
        return ontologyRepo.queryById(id);
    }

    public List<OntologyBO> getAllOntology() {

        return ontologyRepo.queryAll();
    }

    public List<OntologyBO> getOntologyByDomain(String domain, String name) {
        return ontologyRepo.queryOntologyByDomain(domain, name);
    }

    public Long getCountByStatus(Status status) {
        return ontologyRepo.queryCountByStatus(status);
    }

    public Long getCountByDomain(String domain) {
        return ontologyRepo.queryCountByDomain(domain, Status.ENABLE);
    }

}
