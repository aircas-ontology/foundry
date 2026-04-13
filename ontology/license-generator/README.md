
#Lisecnse Generator


## 1 生成公私钥对
keytool -genkeypair -keysize 1024 -alias privatekey -keyalg DSA -keystore privateKeys.keystore -storepass iecas123 -keypass iecas123 -validity 36500 -dname "CN=aircas, OU=RD, O=aircas, L=Beijing, ST=Beijing, C=CN"

将生成的privateKeys.keystore文件放到resources文件夹下

## 2 私钥导出成cer证书
keytool -exportcert -alias privatekey -keystore privateKeys.keystore -storepass iecas123 -file certfile.cer

将生成的certfile.cer文件放到resources文件夹下

## 3 生成公钥文件publicCerts.keystore
keytool  -import -alias publiccert -file certfile.cer -keystore publicCerts.keystore -storepass iecas123

将生成的publicCerts.keystore文件放到需要验证证书的客户端项目中

## 4 生成证书
mvn clean package license-generator对项目打包
运行jar包，手动传入目标服务器的ip和mac，逗号分割（如果不传入ip和mac，会自动读取宿主机的ip和mac地址）
例如 java -jar license-generator-0.0.1-SNAPSHOT.jar ip=192.168.1.1,192.168.1.2 mac=00:1A:2B:3C:4D:5E,00:1A:2B:3C:4D:5F或java -jar license-generator-0.0.1-SNAPSHOT.jar
运行完成后，会在当前目录下生成license.lic文件

## 5 证书验证
将license.lic证书文件放置到待验证的客户端项目中验证，调用com.aircas.ptr.foundry.license.service.LicenseService.verifyLicense进行验证。
以springboot项目验证证书为例：
```java
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
        LicenseCreatorParam param = LicenseCreatorParam.builder()
                .subject(subject)
                .alias(alias)
                .licenseCheckModel(NetworkUtil.getNetworkInfo())
                .licensePath(licensePath)
                .storePass(storePass)
                .keysStorePath(publicKeyStorePath)
                .build();
        LicenseService licenseService = new LicenseService();
        boolean res = licenseService.verifyLicense(param);
        if (res) {
            log.info("验证许可证成功");
        } else {
            log.error("license验证失败");
            System.exit(1);
        }
    }

}

