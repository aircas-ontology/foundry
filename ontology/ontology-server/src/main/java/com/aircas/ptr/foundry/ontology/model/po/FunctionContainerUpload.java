package com.aircas.ptr.foundry.ontology.model.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 容器算子上传文件归属登记：镜像 tar / 接口文档只落在收到上传请求那台节点的本地磁盘上。
 * <p>探查接口（入参仅 docPath）与创建算子（入参仅镜像 path）都无从得知文件在哪台机器，
 * 故上传时按绝对路径登记归属节点，这两步据此反查并把请求自动转发到归属节点；创建成功后删除登记行。</p>
 */
@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "function_container_upload")
public class FunctionContainerUpload {

    /** 文件类型：镜像 tar */
    public static final String KIND_IMAGE = "image";

    /** 文件类型：OpenAPI 接口文档 */
    public static final String KIND_DOC = "doc";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上传后返回给前端的节点本地绝对路径（UUID 前缀，全局唯一） */
    private String filePath;

    /** 文件类型：{@link #KIND_IMAGE} / {@link #KIND_DOC} */
    private String kind;

    /** 实际保存该文件的 ontology-server 实例 IP */
    private String nodeIp;

    /** 该实例的 HTTP 端口 */
    private Integer nodePort;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
