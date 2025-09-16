package com.aircas.ptr.foundry.ontology.entity.repository;

import com.aircas.ptr.foundry.ontology.entity.entity.OntologyNode;
import com.arangodb.springframework.repository.ArangoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OntologyNodeRepository extends ArangoRepository<OntologyNode, String> {
    OntologyNode findByName(String name);
} 