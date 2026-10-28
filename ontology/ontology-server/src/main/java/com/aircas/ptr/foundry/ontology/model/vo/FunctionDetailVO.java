package com.aircas.ptr.foundry.ontology.model.vo;


import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerOperatorConfig;
import com.aircas.ptr.foundry.ontology.model.dto.scan.FunctionScanResult;
import com.aircas.ptr.foundry.ontology.model.enums.ContainerPhaseEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionRuntimeStatusEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScanStatus;
import com.aircas.ptr.foundry.ontology.model.param.BasicQueryConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeConfig;
import com.aircas.ptr.foundry.ontology.model.param.ExternalApiConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "函数详细信息VO")
public class FunctionDetailVO {

    @Schema(name = "functionApi", description = "函数api", requiredMode = Schema.RequiredMode.REQUIRED)
    private String functionApi;

    @Schema(name = "version", description = "版本号，格式 x.y.z", requiredMode = Schema.RequiredMode.REQUIRED)
    private String version;

    @Schema(name = "publishStatus", description = "发布状态：0 未发布，1 已发布", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer publishStatus;

    @Schema(name = "scanStatus", description = "安全扫描状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private ScanStatus scanStatus;

    @Schema(name = "scanResult", description = "完整扫描结果，包含固定 11 个顶层字段及 findings",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private FunctionScanResult scanResult;

    @Schema(name = "displayName", description = "函数名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;

    @Schema(name = "description", description = "描述")
    private String description;

    @Schema(name = "model", description = "函数模型")
    private FunctionModelEnum model;

    @Schema(name = "type", description = "函数类型")
    private FunctionTypeEnum type;

    @Schema(name = "language", description = "脚本语言（仅 CUSTOMIZE 有意义）")
    private FunctionLanguageEnum language;

    @Schema(name = "ontologySpaceId", description = "所属本体空间 id")
    private Integer ontologySpaceId;

    @Schema(name = "createTime", description = "创建时间")
    private Date createTime;

    @Schema(name = "updateTime", description = "更新时间")
    private Date updateTime;

    @Schema(name = "runningStatus", description = "运行状态：RUNNING / NOT_RUNNING")
    private FunctionRuntimeStatusEnum runningStatus;

    @Schema(name = "containerKey", description = "容器算子实例键")
    private String containerKey;

    @Schema(name = "containerPhase", description = "容器运行阶段")
    private ContainerPhaseEnum containerPhase;


    @Schema(name = "params", description = "函数参数")
    private List<FunctionParameterVO> params;

    @Schema(name = "code", description = "函数代码：只有 CUSTOMIZE 类型会存在")
    private String code;

    @Schema(name = "referenceName", description = "函数全限定名称：只有 EXTERNAL 类型会存在")
    private String referenceName;

    @Schema(name = "queryConfig", description = "基础查询算子配置：只有 BASIC_QUERY 类型会存在")
    private BasicQueryConfig queryConfig;

    @Schema(name = "compositeConfig", description = "函数编排配置：只有 COMPOSITE 类型会存在")
    private CompositeConfig compositeConfig;

    @Schema(name = "apiConfig", description = "外部API算子配置：只有 EXTERNAL 类型会存在")
    private ExternalApiConfig apiConfig;

    @Schema(name = "dockerConfig", description = "容器算子配置（镜像/接口文档/接口列表）：只有 CONTAINER 类型会存在")
    private ContainerOperatorConfig dockerConfig;

}
