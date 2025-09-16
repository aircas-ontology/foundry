package com.aircas.ptr.foundry.ontology.entity.repository;

import com.aircas.ptr.foundry.ontology.entity.entity.OntologyRelation;
import com.arangodb.springframework.annotation.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface RelationRepository extends CrudRepository<OntologyRelation, String> {
    // 使用@Query注解明确指定查询
    @Query("FOR r IN relations FILTER r._from == @0 OR r._to == @1 RETURN r")
    List<OntologyRelation> findByFromOrTo(String from, String to);
} 