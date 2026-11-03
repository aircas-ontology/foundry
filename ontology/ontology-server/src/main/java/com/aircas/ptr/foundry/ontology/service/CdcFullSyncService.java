package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.CdcFullSyncParam;
import com.aircas.ptr.foundry.ontology.model.vo.CdcFullSyncResultVO;

/**
 * CDC 初始化全量同步服务。
 * <p>
 * 语义等价于 Debezium 的 initial snapshot：以数据库当前内容为唯一真相源，
 * 将主库（ontology_space / ontology_meta / ontology_property）与数据湖
 * （已绑定本体主键属性的业务表）的存量数据全量重建到对应的 ES 索引，
 * 并清理数据库中已不存在的孤儿文档。
 * <p>
 * 幂等性：文档 _id 取自数据库主键（实例文档为 pk 串），写入为 upsert；
 * 孤儿清理按"数据库当前内容"求差集。因此重复执行、与 CDC 增量消费交错执行，
 * 最终结果都收敛到与数据库一致，不会产生重复文档。
 * <p>
 * 实例段还会把图节点与候选关系边（状态 DELETE）同步到 ArangoDB，并按数据湖当前内容清理孤儿节点
 * （可用 {@code syncGraph=false} 关闭）；图库侧同样按「本体+主键」「关系+起终点节点」定位覆盖，不产生重复数据。
 */
public interface CdcFullSyncService {

    /**
     * 执行全量同步（同步阻塞直到完成）。
     *
     * @param param 同步范围选项，可为 null（表示全部索引、全部本体）
     * @return 各索引写入/清理的文档数与耗时
     */
    CdcFullSyncResultVO fullSync(CdcFullSyncParam param);
}
