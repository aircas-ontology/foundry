package com.aircas.ptr.foundry.rule.executor.entity.dynamics;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CheckRuleStatus {

    private boolean status;

    private String primaryValue;

    private String dataLogId;
}
