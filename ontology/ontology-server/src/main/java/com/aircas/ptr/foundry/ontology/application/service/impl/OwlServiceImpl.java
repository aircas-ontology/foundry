package com.aircas.ptr.foundry.ontology.application.service.impl;


import com.aircas.ptr.foundry.ontology.application.service.OwlService;
import lombok.RequiredArgsConstructor;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;


@Service
@RequiredArgsConstructor
public class OwlServiceImpl implements OwlService {


    //根据uri获取资源
    @Override
    public String getResource(String uri) throws OWLOntologyCreationException, OWLOntologyStorageException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.createOntology();

        OWLDataFactory factory = manager.getOWLDataFactory();
        OWLClass class1 = factory.getOWLClass(IRI.create("舰船本体"));
        OWLNamedIndividual namedIndividual = factory.getOWLNamedIndividual("华盛顿号");
        OWLAxiom axiom = factory.getOWLClassAssertionAxiom(class1, namedIndividual);
        manager.addAxiom(ontology, axiom);
        File file = new File("ontology.owl");
        manager.saveOntology(ontology, IRI.create(file.toURI()));

        return null;
    }


}
