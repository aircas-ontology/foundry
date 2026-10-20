package com.aircas.ptr.foundry.ontology.model.dto;

import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.List;

/**
 * 实例关系边导出结构（图库 relation 边上非 DELETE 的真实关系）。
 *
 * <p>导入后实例主键会由数据库重新生成，故这里记录的是导出时的主键值，导入时据此映射到新主键；
 * 主键统一用字符串表达，避免 Integer/Long 之间的类型漂移。</p>
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class EntityRelationExportDTO {

    /**
     * 开始实体主键值（导出时）
     */
    private String entityPrimaryKeyFrom;

    /**
     * 结束实体主键值（导出时）
     */
    private String entityPrimaryKeyTo;

    /**
     * 边状态：ENABLE 或 DISABLE
     */
    private Status status;

    /**
     * 可见窗口开始时间
     */
    private Date startTime;

    /**
     * 可见窗口结束时间
     */
    private Date endTime;

    /**
     * 可见窗口列表
     */
    private List<VisibilityWindow> timeWindows;
}
