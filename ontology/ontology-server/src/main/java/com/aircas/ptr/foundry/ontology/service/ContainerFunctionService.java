package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerImageInfo;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterface;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterfaceDoc;
import com.aircas.ptr.foundry.ontology.model.param.ContainerFunctionCreateParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 容器算子注册服务：镜像上传（含 ARM 校验）、接口文档上传、接口探查、算子保存。
 * <p>本期只做元数据注册，不做容器部署与执行。</p>
 */
public interface ContainerFunctionService {

    /**
     * 上传镜像 tar/tar.gz：保存到镜像目录，按开关校验 ARM64 架构，返回镜像信息。
     */
    ContainerImageInfo uploadImage(MultipartFile file);

    /**
     * 上传接口文档 json/yaml/yml：保存到文档目录，返回文档信息（含格式）。
     */
    ContainerInterfaceDoc uploadDoc(MultipartFile file);

    /**
     * 探查已上传文档，返回接口列表（不落库）。
     */
    List<ContainerInterface> probeInterfaces(String docPath);

    /**
     * 保存容器算子（设计 C）：每个勾选接口各生成一条独立 function（type=CONTAINER, version=1.0.0），
     * 共用同一镜像与接口文档；api = baseApi + "_" + 接口 api，出入参按接口写入 function_param。
     *
     * @return 本批次生成的算子 api 列表
     */
    List<String> createContainerFunction(ContainerFunctionCreateParam param);
}
