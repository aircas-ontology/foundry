package com.aircas.ptr.foundry.ontology.tool;

import com.aircas.ptr.foundry.agent.tools.api.OntologyQueryTools;
import com.aircas.ptr.foundry.agent.tools.api.model.OntologyGroupToolVO;
import com.aircas.ptr.foundry.agent.tools.api.model.OntologyMetaToolVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.service.OntologyGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 本体查询工具契约的 ontology-server 端实现（混合模式提供端）。
 *
 * <p>委托既有 {@link OntologyMetaService} / {@link OntologyGroupService} 完成原子查询，
 * 并将内部 VO 映射为对外工具契约模型 {@code ToolVO}，仅暴露大模型所需的核心字段。
 * 由 {@code OntologyToolController} 以 REST 形式暴露给 agent-server 调用。</p>
 */
@Service
@RequiredArgsConstructor
public class OntologyQueryToolsImpl implements OntologyQueryTools {

    private final OntologyMetaService ontologyMetaService;
    private final OntologyGroupService ontologyGroupService;

    @Override
    public List<OntologyMetaToolVO> searchOntologyMeta(String keyword) {
        List<OntologyMetaInfoVO> list = ontologyMetaService.searchByKeyword(keyword);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toMetaToolVO).toList();
    }

    @Override
    public OntologyMetaToolVO getOntologyMetaDetail(String uniqueIdentifier) {
        OntologyMetaInfoVO vo = ontologyMetaService.getMetaByUniqueIdentifier(uniqueIdentifier);
        return vo == null ? null : toMetaToolVO(vo);
    }

    @Override
    public List<OntologyGroupToolVO> listGroupsBySpace(Integer spaceId) {
        List<OntologyGroupInfoVO> list = ontologyGroupService.getGroupBySpaceId(spaceId);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toGroupToolVO).toList();
    }

    private OntologyMetaToolVO toMetaToolVO(OntologyMetaInfoVO vo) {
        return new OntologyMetaToolVO(
                vo.getUniqueIdentifier(),
                vo.getDisplayName(),
                vo.getDescription(),
                vo.getApiName(),
                vo.getSpaceId(),
                vo.getEntityCount(),
                vo.getRelationCount(),
                vo.getPropertyCount()
        );
    }

    private OntologyGroupToolVO toGroupToolVO(OntologyGroupInfoVO vo) {
        return new OntologyGroupToolVO(
                vo.getGroupId(),
                vo.getGroupName(),
                vo.getDescription(),
                vo.getSpaceId()
        );
    }
}
