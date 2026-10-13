package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionParamMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 函数编排运行期的函数定义读取入口。
 * <p>编排链路的原子函数节点在每次执行时都要拿到被引用函数的脚本与入参定义，
 * 这里统一封装查询逻辑（按 api 精确定位、入参按 {@code paramOrder} 排序），
 * 供 {@link CompositeFunctionComponent} 与 {@link CompositeChainRegistry} 复用。</p>
 * <p>不做额外缓存：函数定义可能随发布变化，而编排链路的注册/编译已经做了缓存，
 * 每次执行多一次主键查询的代价可以接受，也避免引入缓存失效问题。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeFunctionCatalog {

    private final FunctionMapper functionMapper;

    private final FunctionParamMapper functionParamMapper;

    /** 按函数 api 取函数定义，不存在返回 null */
    public Function findFunction(String functionApi) {
        if (functionApi == null || functionApi.trim().isEmpty()) {
            return null;
        }
        return functionMapper.selectOne(Wrappers.<Function>lambdaQuery()
                .eq(Function::getApi, functionApi.trim())
                .last("limit 1"));
    }

    /** 按函数 id 取入参定义（按 paramOrder 升序，与 {@code executeFunction} 一致） */
    public List<FunctionParamPO> findInputParams(Long functionId) {
        if (functionId == null) {
            return new ArrayList<>();
        }
        List<FunctionParamPO> params = functionParamMapper.selectList(Wrappers.<FunctionParamPO>lambdaQuery()
                .eq(FunctionParamPO::getFunctionId, functionId)
                .eq(FunctionParamPO::getCategory, FunctionParamCategoryEnum.INPUT));
        return sortByOrder(params);
    }

    /** 按 paramOrder 升序排序，未设置顺序的参数排在最后 */
    static List<FunctionParamPO> sortByOrder(List<FunctionParamPO> params) {
        if (params == null) {
            return new ArrayList<>();
        }
        // 复制后排序：调用方可能传入 List.of(...) 等不可变集合，且不应被就地改写
        List<FunctionParamPO> sorted = new ArrayList<>(params);
        sorted.sort(Comparator.comparing(param -> param.getParamOrder() == null ? Integer.MAX_VALUE : param.getParamOrder()));
        return sorted;
    }

    /** 按函数 id 取指定类别的参数定义 */
    public List<FunctionParamPO> findParams(Long functionId, FunctionParamCategoryEnum category) {
        if (functionId == null) {
            return new ArrayList<>();
        }
        List<FunctionParamPO> params = functionParamMapper.selectList(Wrappers.<FunctionParamPO>lambdaQuery()
                .eq(FunctionParamPO::getFunctionId, functionId)
                .eq(FunctionParamPO::getCategory, category));
        return sortByOrder(params);
    }

    /**
     * 画布端口类型标识转函数参数类型，与 {@link #toCanvasType} 互逆。
     * <p>编排对外的输入/输出参数按此映射落 {@code function_param}，未知类型按 OBJECT 处理，
     * 保证编排函数与自定义函数在执行入口上参数转换行为一致。</p>
     *
     * @param canvasType 画布类型：string/number/boolean/object/array
     * @return 函数参数类型
     */
    public static FunctionParamTypeEnum toParamType(String canvasType) {
        if (canvasType == null || canvasType.trim().isEmpty()) {
            return FunctionParamTypeEnum.OBJECT;
        }
        return switch (canvasType.trim().toLowerCase()) {
            case "string" -> FunctionParamTypeEnum.STRING;
            case "number", "integer", "int", "float", "double", "long" -> FunctionParamTypeEnum.DOUBLE;
            case "boolean", "bool" -> FunctionParamTypeEnum.BOOL;
            case "array", "list" -> FunctionParamTypeEnum.LIST;
            default -> FunctionParamTypeEnum.OBJECT;
        };
    }

    /** 画布端口类型标识，与原型 {@code CompositeParameterType} 对齐 */
    public static String toCanvasType(FunctionParamTypeEnum paramType) {
        if (paramType == null) {
            return "string";
        }
        return switch (paramType) {
            case LIST -> "array";
            case OBJECT -> "object";
            case BOOL -> "boolean";
            case INTEGER, LONG, FLOAT, DOUBLE, SHORT, BYTE, CHAR, DATE -> "number";
            case STRING -> "string";
        };
    }
}
