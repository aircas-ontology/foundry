package com.aircas.ptr.foundry.ontology.repository;

import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.model.po.Catalog;
import com.aircas.ptr.foundry.model.repo.BaseRepo;
import com.aircas.ptr.foundry.ontology.model.bo.CatalogBO;
import com.aircas.ptr.foundry.ontology.repository.dao.CatalogMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class CatalogRepo extends BaseRepo<Catalog, CatalogBO> {

    @Resource
    CatalogMapper catalogMapper;

    public CatalogRepo() {
        super(Catalog.class, CatalogBO.class);
    }

    public List<CatalogBO> queryAll() {
        return toBOs(catalogMapper.selectAll());
    }

    public CatalogBO queryById(Long id) {
        return toBO(catalogMapper.selectByPrimaryKey(id));
    }

    public List<CatalogBO> queryByParentId(Long parentId) {
        return toBOs(catalogMapper.queryByParentId(parentId));
    }

    public Long queryCountByStatus(Status status) {
        return catalogMapper.queryCountByStatus(status.getValue());
    }

    public Long queryCountByParentId(Long parentId) {
        return catalogMapper.queryCountByParentId(parentId);
    }

    public void insert(CatalogBO catalog) {

        catalogMapper.insertSelective(catalog);
    }

    public void deleteCatalog(Long id) {
        catalogMapper.deleteByPrimaryKey(id);
    }

}
