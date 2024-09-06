package com.aircas.ptr.api.common.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sun.jna.WString;
import lombok.Data;

import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

@Data
public class ApiTaskResultInfo {

    private Long id;

    private String taskId;

    private String result;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date createTime;
}
