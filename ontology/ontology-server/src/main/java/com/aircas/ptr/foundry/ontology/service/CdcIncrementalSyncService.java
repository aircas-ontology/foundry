package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.CdcFullSyncParam;
import com.aircas.ptr.foundry.ontology.model.vo.CdcFullSyncResultVO;

/**
 * CDC 增量同步服务。
 * <p>
 * 与全量同步（{@link CdcFullSyncService}）的区别：
 * <ul>
 *   <li>仅 upsert 数据，不做孤儿清理（差集删除）；</li>
 *   <li>适合在两次全量同步之间补漏、或按时间窗口定期触发；</li>
 *   <li>可安全地与全量同步交错执行（双方均按 _id upsert，最终一致）；</li>
 *   <li>并发保护：同一实例同时只允许一个增量同步，重复触发返回 409。</li>
 * </ul>
 * <p>
 * 幂等性保证：
 * <ul>
 *   <li>元数据索引（space/meta/property/link_group）：文档 _id = 数据库主键 id，saveAll 即 upsert，重复执行结果不变；</li>
 *   <li>实例索引：文档 _id = pk（{schema}.{table}.{ontology_uid}.{主键值}），同样 upsert，重建结果与逐条快照写入等价；</li>
 *   <li>图库（ArangoDB）：按「本体 + 归一化主键」覆盖写节点，不产生重复数据；</li>
 *   <li>因此增量同步天然幂等，可安全重复调用。</li>
 * </ul>
 */
public interface CdcIncrementalSyncService {

    /**
     * 执行增量同步（同步阻塞直到完成）。
     *
     * @param param 同步范围选项，可为 null（表示全部索引、全部本体）
     * @return 各索引写入的文档数与耗时（无删除计数，增量不做清理）
     */
    CdcFullSyncResultVO incrementalSync(CdcFullSyncParam param);
}
