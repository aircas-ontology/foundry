package com.aircas.ptr.foundry.ontology.model.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class JwtDTO {

    private String accessToken;

    private String refreshToken;

}
