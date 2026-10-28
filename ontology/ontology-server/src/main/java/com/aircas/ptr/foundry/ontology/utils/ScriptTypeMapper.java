package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.ontology.exception.SemgrepScanException;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScanErrorCode;
import com.aircas.ptr.foundry.ontology.model.enums.SemgrepScriptType;

/** 函数语言到扫描服务语言的唯一映射入口。 */
public final class ScriptTypeMapper {

    private ScriptTypeMapper() {
    }

    public static SemgrepScriptType toSemgrep(FunctionLanguageEnum language) {
        if (language == null) {
            throw new SemgrepScanException(ScanErrorCode.UNSUPPORTED_LANGUAGE);
        }
        return switch (language) {
            case JAVA, GROOVY -> SemgrepScriptType.GROOVY;
            case PYTHON -> SemgrepScriptType.PYTHON;
            case TYPESCRIPT -> SemgrepScriptType.TYPESCRIPT;
        };
    }
}
