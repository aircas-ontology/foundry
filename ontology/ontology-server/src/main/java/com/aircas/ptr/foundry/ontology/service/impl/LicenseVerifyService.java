package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.license.param.LicenseCreatorParam;
import com.aircas.ptr.foundry.license.service.LicenseService;
import com.aircas.ptr.foundry.license.util.NetworkUtil;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LicenseVerifyService implements ApplicationRunner {


    @Value("${license.enable:false}")
    private Boolean enableVerify;

    @Value("${license.licensePath}")
    private String licensePath;

    @Value("${license.publicKeyStorePath}")
    private String publicKeyStorePath;

    @Value("${license.storePass}")
    private String storePass;

    @Value("${license.alias}")
    private String alias;

    @Value("${license.subject}")
    private String subject;

    @Override
    public void run(ApplicationArguments args) {

        if (!enableVerify) {
            log.info("未开启许可证认证");
            return;
        }

        log.info("开始验证许可证");
        var param = LicenseCreatorParam.builder()
                .subject(subject)
                .alias(alias)
                .licenseCheckModel(NetworkUtil.getNetworkInfo())
                .licensePath(licensePath)
                .storePass(storePass)
                .keysStorePath(publicKeyStorePath)
                .build();
        var licenseService = new LicenseService();
        var res = licenseService.verifyLicense(param);
        if (res) {
            log.info("验证许可证成功");
        } else {
            log.error("license验证失败");
            System.exit(1);
        }
    }

}
