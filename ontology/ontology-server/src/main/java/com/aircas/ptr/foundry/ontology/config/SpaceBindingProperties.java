package com.aircas.ptr.foundry.ontology.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户与本体空间的绑定配置。
 * <p>
 * 配置了绑定的用户，查询空间列表、全局检索等入口只返回/检索该空间；未配置的用户不受限制。
 * 绑定关系属环境相关配置，key 在 application.yml 声明，value 写在 application-local.yml（本地维护，不提交）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ontology.space-binding")
public class SpaceBindingProperties {

    /**
     * 用户名（users.username，大小写敏感） → 绑定的本体空间id（ontology_space.id）
     */
    private Map<String, Integer> users = new HashMap<>();

    /**
     * 取用户绑定的空间id，未配置返回 null（表示不限制空间）。
     */
    public Integer resolveSpaceId(String username) {
        if (StringUtils.isBlank(username) || users == null) {
            return null;
        }
        return users.get(username);
    }
}
