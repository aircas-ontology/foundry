package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;

public interface PropertyCdcJobService {
    void handleCreateCdc(OntologyProperty after);
    boolean handleUpdateCdc(OntologyProperty before,OntologyProperty after);
    boolean handleDeleteCdc(OntologyProperty before);
}
