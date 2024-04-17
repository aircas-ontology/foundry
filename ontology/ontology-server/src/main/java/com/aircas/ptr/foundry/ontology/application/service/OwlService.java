package com.aircas.ptr.foundry.ontology.application.service;



import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import java.net.URI;


public interface OwlService {

    String getClassResource(String ontologyApi) throws Exception;

    String getIndividualResource(String ontologyApi, String primaryKey) throws Exception;


}
