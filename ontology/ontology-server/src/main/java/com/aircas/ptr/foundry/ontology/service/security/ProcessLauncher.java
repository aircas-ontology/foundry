package com.aircas.ptr.foundry.ontology.service.security;

import java.io.IOException;
import java.util.List;

public interface ProcessLauncher {
    Process start(List<String> command) throws IOException;
}
