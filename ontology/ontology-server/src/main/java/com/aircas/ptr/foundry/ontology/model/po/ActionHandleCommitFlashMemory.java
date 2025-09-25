package com.aircas.ptr.foundry.ontology.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

@Data
@Entity
@TableName(value = "action_handle_commit_flash_memory")
public class ActionHandleCommitFlashMemory {

    @Id
    @Column(name = "id")
    private Long id;

    private String primaryKey;

    private Long actionId;

    private String tableName;

    private String data;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date updateTime;
}
