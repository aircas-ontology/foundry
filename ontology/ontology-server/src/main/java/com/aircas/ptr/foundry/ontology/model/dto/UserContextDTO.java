package com.aircas.ptr.foundry.ontology.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContextDTO {

    private Integer userId;

    private String username;

    /**
     * 用户绑定的本体空间id，为空表示不限制空间
     */
    private Integer spaceId;

}
