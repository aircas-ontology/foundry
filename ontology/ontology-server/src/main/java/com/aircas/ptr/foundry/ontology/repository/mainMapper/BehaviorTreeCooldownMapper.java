package com.aircas.ptr.foundry.ontology.repository.mainMapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

/**
 * 行为树冷却节点的执行状态。
 * <p>冷却判断与占位用一条 upsert 完成：并发执行同一节点时，只有数据库认为间隔已满的那一次会更新成功，
 * 避免两次执行同时读到旧时间后都放行。</p>
 */
@Mapper
public interface BehaviorTreeCooldownMapper {

    /**
     * 尝试占位，返回 1 表示允许执行并已记录本次时间，0 表示仍在冷却期。
     */
    @Insert("""
            INSERT INTO ontology.ontology_behavior_tree_cooldown AS c
                (ontology_space_id, tree_api, node_id, scope_key, last_run_at, update_time)
            VALUES (#{spaceId}, #{treeApi}, #{nodeId}, #{scopeKey}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (ontology_space_id, tree_api, node_id, scope_key)
            DO UPDATE SET last_run_at = EXCLUDED.last_run_at, update_time = EXCLUDED.update_time
            WHERE c.last_run_at <= EXCLUDED.last_run_at - make_interval(secs => #{intervalSeconds})
            """)
    int tryAcquire(@Param("spaceId") Integer spaceId,
                   @Param("treeApi") String treeApi,
                   @Param("nodeId") String nodeId,
                   @Param("scopeKey") String scopeKey,
                   @Param("intervalSeconds") int intervalSeconds);

    /** 删除整棵树的冷却记录，树被删除后同名树重建不会继承旧冷却 */
    @Delete("""
            DELETE FROM ontology.ontology_behavior_tree_cooldown
            WHERE ontology_space_id = #{spaceId} AND tree_api = #{treeApi}
            """)
    int deleteByTree(@Param("spaceId") Integer spaceId, @Param("treeApi") String treeApi);

    /**
     * 删除定义里已经不存在的节点的冷却记录。同一个节点标识改配置仍沿用冷却时间；
     * 节点被删掉后记录留着没有意义，还会在节点标识被复用时冒出旧冷却。
     *
     * @param nodeIds 改后的全部节点标识；为空表示树里已经没有节点，相当于清空整棵树
     */
    @Delete("""
            <script>
            DELETE FROM ontology.ontology_behavior_tree_cooldown
            WHERE ontology_space_id = #{spaceId} AND tree_api = #{treeApi}
            <if test="nodeIds != null and nodeIds.size() > 0">
                AND node_id NOT IN
                <foreach collection="nodeIds" item="nodeId" open="(" separator="," close=")">#{nodeId}</foreach>
            </if>
            </script>
            """)
    int deleteByTreeExceptNodes(@Param("spaceId") Integer spaceId, @Param("treeApi") String treeApi,
                                @Param("nodeIds") Collection<String> nodeIds);
}
