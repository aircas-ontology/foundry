package com.aircas.ptr.foundry.ontology.repository.mainMapper;

import com.aircas.ptr.foundry.ontology.model.po.OntologyInstanceUrl;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 领域空间实例 URL Mapper（主库数据源）。
 * <p>位于 {@code mainMapper} 包，由 {@code mainSqlSessionFactory} 扫描，绑定主库
 * {@code postgres} 库的 {@code ontology} schema。</p>
 */
@Mapper
public interface OntologyInstanceUrlMapper extends BaseMapper<OntologyInstanceUrl> {

    /**
     * 按（空间 + 对象 + 实例）保存 URL：不存在则插入，已存在则覆盖 url 并把存入时间刷成本次时间。
     * <p>冲突目标依赖唯一索引 {@code uk_ontology_instance_url}，DDL 见
     * {@code resources/ddl/alter_table_20261112.sql}。一条语句完成读判写，
     * 并发提交同一实例时不会插入出两行。</p>
     *
     * @return 受影响行数，恒为 1
     */
    @Insert("""
            INSERT INTO ontology.ontology_instance_url
                (ontology_space_id, ontology_unique_identifier, instance_id, url, create_time)
            VALUES (#{spaceId}, #{objectId}, #{instanceId}, #{url}, CURRENT_TIMESTAMP)
            ON CONFLICT (ontology_space_id, ontology_unique_identifier, instance_id)
            DO UPDATE SET url = EXCLUDED.url, create_time = EXCLUDED.create_time
            """)
    int upsert(@Param("spaceId") Integer spaceId,
               @Param("objectId") String objectId,
               @Param("instanceId") String instanceId,
               @Param("url") String url);
}
