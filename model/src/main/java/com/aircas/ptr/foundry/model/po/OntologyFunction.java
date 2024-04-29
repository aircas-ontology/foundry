package com.aircas.ptr.foundry.model.po;

import lombok.Data;

import java.util.Date;


@Data
public class OntologyFunction {
    /**
     * Column: api
     */
    private String api;

    /**
     * Column: original_api
     */
    private String originalApi;

    /**
     * Column: ontology_unique_identifier
     */
    private String ontologyUniqueIdentifier;

    /**
     * Column: id
     */
    private Long id;

    private String description;

    /**
     isPreview == 1, 则是测试模式下，使用完后要删除
     */
    private boolean isPreview;

    /**
     * Column: create_time
     */
    private Date createTime;

    /**
     * Column: update_time
     */
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}