package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
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
 * 函数算子代码模板：按脚本语言维护示例代码，创建脚本算子时选用带出初始代码。
 */
@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "function_operator_template")
@Schema(description = "函数算子代码模板")
public class FunctionOperatorTemplate {

    @TableId(type = IdType.AUTO)
    @Schema(description = "模板 ID")
    private Long id;

    @Schema(description = "模板名称（全局唯一）")
    private String name;

    @Schema(description = "脚本语言：GROOVY/JAVA/PYTHON/TYPESCRIPT")
    private FunctionLanguageEnum language;

    @Schema(description = "模板说明")
    private String description;

    @Schema(description = "模板代码内容")
    private String code;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private Date updateTime;
}
