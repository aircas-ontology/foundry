package com.aircas.ptr.foundry.topic.model.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("upload_file")
public class UploadFile {

    /**
     * 文件编号，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 关联本体空间编号，多个用逗号分割
     */
    private String ontologySpace;

    /**
     * 关联本体空间名称
     */
    private String ontologySpaceName;

    /**
     * 文件上传路径。网盘上传后续补充。
     */
    private String uploadPath;

    /**
     * 抽取进度
     */
    private Integer extractProgress;

    /**
     * 上传人员
     */
    private String uploader;

    /**
     * 抽取时间
     */
    private Date extractTime;

    /**
     * 是否删除：0未删除，1已删除
     */
    private Integer deleted;

    /**
     * 文件内容。图片暂不识别。
     */
    private String context;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
