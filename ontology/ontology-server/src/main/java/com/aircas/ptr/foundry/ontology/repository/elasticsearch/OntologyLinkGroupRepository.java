package com.aircas.ptr.foundry.ontology.repository.elasticsearch;

import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.OntologyLinkGroupDTO;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OntologyLinkGroupRepository extends ElasticsearchRepository<OntologyLinkGroupDTO, Long> {
}
