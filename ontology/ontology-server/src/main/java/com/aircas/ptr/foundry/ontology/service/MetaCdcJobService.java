package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;

public interface MetaCdcJobService {
    void handleCreateCdc(OntologyMeta after);
    boolean handleUpdateCdc(OntologyMeta before,OntologyMeta after);
    boolean handleDeleteCdc(OntologyMeta before);
}
