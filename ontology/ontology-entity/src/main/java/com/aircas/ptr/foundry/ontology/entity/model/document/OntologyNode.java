package com.aircas.ptr.foundry.ontology.entity.model.document;

import com.arangodb.springframework.annotation.ArangoId;
import com.arangodb.springframework.annotation.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import java.util.Date;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OntologyNode {
    @Id
    private String id;
    
    @ArangoId
    private String key;
    
    private String name;                    // 节点名称
    private String description;             // 节点描述
    private String type;                    // 节点类型
    private String category;                // 节点分类
    private Map<String, Object> properties; // 动态属性
    private String source;                  // 数据来源
    private String status;                  // 节点状态
    private Date createTime;                // 创建时间
    private Date updateTime;                // 更新时间
    private String createBy;                // 创建人
    private String updateBy;                // 更新人
    private Integer version;                // 版本号
    private String remarks;                 // 备注
    private Boolean isDeleted;              // 是否删除
    

} 