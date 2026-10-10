package com.aircas.ptr.foundry.ontology.service.security;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SemgrepProcessResult {
    Integer exitCode;
    String stdout;
    String stderr;
    boolean timedOut;
    boolean outputTruncated;
    boolean overloaded;
    String failureSummary;
    long durationMillis;
}
