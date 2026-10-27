package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Date;


@Data
@Entity
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "function")
public class Function {


    /**
     * 记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;


    /**
     * 记录更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * Column: api
     * 创建后不可修改：updateStrategy = NEVER 使生成的 UPDATE 语句永不包含 api 列
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String api;

    /**
     * Column: desc
     */
    private String description;

    /**
     * Column: displayName
     * 创建后不可修改：updateStrategy = NEVER 使生成的 UPDATE 语句永不包含 display_name 列
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String displayName;

    /**
     * Column: status
     */
    private Integer status;

    /**
     * 版本号，格式 x.y.z（如 1.0.0）；与 api 联合唯一，同一 api 下必须递增
     */
    private String version;

    /**
     * 发布状态：0 未发布（可修改/删除），1 已发布（锁定）
     *
     * @see com.aircas.ptr.foundry.ontology.constant.FunctionConstant
     */
    private Integer publish;

    /**
     * Column: id
     */
    @TableId(type = IdType.AUTO)
    private Long id;


    /**
     * 自定义函数类型才保存code
     */
    private String code;


    /**
     * 已存在函数类型才保存引入的函数全限定名称
     */
    private String referenceName;

    /**
     * FunctionTypeEnum
     */
    private FunctionTypeEnum type;

    /**
     * 脚本语言，仅 type=CUSTOMIZE 脚本算子有值，其余算子（基础查询/外部/容器）为 NULL；执行时按此路由到对应语言 runner 容器。
     * 创建后锁定（updateStrategy=NEVER），换语言请走新版本。
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private FunctionLanguageEnum language;

    private FunctionModelEnum model;

    /**
     * 涉及的本体
     */
    private String objectTypes;

    /**
     * 本体空间id
     */
    private Integer ontologySpaceId;

    /**
     * 容器算子关联的运行实例分组键（外键，= function_container_instance.container_key），仅 type=CONTAINER 有值。
     * 一容器 : 多接口算子 = 1:N：同一容器算子拆出的多条 function 共用同一 container_key。
     * 容器级配置（镜像/接口文档/启动命令/端口）与运行态均存于 function_container_instance，本列仅作为关联指针。
     */
    private String containerKey;

    /**
     * 容器算子接口级配置 JSON（仅 type=CONTAINER 有值）：只存选中接口的定义，
     * 结构见 {@link com.aircas.ptr.foundry.ontology.model.dto.container.ContainerOperatorConfig} 的 interfaces 字段
     */
    private String dockerConfig;

    /**
     * 选用的限流配置模板 ID。
     */
    private Long rateLimitTemplateId;

    /**
     * 限流配置快照 JSON（从模板复制，按 api+version 独立执行限流）。
     * 结构：{"timeoutMs":10000,"retryCount":2,"retryIntervalMs":500,"tpsLimit":50,"maxConcurrency":20,"enabled":1}
     */
    private String rateLimitConfig;
}