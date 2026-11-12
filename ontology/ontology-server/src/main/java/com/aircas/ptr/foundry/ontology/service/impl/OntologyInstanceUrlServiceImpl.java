package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.param.OntologyInstanceUrlSaveParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyInstanceUrl;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyInstanceUrlVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyInstanceUrlMapper;
import com.aircas.ptr.foundry.ontology.service.OntologyInstanceUrlService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class OntologyInstanceUrlServiceImpl
        extends ServiceImpl<OntologyInstanceUrlMapper, OntologyInstanceUrl>
        implements OntologyInstanceUrlService {

    @Override
    public OntologyInstanceUrlVO saveInstanceUrl(OntologyInstanceUrlSaveParam param) {
        baseMapper.upsert(param.getSpaceId(), param.getObjectId(), param.getInstanceId(), param.getUrl().trim());
        return toVO(requireByKey(param.getSpaceId(), param.getObjectId(), param.getInstanceId()));
    }

    @Override
    public List<OntologyInstanceUrlVO> listInstanceUrls(Integer spaceId, String objectId, String instanceId) {
        return baseMapper.selectList(new LambdaQueryWrapper<OntologyInstanceUrl>()
                        .eq(OntologyInstanceUrl::getOntologySpaceId, spaceId)
                        .eq(StringUtils.isNotBlank(objectId), OntologyInstanceUrl::getOntologyUniqueIdentifier, objectId)
                        .eq(StringUtils.isNotBlank(instanceId), OntologyInstanceUrl::getInstanceId, instanceId)
                        .orderByDesc(OntologyInstanceUrl::getCreateTime))
                .stream().map(this::toVO).toList();
    }

    private OntologyInstanceUrl requireByKey(Integer spaceId, String objectId, String instanceId) {
        OntologyInstanceUrl po = baseMapper.selectOne(new LambdaQueryWrapper<OntologyInstanceUrl>()
                .eq(OntologyInstanceUrl::getOntologySpaceId, spaceId)
                .eq(OntologyInstanceUrl::getOntologyUniqueIdentifier, objectId)
                .eq(OntologyInstanceUrl::getInstanceId, instanceId));
        PreconditionUtils.checkNotNull(po, "实例 URL 落库后未查到记录，请确认唯一索引 uk_ontology_instance_url 已建",
                HttpStatus.INTERNAL_SERVER_ERROR);
        return po;
    }

    private OntologyInstanceUrlVO toVO(OntologyInstanceUrl po) {
        return OntologyInstanceUrlVO.builder()
                .id(po.getId())
                .spaceId(po.getOntologySpaceId())
                .objectId(po.getOntologyUniqueIdentifier())
                .instanceId(po.getInstanceId())
                .url(po.getUrl())
                .createTime(po.getCreateTime())
                .build();
    }
}
