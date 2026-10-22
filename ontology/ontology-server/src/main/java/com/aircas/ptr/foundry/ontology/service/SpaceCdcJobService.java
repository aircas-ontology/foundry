package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;

public interface SpaceCdcJobService {
    void handleCreateCdc(OntologySpace after);
    boolean handleUpdateCdc(OntologySpace before,OntologySpace after);
    boolean handleDeleteCdc(OntologySpace before);
}
