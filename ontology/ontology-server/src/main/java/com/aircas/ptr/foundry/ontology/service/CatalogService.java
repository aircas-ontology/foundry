package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.ontology.model.bo.CatalogBO;
import com.aircas.ptr.foundry.ontology.repository.CatalogRepo;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class CatalogService {

    @Resource
    private CatalogRepo catalogRepo;

    public CatalogBO getCatalogById(Long id) {
        return catalogRepo.queryById(id);
    }

    public List<CatalogBO> getCatalogByParentId(Long id) {
        return catalogRepo.queryByParentId(id);
    }

    public List<CatalogBO> getLeavesById(Long id) {
        List<CatalogBO> catalogs = new ArrayList<>();
        getCatalogByParentId(id).forEach(catalogBO -> {
            if (getCatalogByParentId(catalogBO.getId()).size() == 0)
                catalogs.add(catalogBO);
            else {
                catalogs.addAll(getLeavesById(catalogBO.getId()));
            }
        });
        return catalogs;
    }

    public List<CatalogBO> getAllCatalog() {
        return catalogRepo.queryAll();
    }

    public Long getCountByStatus(Status status) {
        return catalogRepo.queryCountByStatus(status);
    }

    public Long getCountByParentId(Long id) {
        return catalogRepo.queryCountByParentId(id);
    }

}
