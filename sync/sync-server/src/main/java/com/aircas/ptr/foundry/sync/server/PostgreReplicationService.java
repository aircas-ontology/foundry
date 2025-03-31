package com.aircas.ptr.foundry.sync.server;

import com.aircas.ptr.foundry.common.pg.EventParser;
import com.aircas.ptr.foundry.common.pg.PostgresqlConstants;
import com.aircas.ptr.foundry.common.pg.SyncEventContext;
import com.aircas.ptr.foundry.sync.common.DataSourceConfig;
import com.aircas.ptr.foundry.sync.util.TimeUtils;
import io.transwarp.studio.dm.mq.RocketMQClient;
import io.transwarp.studio.dm.mq.topic.MetaDataMQTopics;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.postgresql.PGConnection;
import org.postgresql.PGProperty;
import org.postgresql.replication.LogSequenceNumber;
import org.postgresql.replication.PGReplicationStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;


/**
 * PostgreSQL的DML操作的CDC捕获处理
 *
 * <pre>
 * 要求：根据表名前缀进行过滤，需要正确配置捕获CDC的表名前缀;
 * </pre>
 *
 * @author yibo.tang
 * @date 2021-04-26 10:02:04
 * @since 1.0
 */
@Slf4j
@Service
public class PostgreReplicationService {

    @Autowired
    private DataSourceConfig dataSourceConfig;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private DataSourceProperties dataSourceProperties;



    private Connection connection;
    private PGConnection rplConnection;
    private PGReplicationStream stream;

    private EventParser eventParser;

    public PostgreReplicationService() {
        eventParser = new EventParser();
    }


    /**
     * 开启逻辑复制Server服务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restartReplicationServer() {
        while (true) {
            try {
                createReplicationConnection();
                createReplicationSlot();
                createReplicationStream();

                while (true) {
                    receiveStreamMessage();
                }

            } catch (Exception e) {
                log.error("Error for information error: message={}", e.getMessage(), e);
            } finally {
                try {
                    if (this.stream != null) {
                        this.stream.close();
                    }
                } catch (Exception e) {
                    log.error("restartReplicationServer close stream error: message={}", e.getMessage(), e);
                }
                try {
                    if (this.connection != null) {
                        this.connection.close();
                    }
                } catch (Exception e) {
                    log.error("restartReplicationServer close connection error: message={}", e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 创建逻辑复制连接
     *
     * @throws SQLException
     */
    private void createReplicationConnection() throws SQLException {

        Properties props = new Properties();
        PGProperty.USER.set(props, dataSourceConfig.getPostgresDatalakeUserName());
        PGProperty.PASSWORD.set(props, dataSourceConfig.getPostgresDatalakePassword());
        PGProperty.ASSUME_MIN_SERVER_VERSION.set(props, PostgresqlConstants.minVersion);
        PGProperty.REPLICATION.set(props, PostgresqlConstants.rplLevel);
        PGProperty.PREFER_QUERY_MODE.set(props, PostgresqlConstants.queryMode);
        this.connection = DriverManager.getConnection(dataSourceConfig.getPostgresDatalakeUrl(), props);

        this.rplConnection = this.connection.unwrap(PGConnection.class);
        log.info("Get PostgreSQL Replication Connection success!");
    }

    /**
     * 创建复制槽
     *
     * @throws SQLException
     */
    private void createReplicationSlot() throws SQLException {
        String postgresSlotName = dataSourceConfig.getPostgresSlotName();
        try {
            this.rplConnection.getReplicationAPI()
                    .createReplicationSlot()
                    .logical()
                    .withSlotName("\"" + postgresSlotName + "\"")
                    .withOutputPlugin("test_decoding")
                    .make();
        } catch (SQLException e) {
            String msg = "ERROR: replication slot \"" + postgresSlotName + "\" already exists";
//            this.rplConnection.getReplicationAPI().dropReplicationSlot(postgresSlotName);
            if (msg.equals(e.getMessage())) {
                return;
            }
            throw e;
        }
        log.info("Get PostgreSQL Replication success,slot:{}", postgresSlotName);
    }

    /**
     * 创建复制流
     *
     * @throws SQLException
     */
    private void createReplicationStream() throws SQLException {
        String postgresSlotName = dataSourceConfig.getPostgresSlotName();
        this.stream = this.rplConnection.getReplicationAPI()
                .replicationStream()
                .logical()
                .withSlotName(postgresSlotName)
//                .withSlotOption("include-table-data", true)
                .withSlotOption("skip-empty-xacts", true)
                .withStatusInterval(5, TimeUnit.SECONDS)
                .start();
        log.info("Get PostgreSQL Replication Stream success,slot:{}", postgresSlotName);
    }

    /**
     * 接收消息流
     *
     * @throws SQLException
     */
    private void receiveStreamMessage() throws SQLException {
        assert !stream.isClosed();
        assert !connection.isClosed();

        ByteBuffer msg = stream.readPending();
        if (msg == null) {
            TimeUtils.sleepInMills(10L);
            return;
        }

        int offset = msg.arrayOffset();
        byte[] source = msg.array();
        int length = source.length - offset;
        LogSequenceNumber lsn = stream.getLastReceiveLSN();
        String message = new String(source, offset, length);

        try {
            SyncEventContext context = eventParser.parse(message);
            if (null != context) {
                context.setLsn(lsn.asLong());

                handleEventContext(context);
            }
        } catch (Exception e) {
            log.warn("Parse PostgreSQL Replication Message failed, message content: {}\t", message, e);
        }

        stream.setAppliedLSN(lsn);
        stream.setFlushedLSN(lsn);
    }

    /**
     * 处理消息事件
     *
     * @param context 事件上下文
     */
    private void handleEventContext(SyncEventContext context) {
        if (!dataSourceConfig.getPostgresTableSchema().equalsIgnoreCase(context.getSchema())) {
            return;
        }

        /**
         * 消息处理业务逻辑
         */
        log.info("message context:{}", context);
        SendResult sendResult = rocketMQTemplate.syncSend(MetaDataMQTopics.ONTOLOGY_META_CHANGE_NOTIFY, context);
        log.info("send result:{}", sendResult);
    }
}