package com.aircas.ptr.api.common.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;
import java.util.Date;

@Data
public class ApiTaskCommitInfo {

    public Long id;

    private String taskId;

    private String params;

    private String body;

    private String path;

    private int status;

    private String msg;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date updateTime;
}
