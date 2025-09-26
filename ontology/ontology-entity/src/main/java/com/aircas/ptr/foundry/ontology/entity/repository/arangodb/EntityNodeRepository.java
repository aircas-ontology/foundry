package com.aircas.ptr.foundry.ontology.entity.repository.arangodb;

import com.aircas.ptr.foundry.ontology.entity.model.document.EntityNode;
import com.arangodb.springframework.repository.ArangoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EntityNodeRepository extends ArangoRepository<EntityNode, String> {
}