package com.aircas.ptr.foundry.license.service;

import com.aircas.ptr.foundry.license.param.LicenseCheckModel;
import com.aircas.ptr.foundry.license.param.LicenseCreatorParam;
import com.aircas.ptr.foundry.license.util.NetworkUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.schlichtherle.license.*;
import lombok.SneakyThrows;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Date;
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

        System.out.println("生成 license 成功, 文件名:" + param.getLicensePath());
    }

    public boolean verifyLicense(LicenseCreatorParam param) {
        try {
            var LicenseParam = buildLicenseParam(param);
            var licenseManager = new LicenseManager(LicenseParam);

            var licenseFile = new File(param.getLicensePath());
            if (!licenseFile.exists()) {
                throw new FileNotFoundException("许可证文件不存在:" + param.getLicensePath());
            }
            licenseManager.install(licenseFile);
            var content = licenseManager.verify();

            // 从 LicenseContent 中提取 IP 地址和 MAC 地址
            var checkModel = objectMapper.readValue(content.getExtra().toString(), LicenseCheckModel.class);
            // 获取真实ip和mac地址
            var actualCheckModel = NetworkUtil.getNetworkInfo();

            // 校验 IP 地址
            if (checkModel.getIpAddress() != null && !checkModel.getIpAddress().isEmpty()) {
                boolean ipMatch = checkModel.getIpAddress().stream().anyMatch(actualCheckModel.getIpAddress()::contains);
                if (!ipMatch) {
                    throw new SecurityException("IP 地址不匹配");
                }
            }

            // 校验 MAC 地址
            if (checkModel.getMacAddress() != null && !checkModel.getMacAddress().isEmpty()) {
                boolean macMatch = checkModel.getMacAddress().stream().anyMatch(actualCheckModel.getMacAddress()::contains);
                if (!macMatch) {
                    throw new SecurityException("MAC 地址不匹配");
                }
            }

            // 校验证书有效性
            var begin = content.getNotBefore();
            var end = content.getNotAfter();
            var curDate = new Date();
            if (curDate.before(begin) || curDate.after(end)) {
                throw new SecurityException("证书已过期");
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

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
