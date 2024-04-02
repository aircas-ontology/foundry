package com.aircas.ptr.foundry.model.po;

import lombok.Data;


@Data
public class Function {
    /**
     * Column: api
     */
    private String api;

    /**
     * Column: desc
     */
    private String desc;

    /**
     * Column: status
     */
    private Long status;

    /**
     * Column: id
     */
    private Long id;

    /**
     * Column: ontologyApis
     */
    private String ontologyapis;

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api == null ? null : api.trim();
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc == null ? null : desc.trim();
    }

    public Long getStatus() {
        return status;
    }

    public void setStatus(Long status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOntologyapis() {
        return ontologyapis;
    }

    public void setOntologyapis(String ontologyapis) {
        this.ontologyapis = ontologyapis == null ? null : ontologyapis.trim();
    }
}