package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.BasicQueryTestParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionTestParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionExecuteResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityPropertyGenericQueryVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public interface FunctionService extends IService<Function> {


    Page<FunctionInfoVO> getFunctions(Integer ontologySpaceId, String displayName,
                                       FunctionTypeEnum type,
                                       Integer publishStatus,
                                       String startDate, String endDate,
                                       Integer pageNum, Integer pageSize);

    String executeFunction(FunctionExecuteParam param);


    /**
     * 按 api + 版本号查函数详情；version 为空时取该 api 的最新版本
     */
    FunctionDetailVO getFunctionDetailByApi(String api, String version);

    /**
     * 兼容旧调用：默认取该 api 的最新版本
     */
    default FunctionDetailVO getFunctionDetailByApi(String api) {
        return getFunctionDetailByApi(api, null);
    }

    /**
     * 按 api + 版本号删除函数（version 必填，精确定位）。仅未发布状态可删除
     */
    void deleteByApi(String api, String version);

    /**
     * 创建函数版本：新版本号必须大于同 api 下已有最大版本号，初始为未发布状态
     */
    void createFunction(FunctionCreateParam param);

    /**
     * 修改函数版本：仅未发布且未被行为关联的版本可原地更新；
     * 已发布版本一律禁止修改（需先下线），需要新版本请走创建接口
     */
    void updateFunction(FunctionUpdateParam param);

    /**
     * 发布：未发布 → 已发布，发布后不可修改/删除
     */
    void publishFunction(String api, String version);

    /**
     * 下线：已发布 → 未发布，下线后才可修改/删除
     */
    void unpublishFunction(String api, String version);

    FunctionExecuteResultVO getExecuteResult(String taskId);

    void callback(FunctionResultVO result);

    /**
     * 基础查询算子测试执行：将变量占位符替换为实际属性后调用 genericQuery。
     * 聚合模式返回 BasicQueryResultVO，query 模式返回分页结果。
     */
    Object testBasicQuery(BasicQueryTestParam param);

    /**
     * 统一函数测试入口：根据函数类型分发到不同测试逻辑。
     */
    Object testFunction(FunctionTestParam param);

    /**
     * 按 function api 批量取函数描述（每个 api 取最新版本），供行为出参冗余函数描述使用（一次查询，避免逐条查库）。
     * <p>
     * api 不存在或描述为空的不会出现在返回结果中。
     *
     * @param functionApis 函数 api 列表，允许为空
     * @return {@code functionApi -> description} 映射；无数据时返回空 Map，不返回 null
     */
    Map<String, String> mapDescriptionByApi(List<String> functionApis);
}
