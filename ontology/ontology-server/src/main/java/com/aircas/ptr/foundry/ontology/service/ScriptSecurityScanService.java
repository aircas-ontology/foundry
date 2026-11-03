package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.dto.scan.FunctionScanResult;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScanPurpose;

/** 扫描跳过策略、语言映射和 fail-closed 业务判定。 */
public interface ScriptSecurityScanService {

    boolean isEnabled();

    FunctionScanResult scan(FunctionTypeEnum functionType, FunctionLanguageEnum language,
                            String code, ScanPurpose purpose);
}
