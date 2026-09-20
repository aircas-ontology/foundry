package com.aircas.ptr.foundry.agent.tools.api;

import com.aircas.ptr.foundry.agent.tools.api.model.OntologyGroupToolVO;
import com.aircas.ptr.foundry.agent.tools.api.model.OntologyMetaToolVO;

import java.util.List;

/**
 * 本体查询工具契约。
 *
 * <p>定义 ontology-server 对外暴露给 Agent 的只读原子操作。混合模式下的职责划分：</p>
 * <ul>
 *   <li><b>ontology-server</b>：实现本接口（委托既有 Service，将内部 VO 映射为 ToolVO），
 *       并通过 {@code OntologyToolController} 以 REST 形式暴露（路径前缀 {@code /tool/query}）。</li>
 *   <li><b>agent-server</b>：以 HTTP 客户端实现本接口，在实现方法上标注 Spring AI
 *       {@code @Tool} / {@code @ToolParam}，注册到 ChatClient 供大模型按需调用。</li>
 * </ul>
 *
 * <p>本接口刻意不引入任何框架注解，保持契约模块零依赖；工具语义描述由 agent-server 的实现类承载。</p>
 */
public interface OntologyQueryTools {

    /**
     * 按关键词搜索本体元数据。
     *
     * @param keyword 搜索关键词，可为空（空则返回全部）
     * @return 匹配的本体元数据列表
     */
    List<OntologyMetaToolVO> searchOntologyMeta(String keyword);

    /**
     * 根据唯一标识查询单个本体元数据详情。
     *
     * @param uniqueIdentifier 本体唯一标识
     * @return 本体元数据，不存在时返回 {@code null}
     */
    OntologyMetaToolVO getOntologyMetaDetail(String uniqueIdentifier);

    /**
     * 根据空间 id 查询该空间下的本体分组列表。
     *
     * @param spaceId 本体空间 id
     * @return 分组列表
     */
    List<OntologyGroupToolVO> listGroupsBySpace(Integer spaceId);
}
