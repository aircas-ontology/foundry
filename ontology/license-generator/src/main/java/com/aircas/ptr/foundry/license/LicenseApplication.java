package com.aircas.ptr.foundry.license;

import com.aircas.ptr.foundry.license.param.LicenseCheckModel;
import com.aircas.ptr.foundry.license.param.LicenseCreatorParam;
import com.aircas.ptr.foundry.license.service.LicenseService;
import com.aircas.ptr.foundry.license.util.NetworkUtil;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class LicenseApplication {

    public static final long EXPIRE_TIME = 3600L * 1000 * 24 * 365 * 10;


    private static final String SUBJECT = "aircas";

    private static final String PASSWORD = "iecas123";

    private static final String PRIVATE_ALIAS = "privateKey";

    private static final String PRIVATE_KEYS_STORE_PATH = "/privateKeys.keystore";


    public static void main(String[] args) {

        LicenseCheckModel licenseCheckModel;
        /**
         * 手动输入参数：ip 和 mac地址
         */
        if (args.length == 2 && args[0].startsWith("ip=") && args[1].startsWith("mac=")) {

            var ipAddresses = args[0].substring(3);
            var macAddresses = args[1].substring(4);

            var ipAddress = Arrays.asList(ipAddresses.split(","));
            var macAddress = Arrays.asList(macAddresses.split(","));

            licenseCheckModel = LicenseCheckModel.builder()
                    .ipAddress(ipAddress)
                    .macAddress(macAddress)
                    .build();

        } else {
            licenseCheckModel = NetworkUtil.getNetworkInfo();
        }

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
                .licenseCheckModel(licenseCheckModel)
                .build());
    }

}
