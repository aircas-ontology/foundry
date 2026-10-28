package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.aircas.ptr.foundry.ontology.model.bo.CompositePlanBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeValidationBO;
import com.aircas.ptr.foundry.ontology.model.param.BasicQueryTestParam;
import com.aircas.ptr.foundry.ontology.model.param.CompositeValidateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionListQueryParam;
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


    Page<FunctionInfoVO> getFunctions(FunctionListQueryParam param);

    String executeFunction(FunctionExecuteParam param);


    /**
     * 按 空间 + api + 版本号 查函数详情；version 为空时取该空间内该 api 的最新版本。
     * <p>spaceId 为空表示不限空间（兼容旧调用）；同 api 在同空间内才唯一，跨空间允许重名。</p>
     */
    FunctionDetailVO getFunctionDetailByApi(Integer spaceId, String api, String version);

    /**
     * 兼容旧调用：不限空间
     */
    default FunctionDetailVO getFunctionDetailByApi(String api, String version) {
        return getFunctionDetailByApi(null, api, version);
    }

    /**
     * 兼容旧调用：默认取该 api 的最新版本
     */
    default FunctionDetailVO getFunctionDetailByApi(String api) {
        return getFunctionDetailByApi(null, api, null);
    }

    /**
     * 按 spaceId + function api 查询其全部版本号，按语义化版本倒序排列（最新在前）；
     * spaceId 为空表示不限空间，api 不存在时返回空列表
     */
    List<String> listVersionsByApi(Integer spaceId, String api);

    default List<String> listVersionsByApi(String api) {
        return listVersionsByApi(null, api);
    }

    /**
     * 按 空间 + api + 版本号删除函数（version 必填，精确定位）。仅未发布状态可删除
     */
    void deleteByApi(Integer spaceId, String api, String version);

    default void deleteByApi(String api, String version) {
        deleteByApi(null, api, version);
    }

    /**
     * 多节点部署下，删除容器算子最后一个接口后，归属节点本地执行的清理：
     * 停止 docker 容器、删除镜像 tar 与接口文档磁盘文件、删除实例记录。
     * 由清理转发端点调用，不再做二次转发。
     */
    void cleanupContainerLocal(String containerKey);

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
    void publishFunction(Integer spaceId, String api, String version);

    default void publishFunction(String api, String version) {
        publishFunction(null, api, version);
    }

    /**
     * 下线：已发布 → 未发布，下线后才可修改/删除
     */
    void unpublishFunction(Integer spaceId, String api, String version);

    default void unpublishFunction(String api, String version) {
        unpublishFunction(null, api, version);
    }

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
     * 校验函数编排定义（不落库），供前端画布实时校验使用。
     *
     * @param param 校验请求，包含编排定义
     * @return 校验结果，含错误与告警文案
     */
    CompositeValidationBO validateComposite(CompositeValidateParam param);

    /**
     * 编译已落库的函数编排定义，返回 LiteFlow 执行链 EL 与逐节点运行期定义（不注册执行链）。
     *
     * @param functionApi 编排函数 api
     * @return 编译结果
     */
    CompositePlanBO compileComposite(String functionApi);

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
