package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.ontology.constant.FunctionConstant;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.composite.CompositeFunctionCatalog;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScanSkipReason;
import com.aircas.ptr.foundry.ontology.model.enums.ScanStatus;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;

import java.util.Date;

/**
 * 算子（函数）记录创建的公共默认约定工厂。
 * <p>各类算子（自定义脚本 / 基础查询 / 外部API / 函数编排 / 容器）落 {@code function} 表时，
 * 「未发布 + 启用」这两个平台级默认值必须一致。历史上统一创建
 * （{@code FunctionServiceImpl.createFunction}）与容器创建
 * （{@code ContainerFunctionServiceImpl.createContainerFunction}）各抄了一份 builder，
 * 约定漂移风险大。此处收敛为单一入口：调用方拿到预置好的 builder，再补各自专有字段
 * （code / dockerConfig / language / containerKey / description 等）。</p>
 */
public final class OperatorFunctionFactory {

    private OperatorFunctionFactory() {
    }

    /**
     * 预置算子行的公共默认：{@code publish = 未发布}、{@code status = 启用}，
     * 并生成类型匹配的完整跳过扫描结果。阶段一尚未接入远端扫描，CUSTOMIZE
     * 暂按默认关闭策略写入 FEATURE_DISABLED；后续扫描编排层会在 insert 前覆盖为
     * 本次真实结果或相同的关闭结果。非脚本算子始终为 NON_SCRIPT_OPERATOR。
     * 其余随类型而异的字段由调用方在返回的 builder 上继续链式设置。
     *
     * @param api             算子 api
     * @param version         版本号
     * @param displayName     展示名
     * @param type            算子类型
     * @param ontologySpaceId 所属本体空间 id
     * @param model           函数模型（如 BASIC / OTHER；调用方按各自规则推断后传入）
     * @return 预置好公共约定的 Function builder
     */
    public static Function.FunctionBuilder operatorDefaults(String api, String version, String displayName,
                                                            FunctionTypeEnum type, Integer ontologySpaceId,
                                                            FunctionModelEnum model) {
        ScanSkipReason skipReason = type == FunctionTypeEnum.CUSTOMIZE
                ? ScanSkipReason.FEATURE_DISABLED
                : ScanSkipReason.NON_SCRIPT_OPERATOR;
        return Function.builder()
                .api(api)
                .version(version)
                .displayName(displayName)
                .type(type)
                .ontologySpaceId(ontologySpaceId)
                .model(model)
                .publish(FunctionConstant.PUBLISH_UNPUBLISHED)
                .status(Status.ENABLE.getValue())
                .scanStatus(ScanStatus.SKIPPED)
                .scanResult(FunctionScanResultFactory.skipped(skipReason));
    }

    /**
     * 预置算子出入参（{@code function_param}）行的公共骨架：外键、参数名、由原始类型
     * 映射出的 {@code paramType}、入/出分类、顺序、描述与时间戳。
     * <p>各类算子（自定义 / 基础查询 / 外部API / 编排 / 容器）落参数时这套骨架完全一致，
     * 过去在 {@code FunctionServiceImpl} 与 {@code ContainerFunctionServiceImpl} 各抄一遍，
     * 收敛到此处单点。类型映射统一走 {@link CompositeFunctionCatalog#toParamType}，
     * 保证「数值→DOUBLE」等平台约定不再各写各的。需要额外列（如 {@code paramSchema}/
     * {@code fields}）的调用方在返回的 builder 上继续链式设置。</p>
     *
     * @param functionId  所属函数 id
     * @param paramName   参数名
     * @param rawType     原始类型标识（string/number/integer/array...，按平台约定映射）
     * @param category    入参/出参分类
     * @param paramOrder  参数顺序
     * @param description 参数描述
     * @return 预置好公共列的 FunctionParamPO builder
     */
    public static FunctionParamPO.FunctionParamPOBuilder functionParamDefaults(
            Long functionId, String paramName, String rawType,
            FunctionParamCategoryEnum category, int paramOrder, String description) {
        return FunctionParamPO.builder()
                .functionId(functionId)
                .paramName(paramName)
                .paramType(CompositeFunctionCatalog.toParamType(rawType))
                .category(category)
                .paramOrder(paramOrder)
                .description(description)
                .createTime(new Date())
                .updateTime(new Date());
    }
}
