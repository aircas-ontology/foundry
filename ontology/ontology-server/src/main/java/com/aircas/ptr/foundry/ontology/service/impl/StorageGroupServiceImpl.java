package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.StorageGroupCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.StorageGroupUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.StorageGroup;
import com.aircas.ptr.foundry.ontology.model.vo.StorageGroupVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.StorageGroupMapper;
import com.aircas.ptr.foundry.ontology.service.StorageGroupService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StorageGroupServiceImpl extends ServiceImpl<StorageGroupMapper, StorageGroup> implements StorageGroupService {

    @Resource
    private OntologyMetaMapper ontologyMetaMapper;

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void create(StorageGroupCreateParam param) {
        var ontologyExists = ontologyMetaMapper.selectCount(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, param.getOntologyUniqueIdentifier())
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())) > 0;
        PreconditionUtils.checkArgument(ontologyExists,
                "本体对象不存在，ontologyUniqueIdentifier=" + param.getOntologyUniqueIdentifier(),
                HttpStatus.BAD_REQUEST);

        var entity = StorageGroup.builder()
                .ontologyUniqueIdentifier(param.getOntologyUniqueIdentifier())
                .storageName(param.getStorageName())
                .build();
        save(entity);
    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void update(StorageGroupUpdateParam param) {
        var existing = getById(param.getId());
        PreconditionUtils.checkArgument(existing != null, "存储分组记录不存在，id=" + param.getId(), HttpStatus.BAD_REQUEST);
        existing.setStorageName(param.getStorageName());
        updateById(existing);
    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void delete(Integer id) {
        var existing = getById(id);
        PreconditionUtils.checkArgument(existing != null, "存储分组记录不存在，id=" + id, HttpStatus.BAD_REQUEST);
        removeById(id);
    }

    @Override
    public List<StorageGroupVO> listByOntologyId(String ontologyUniqueIdentifier) {
        var list = list(new LambdaQueryWrapper<StorageGroup>()
                .eq(StorageGroup::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        return list.stream()
                .map(entity -> StorageGroupVO.builder()
                        .id(entity.getId())
                        .ontologyUniqueIdentifier(entity.getOntologyUniqueIdentifier())
                        .storageName(entity.getStorageName())
                        .createTime(entity.getCreateTime())
                        .updateTime(entity.getUpdateTime())
                        .build())
                .collect(Collectors.toList());
    }
}
