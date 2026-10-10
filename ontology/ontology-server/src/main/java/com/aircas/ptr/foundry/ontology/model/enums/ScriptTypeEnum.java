package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.Getter;

@Getter
public enum ScriptTypeEnum {

    GROOVY(".groovy", "groovy-generic"),
    PYTHON(".py", "python"),
    TYPESCRIPT(".ts", "typescript");

    private final String fileExtension;
    private final String rulesDirectory;

    ScriptTypeEnum(String fileExtension, String rulesDirectory) {
        this.fileExtension = fileExtension;
        this.rulesDirectory = rulesDirectory;
    }

    public String getSourceFileName() {
        return "source" + fileExtension;
    }
}
