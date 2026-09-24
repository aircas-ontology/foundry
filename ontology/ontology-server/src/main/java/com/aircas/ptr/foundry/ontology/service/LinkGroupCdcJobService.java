package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;

public interface LinkGroupCdcJobService {
    void handleCreateCdc(OntologyLinkGroup after);
    boolean handleUpdateCdc(OntologyLinkGroup before,OntologyLinkGroup after);
    boolean handleDeleteCdc(OntologyLinkGroup before);
}
