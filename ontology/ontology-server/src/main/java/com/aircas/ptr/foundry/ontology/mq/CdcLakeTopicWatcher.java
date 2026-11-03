package com.aircas.ptr.foundry.ontology.mq;

import com.aircas.ptr.foundry.ontology.mq.consumer.EntityDatasourceCdcTopics;
import com.aircas.ptr.foundry.ontology.model.common.Constants;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 运行期发现数据湖新增表的 CDC topic。
 * <p>
 * 湖监听用 {@code topicPattern} 订阅（见 {@link EntityDatasourceCdcTopics}），pattern 只在容器启动时解析一次，
 * 湖里新建一张表产生的 topic 在运行期不会被订阅，该表在下次服务重启前的变更全部积在 topic 里没人消费。
 * 这里定时用 AdminClient 比对 pattern 命中的 topic 集合，一旦发现新 topic，就停起一次湖监听容器——
 * 容器重新启动会重新解析 pattern，配合 {@code auto-offset-reset=earliest}（新分区没有已提交 offset）
 * 把积压的变更一次性补回来，等价于"重启 java"里消费端做的那件事，不需要再重启整个服务。
 * <p>
 * 只处理消费端：若新表连 topic 都没建出来（Debezium 侧没捕获该表），本组件不会触发，那种情况仍需重启服务
 * 让 {@code CdcTaskBuilder} 重新下发连接器配置。
 * <p>
 * 由 xxl-job 调度（JobHandler = DISCOVER_CDC_LAKE_TOPIC），多实例部署下路由策略必须选「广播执行」：
 * 这里重启的是当前 JVM 内的监听容器，只路由到一台 executor 时其余实例的 pattern 依旧不会刷新，
 * 等于退回"新增表要重启服务"的老问题。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "foundry.integration.cdc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CdcLakeTopicWatcher {

    private static final Pattern TOPIC_PATTERN = Pattern.compile(EntityDatasourceCdcTopics.TOPIC_PATTERN);

    /** 单次 listTopics 的等待上限（秒） */
    private static final long LIST_TIMEOUT_SECONDS = 10;

    @Autowired
    private KafkaProperties kafkaProperties;
    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    /** 上一轮 pattern 命中的湖 topic 集合 */
    private Set<String> knownTopics = new HashSet<>();
    /** 首轮只记录基线，避免启动顺序被误判成"新增 topic" */
    private boolean primed;
    private AdminClient adminClient;

    @XxlJob(Constants.DISCOVER_CDC_LAKE_TOPIC_JOB_NAME)
    public void discoverNewLakeTopics() {
        Set<String> current = listLakeTopics();
        if (current == null) {
            return;
        }
        if (!primed) {
            primed = true;
            knownTopics = current;
            log.info("湖 CDC topic 基线: {} 个", current.size());
            return;
        }
        Set<String> newTopics = new TreeSet<>(current);
        newTopics.removeAll(knownTopics);
        knownTopics = current;
        if (newTopics.isEmpty()) {
            return;
        }
        MessageListenerContainer container = listenerRegistry.getListenerContainer(EntityDatasourceCdcTopics.LISTENER_ID);
        if (container == null) {
            log.warn("未找到湖监听容器 [{}]，新增 topic 不会被消费: {}", EntityDatasourceCdcTopics.LISTENER_ID, newTopics);
            return;
        }
        log.info("检测到数据湖新增 topic {}，重启湖监听容器以重新解析 topicPattern", newTopics);
        try {
            container.stop();
            container.start();
        } catch (Exception e) {
            log.error("重启湖监听容器失败，新增表的增量同步不会生效，需人工重启服务: {}", newTopics, e);
        }
    }

    /**
     * @return pattern 命中的湖 topic 名集合；取不到（Kafka 不可用/未配地址）返回 null，本轮跳过
     */
    private Set<String> listLakeTopics() {
        List<String> bootstrapServers = kafkaProperties.getBootstrapServers();
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            log.warn("未配置 spring.kafka.bootstrap-servers，跳过湖 CDC topic 检查");
            return null;
        }
        try {
            if (adminClient == null) {
                Map<String, Object> props = new HashMap<>();
                props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", bootstrapServers));
                props.put(AdminClientConfig.CLIENT_ID_CONFIG, "cdc-lake-topic-watcher");
                adminClient = AdminClient.create(props);
            }
            Set<String> matched = new HashSet<>();
            for (String topic : adminClient.listTopics().names().get(LIST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                if (TOPIC_PATTERN.matcher(topic).matches()) {
                    matched.add(topic);
                }
            }
            return matched;
        } catch (Exception e) {
            log.warn("列出数据湖 CDC topic 失败，跳过本轮: {}", e.toString());
            closeAdminClient();
            return null;
        }
    }

    private void closeAdminClient() {
        if (adminClient != null) {
            try {
                adminClient.close(java.time.Duration.ofSeconds(5));
            } catch (Exception e) {
                log.warn("关闭 topic 探测用 AdminClient 失败: {}", e.toString());
            }
            adminClient = null;
        }
    }

    @PreDestroy
    public void shutdown() {
        closeAdminClient();
    }
}
