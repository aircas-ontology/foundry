package com.aircas.ptr.foundry.license;

import com.aircas.ptr.foundry.license.param.LicenseCreatorParam;
import com.aircas.ptr.foundry.license.service.LicenseService;
import com.aircas.ptr.foundry.license.util.NetworkUtil;
import lombok.var;

import java.util.Date;

public class LicenseApplication {

    public static final long EXPIRE_TIME = 3600L * 1000 * 24 * 365 * 10;


    private static final String SUBJECT = "aircas";

    private static final String PASSWORD = "iecas123";

    private static final String PRIVATE_ALIAS = "privateKey";

    private static final String PRIVATE_KEYS_STORE_PATH = "/privateKeys.keystore";


    public static void main(String[] args) {

        var licenseService = new LicenseService();
        licenseService.generateLicense(LicenseCreatorParam.builder()
                .subject(SUBJECT)
                .alias(PRIVATE_ALIAS)
                .keyPass(PASSWORD)
                .storePass(PASSWORD)
                .licensePath("license.lic")
                .keysStorePath(PRIVATE_KEYS_STORE_PATH)
                .issuedTime(new Date())
                .expiryTime(new Date(System.currentTimeMillis() + EXPIRE_TIME))
                .licenseCheckModel(NetworkUtil.getNetworkInfo())
                .build());
    }

}
