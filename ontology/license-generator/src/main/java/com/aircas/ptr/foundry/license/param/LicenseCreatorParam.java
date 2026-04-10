package com.aircas.ptr.foundry.license.param;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class LicenseCreatorParam {

    /**
     * 证书主题
     */
    private String subject;

    /**
     * 别名
     */
    private String alias;

    /**
     * 密钥密码
     */
    private String keyPass;

    /**
     * 存储密码
     */
    private String storePass;

    /**
     * 证书路径
     */
    private String licensePath;


    /**
     * 私钥存储路径
     */
    private String keysStorePath;

    /**
     * 证书生效时间
     */
    private Date issuedTime;

    /**
     * 证书失效时间
     */
    private Date expiryTime;

    private String description;

    /**
     * 自定义校验模型
     */
    private LicenseCheckModel licenseCheckModel;
}
