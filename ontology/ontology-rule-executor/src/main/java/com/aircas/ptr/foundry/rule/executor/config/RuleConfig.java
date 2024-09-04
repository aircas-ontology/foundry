package com.aircas.ptr.foundry.rule.executor.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class RuleConfig {

    @Value(value = "${ontology.server}")
    private String ontologyServerRemote;
}
