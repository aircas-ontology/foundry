package com.aircas.ptr.foundry.ontology.model.dto.scan;

import com.aircas.ptr.foundry.ontology.model.enums.ScanSeverity;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** 扫描 finding 四项固定摘要。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ScanSummary(int total, int high, int medium, int low) {

    public static ScanSummary empty() {
        return new ScanSummary(0, 0, 0, 0);
    }

    public static ScanSummary from(List<ScanFinding> findings) {
        int high = 0;
        int medium = 0;
        int low = 0;
        for (ScanFinding finding : findings) {
            if (finding.severity() == ScanSeverity.HIGH) {
                high++;
            } else if (finding.severity() == ScanSeverity.MEDIUM) {
                medium++;
            } else if (finding.severity() == ScanSeverity.LOW) {
                low++;
            }
        }
        return new ScanSummary(findings.size(), high, medium, low);
    }
}
