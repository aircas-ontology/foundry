package com.aircas.ptr.foundry.license.param;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class LicenseCheckModel {

    private List<String> ipAddress;

    private List<String> macAddress;


}
