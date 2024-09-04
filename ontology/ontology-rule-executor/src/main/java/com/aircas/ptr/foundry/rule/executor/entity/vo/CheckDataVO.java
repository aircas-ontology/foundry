package com.aircas.ptr.foundry.rule.executor.entity.vo;

import lombok.Data;

import java.util.Map;

@Data
public class CheckDataVO {

    String db;

    String table;

    private Map<String,Object> data;
}
