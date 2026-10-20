package com.aircas.ptr.foundry.ontology.model.dto.container;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 容器算子镜像信息（上传镜像接口返回并回传保存）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContainerImageInfo {

    /** 原始文件名 */
    private String fileName;

    /** 服务器存储的绝对路径 */
    private String path;

    /** 文件大小（字节） */
    private Long size;

    /** 镜像架构（arm64 / amd64 等，docker save tar 内 config.architecture） */
    private String architecture;

    /** 镜像标签（manifest.json 的 RepoTags） */
    private List<String> repoTags;
}
