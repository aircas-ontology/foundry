package com.aircas.ptr.foundry.license.service;

import com.aircas.ptr.foundry.license.param.LicenseCreatorParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.schlichtherle.license.*;
import lombok.SneakyThrows;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.util.prefs.Preferences;


public class LicenseService {


    private final ObjectMapper objectMapper = new ObjectMapper();

    private final X500Principal DEFAULT_ISSUER_AND_HOLDER = new X500Principal("CN=aircas, OU=RD, O=aircas, L=Beijing, ST=Beijing, C=CN");


    @SneakyThrows
    public void generateLicense(LicenseCreatorParam param) {

        System.out.println("开始生成 license:" + param);

        LicenseParam licenseParam = buildLicenseParam(param);

        LicenseManager licenseManager = new LicenseManager(licenseParam);

        LicenseContent content = buildLicenseContent(param);

        File licenseFile = new File(param.getLicensePath());

        licenseManager.store(content, licenseFile);

        System.out.println("生成 license 成功, 文件名:"+ param.getLicensePath());
    }

    public LicenseParam buildLicenseParam(LicenseCreatorParam param) {


        Preferences preference = Preferences.userNodeForPackage(LicenseManager.class);

        CipherParam cipherParam = new DefaultCipherParam(param.getStorePass());


        KeyStoreParam privateStoreParam = new DefaultKeyStoreParam(
                LicenseService.class,
                param.getKeysStorePath(),
                param.getAlias(),
                param.getStorePass(),
                param.getKeyPass());


        LicenseParam licenseParams = new DefaultLicenseParam(param.getSubject(), preference, privateStoreParam, cipherParam);

        return licenseParams;

    }

    @SneakyThrows
    private LicenseContent buildLicenseContent(LicenseCreatorParam param) {
        LicenseContent content = new LicenseContent();
        content.setSubject(param.getSubject());
       // content.setIssuer(DEFAULT_ISSUER_AND_HOLDER);
        content.setIssued(param.getIssuedTime());
        content.setNotBefore(param.getIssuedTime());
        content.setNotAfter(param.getExpiryTime());
        content.setInfo(param.getDescription());
      //  content.setHolder(DEFAULT_ISSUER_AND_HOLDER);

        if (param.getLicenseCheckModel() != null) {
            content.setExtra(objectMapper.writeValueAsString(param.getLicenseCheckModel()));
        }

        return content;
    }
}
