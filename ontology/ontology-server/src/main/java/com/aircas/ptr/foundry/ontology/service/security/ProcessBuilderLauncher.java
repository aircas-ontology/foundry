package com.aircas.ptr.foundry.ontology.service.security;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class ProcessBuilderLauncher implements ProcessLauncher {

    @Override
    public Process start(List<String> command) throws IOException {
        return new ProcessBuilder(command).start();
    }
}
