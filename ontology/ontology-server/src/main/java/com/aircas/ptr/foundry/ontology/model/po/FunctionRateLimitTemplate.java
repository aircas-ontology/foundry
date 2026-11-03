package com.aircas.ptr.foundry.ontology.model.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "限流配置模板")
public class FunctionRateLimitTemplate {

    @TableId(type = IdType.AUTO)
    @Schema(description = "模板 ID")
    private Long id;

    @Schema(description = "模板名称（如\"宽松\"、\"标准\"、\"严格\"）")
    private String name;

    @Schema(description = "超时时间（毫秒）")
    private Integer timeoutMs;

    @Schema(description = "重试次数（0 表示不重试）")
    private Integer retryCount;

    @Schema(description = "重试间隔（毫秒）")
    private Integer retryIntervalMs;

    @Schema(description = "TPS 限流（每秒最大调用数）")
    private Integer tpsLimit;

    @Schema(description = "最大并发数")
    private Integer maxConcurrency;

    @Schema(description = "是否启用限流（0 禁用 / 1 启用）")
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private Date updateTime;
}
