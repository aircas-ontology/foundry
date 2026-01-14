package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.OntologyLemmaTypeEnum;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLemmaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLemmaUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyStatisticLemmaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyStatisticLemmaUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLemma;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLemmaTreeVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLemmaVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLemmaMapper;
import com.aircas.ptr.foundry.ontology.service.OntologyLemmaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OntologyLemmaServiceImpl extends ServiceImpl<OntologyLemmaMapper, OntologyLemma> implements OntologyLemmaService {


    private final ObjectMapper objectMapper = new ObjectMapper();


    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public Integer createLemma(OntologyLemmaCreateParam param) {
        var lemma = OntologyLemma.builder()
                .content(param.getContent())
                .ontologyUniqueIdentifier(param.getOntologyIdentifier())
                .extraInfo(param.getExtraInfo())
                .type(param.getType())
                .parentId(param.getParentId())
                .orderIndex(param.getOrderIndex())
                .title(param.getTitle())
                .build();
        save(lemma);
        return lemma.getId();
    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void batchUpdateLemma(List<OntologyLemmaUpdateParam> param) {
        var lemmaList = param.stream().map(v -> OntologyLemma.builder()
                        .content(v.getContent())
                        .ontologyUniqueIdentifier(v.getOntologyIdentifier())
                        .extraInfo(v.getExtraInfo())
                        .type(v.getType())
                        .parentId(v.getParentId())
                        .orderIndex(v.getOrderIndex())
                        .title(v.getTitle())
                        .id(v.getLemmaId())
                        .build())
                .collect(Collectors.toList());
        saveOrUpdateBatch(lemmaList);
    }

    @Override
    public OntologyLemmaTreeVO queryLemmaByOntologyId(String ontologyUniqueIdentifier) {
        var lemmaList = list(new LambdaQueryWrapper<OntologyLemma>().eq(OntologyLemma::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        if (CollectionUtils.isEmpty(lemmaList)) {
            return null;
        }
        OntologyLemmaTreeVO root = new OntologyLemmaTreeVO().setLemmaId(0).setChild(Lists.newArrayList());
        buildLemmaTree(root, lemmaList);
        return root;
    }


    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void deleteLemma(Integer lemmaId) {
        var idsList = Lists.newArrayList(lemmaId);
        var removeIds = findChildLemma(idsList);
        removeByIds(removeIds);
    }


    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    @SneakyThrows
    public Integer createStatisticLemma(OntologyStatisticLemmaCreateParam param) {

        var statistics = getOne(new LambdaQueryWrapper<OntologyLemma>().eq(OntologyLemma::getType, OntologyLemmaTypeEnum.STATISTICS)
                .eq(OntologyLemma::getOntologyUniqueIdentifier, param.getOntologyIdentifier()));


        var config = objectMapper.readTree(param.getSceneConfig());
        var objectNode = (ObjectNode) config;
        objectNode.put("sceneId", param.getSceneId());

        //已创建词条，更新画布
        if (statistics != null) {
            updateById(statistics.setContent(param.getSceneUrl())
                    .setExtraInfo(config.toPrettyString()));
            return statistics.getId();
        }

        //find max orderIndex
        var childs = list(new LambdaQueryWrapper<OntologyLemma>().eq(OntologyLemma::getParentId, 0)
                .eq(OntologyLemma::getOntologyUniqueIdentifier, param.getOntologyIdentifier())
                .orderByDesc(OntologyLemma::getOrderIndex));
        var orderIndex = CollectionUtils.isEmpty(childs) ? 1 : childs.get(0).getOrderIndex() + 1;

        var lemma = OntologyLemma.builder()
                .content(param.getSceneUrl())
                .ontologyUniqueIdentifier(param.getOntologyIdentifier())
                .extraInfo(config.toPrettyString())
                .type(OntologyLemmaTypeEnum.STATISTICS)
                .parentId(0)
                .orderIndex(orderIndex)
                .title("统计")
                .build();
        save(lemma);
        return lemma.getId();
    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    @SneakyThrows
    public void updateStatisticLemma(OntologyStatisticLemmaUpdateParam param) {

        var statistics = getById(param.getLemmaId());
        PreconditionUtils.checkNotNull(statistics, "invalid lemma id:" + param.getLemmaId());

        var config = objectMapper.readTree(param.getSceneConfig());
        var objectNode = (ObjectNode) config;
        objectNode.put("sceneId", param.getSceneId());
        statistics.setExtraInfo(config.toPrettyString()).setContent(param.getSceneUrl());
        updateById(statistics);

    }

    @Override
    @SneakyThrows
    public OntologyLemmaVO queryLemmaById(Integer lemmaId) {
        var lemma = getById(lemmaId);
        PreconditionUtils.checkNotNull(lemma, "invalid lemmaId：" + lemmaId);

        return OntologyLemmaVO.builder()
                .lemmaId(lemma.getId())
                .content(lemma.getContent())
                .extraInfo(objectMapper.readTree(lemma.getExtraInfo()))
                .orderIndex(lemma.getOrderIndex())
                .parentId(lemma.getParentId())
                .title(lemma.getTitle())
                .type(lemma.getType())
                .ontologyUniqueIdentifier(lemma.getOntologyUniqueIdentifier())
                .build();
    }


    private void buildLemmaTree(OntologyLemmaTreeVO root, List<OntologyLemma> lemmaList) {

        var parentId = root.getLemmaId();
        lemmaList.stream().filter(v -> v.getParentId().equals(parentId)).forEach(
                l -> {
                    var lemma = OntologyLemmaTreeVO.builder()
                            .lemmaId(l.getId())
                            .parentId(l.getParentId())
                            .content(l.getContent())
                            .extraInfo(l.getExtraInfo())
                            .orderIndex(l.getOrderIndex())
                            .type(l.getType())
                            .ontologyUniqueIdentifier(l.getOntologyUniqueIdentifier())
                            .title(l.getTitle())
                            .child(Lists.newArrayList())
                            .build();
                    root.getChild().add(lemma);
                    buildLemmaTree(lemma, lemmaList);
                }
        );
    }


    private List<Integer> findChildLemma(List<Integer> lemmaIds) {
        var res = Lists.newArrayList(lemmaIds);
        var lemmaList = list(new LambdaQueryWrapper<OntologyLemma>().in(OntologyLemma::getParentId, lemmaIds));
        if (CollectionUtils.isNotEmpty(lemmaList)) {
            var allChild = findChildLemma(lemmaList.stream().map(v -> v.getId()).collect(Collectors.toList()));
            res.addAll(allChild);
        }
        return res;
    }
}
