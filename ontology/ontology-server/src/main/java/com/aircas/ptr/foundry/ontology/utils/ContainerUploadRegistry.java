package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.ontology.model.po.FunctionContainerUpload;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionContainerUploadMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 容器算子上传文件归属登记：记下「这个文件落在我这台机器的磁盘上」。
 * <p>上传镜像/接口文档的 multipart 请求本身不做跨节点转发（流式转发不在范围内），它永远落在负载均衡选中的节点；
 * 而后续的「探查接口」只带 docPath、「创建算子」只带镜像 path，无从得知文件在哪台机器，
 * 只能靠这张按绝对路径索引的登记表把请求指回归属节点。</p>
 * <p>单节点模式（未配 {@code container-operator.node.ip}）下整套登记都不执行；
 * 登记本身的异常只记日志，绝不影响已经落盘的上传——最坏退化成单节点行为，而不是把上传打成失败。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerUploadRegistry {

    private final FunctionContainerUploadMapper uploadMapper;
    private final ContainerNodeIdentity identity;

    /** 登记文件归属为本节点；单节点模式空操作 */
    public void record(String filePath, String kind) {
        if (!identity.isMultiNode() || StringUtils.isBlank(filePath)) {
            return;
        }
        try {
            // 同一路径重复登记（重试/重传）以最后一次为准：先删后插，避开 file_path 唯一索引冲突
            uploadMapper.delete(new LambdaQueryWrapper<FunctionContainerUpload>()
                    .eq(FunctionContainerUpload::getFilePath, filePath));
            uploadMapper.insert(FunctionContainerUpload.builder()
                    .filePath(filePath)
                    .kind(kind)
                    .nodeIp(identity.nodeIp())
                    .nodePort(identity.nodePort())
                    .createTime(new Date())
                    .build());
        } catch (Exception e) {
            log.warn("容器算子上传文件归属登记失败（{} {}），后续探查/创建将按本节点处理：{}", kind, filePath, e.getMessage());
        }
    }

    /** 按文件路径反查归属节点；单节点模式或查不到返回 null（调用方按本节点处理） */
    public FunctionContainerUpload findOwner(String filePath) {
        if (!identity.isMultiNode() || StringUtils.isBlank(filePath)) {
            return null;
        }
        try {
            return uploadMapper.selectOne(new LambdaQueryWrapper<FunctionContainerUpload>()
                    .eq(FunctionContainerUpload::getFilePath, filePath));
        } catch (Exception e) {
            log.warn("查询容器算子上传文件归属失败（{}），按本节点处理：{}", filePath, e.getMessage());
            return null;
        }
    }

    /** 删除登记（算子创建成功后，容器级归属已固化到 function_container_instance，登记行不再需要） */
    public void remove(String... filePaths) {
        if (!identity.isMultiNode() || filePaths == null || filePaths.length == 0) {
            return;
        }
        try {
            for (String filePath : filePaths) {
                if (StringUtils.isBlank(filePath)) {
                    continue;
                }
                uploadMapper.delete(new LambdaQueryWrapper<FunctionContainerUpload>()
                        .eq(FunctionContainerUpload::getFilePath, filePath));
            }
        } catch (Exception e) {
            log.warn("清理容器算子上传文件登记失败（不影响已创建的算子）：{}", e.getMessage());
        }
    }
}
