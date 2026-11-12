package com.aircas.ptr.foundry.ontology.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 领域空间实例 URL
 * 对应主库 ontology 数据源下的 ontology.ontology_instance_url 表：
 * 按（空间 + 对象 + 实例）唯一，一个实例至多一条 URL 记录。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@TableName(value = "ontology_instance_url")
public class OntologyInstanceUrl {

    /**
     * 主键自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属对象空间 id
     */
    private Integer ontologySpaceId;

    /**
     * 对象唯一标识，对应 ontology_meta.unique_identifier
     */
    private String ontologyUniqueIdentifier;

    /**
     * 实例 id（实例主键值，数字主键按字符串存）
     */
    private String instanceId;

    /**
     * 实例对应的 URL
     */
    private String url;

    /**
     * 存入时间；由 upsert SQL 取 CURRENT_TIMESTAMP，不接受请求传入
     */
    private Date createTime;
}
