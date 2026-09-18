package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.service.LinkGroupCdcJobService;

public class LinkGroupCdcJobServiceImpl implements LinkGroupCdcJobService {
    @Override
    public void handleCreateCdc(OntologyProperty after) {

    }

    @Override
    public boolean handleUpdateCdc(OntologyProperty before, OntologyProperty after) {
        return false;
    }

    @Override
    public boolean handleDeleteCdc(OntologyProperty before) {
        return false;
    }
}
