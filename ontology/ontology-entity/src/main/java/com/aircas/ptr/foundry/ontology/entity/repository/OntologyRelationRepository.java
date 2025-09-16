package com.aircas.ptr.foundry.ontology.entity.repository;

import com.aircas.ptr.foundry.ontology.entity.entity.OntologyRelation;
import com.arangodb.springframework.annotation.Query;
import com.arangodb.springframework.repository.ArangoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OntologyRelationRepository extends ArangoRepository<OntologyRelation, String> {
    List<OntologyRelation> findBy_from(String fromNode);

    List<OntologyRelation> findBy_to(String toNode);

    List<OntologyRelation> findByType(String relationType);

    @Query("FOR r IN relations FILTER r._from == @0 OR r._to == @1 RETURN r")
    List<OntologyRelation> findByFromOrTo(String from, String to);
} 