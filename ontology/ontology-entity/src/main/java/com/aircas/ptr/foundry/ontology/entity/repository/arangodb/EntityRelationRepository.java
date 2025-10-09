package com.aircas.ptr.foundry.ontology.entity.repository.arangodb;

import com.aircas.ptr.foundry.ontology.entity.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.entity.model.document.EntityRelation;
import com.arangodb.springframework.annotation.Query;
import com.arangodb.springframework.repository.ArangoRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntityRelationRepository extends ArangoRepository<EntityRelation, String> {

    List<EntityRelation> findByFromIn(List<EntityNode> nodes);

    List<EntityRelation> findByToIn(List<EntityNode> nodes);

    @Query("FOR r IN relation FILTER r._id IN @relationIds REMOVE r IN relation")
    void deleteByIds(@Param("relationIds") List<String> relationIds);

} 