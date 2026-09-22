package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.StorageGroupCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.StorageGroupUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.StorageGroup;
import com.aircas.ptr.foundry.ontology.model.vo.StorageGroupVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface StorageGroupService extends IService<StorageGroup> {

    void create(StorageGroupCreateParam param);

    void update(StorageGroupUpdateParam param);

    void delete(Integer id);

    List<StorageGroupVO> listByOntologyId(String ontologyUniqueIdentifier);
}
