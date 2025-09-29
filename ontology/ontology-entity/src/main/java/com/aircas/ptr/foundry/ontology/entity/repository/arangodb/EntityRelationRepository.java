package com.aircas.ptr.foundry.ontology.entity.repository.arangodb;

import com.aircas.ptr.foundry.ontology.entity.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.entity.model.document.EntityRelation;
import com.arangodb.springframework.repository.ArangoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntityRelationRepository extends ArangoRepository<EntityRelation, String> {

    List<EntityRelation> findByFromIn(List<EntityNode> nodes);

    List<EntityRelation> findByToIn(List<EntityNode> nodes);


} 