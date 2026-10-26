package com.aircas.ptr.foundry.ontology.model.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 算子调用与限流配置模板：创建函数时选定，配置快照写入 function 表，模板后续修改不影响历史函数。
 */
@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "function_rate_limit_template")
public class FunctionRateLimitTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板名称（如"宽松"、"标准"、"严格"） */
    private String name;

    /** 超时时间（毫秒） */
    private Integer timeoutMs;

    /** 重试次数（0 表示不重试） */
    private Integer retryCount;

    /** 重试间隔（毫秒） */
    private Integer retryIntervalMs;

    /** TPS 限流（每秒最大调用数） */
    private Integer tpsLimit;

    /** 最大并发数 */
    private Integer maxConcurrency;

    /** 是否启用限流（0 禁用 / 1 启用） */
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
