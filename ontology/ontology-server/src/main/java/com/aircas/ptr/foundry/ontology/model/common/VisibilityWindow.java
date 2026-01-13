package com.aircas.ptr.foundry.ontology.model.common;

import lombok.*;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class VisibilityWindow {

    private Date startTime;

    private Date endTime;

}