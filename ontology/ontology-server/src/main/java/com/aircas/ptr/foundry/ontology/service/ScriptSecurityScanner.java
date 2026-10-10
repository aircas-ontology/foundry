package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.dto.ScriptScanResultDTO;
import com.aircas.ptr.foundry.ontology.model.enums.ScriptTypeEnum;

public interface ScriptSecurityScanner {
    ScriptScanResultDTO scan(ScriptTypeEnum scriptType, String code);
}
