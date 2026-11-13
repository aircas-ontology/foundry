package com.aircas.ptr.foundry.common.exception;

import com.aircas.ptr.foundry.common.filter.LoggingFilter;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把「没抛 BusinessException、直接冲到全局处理器」的异常翻译成客户能看懂的一句话。
 *
 * <p>匹配方式：只按异常类名（含父类链）与报文关键字判断，不 import 数据库 / 图库 / Redis / 脚本引擎的异常类型。
 * common 模块不依赖这些组件，按类名匹配既可覆盖其子类，也不会因某个依赖缺失而编译不过；
 * 所有规则都不命中时走兜底文案，因此新增的未知异常最差也退回「系统内部错误 + 请求号」，不会比现在更含糊。
 *
 * <p>输出只给结论和必要标识（表名、字段名、目标地址、请求号），不给堆栈、不给原始 SQL。
 *
 * <p>分支顺序有两处硬约束，改动前务必注意：
 * <ul>
 *   <li>PG 的 {@code column "x" of relation "y" does not exist} 同时含 column 与 relation，缺字段必须判在缺表之前；</li>
 *   <li>Jackson、文件、脚本类异常都继承 {@code java.io.IOException}，通用的「调用外部服务失败」分支必须排在它们之后。</li>
 * </ul>
 *
 * @since 2026/11/13
 */
public final class SystemErrorClassifier {

    /** 单条异常报文参与匹配时的截断长度，避免超长驱动报文撑爆前端提示 */
    private static final int MESSAGE_SEGMENT_LIMIT = 300;
    /** 回显给客户的标识长度上限（表名、字段名、地址等） */
    private static final int DETAIL_LIMIT = 60;
    /** 最终提示语的总长度上限 */
    private static final int PROMPT_LIMIT = 320;
    /** 遍历 cause 链的最大层数，防止自引用异常死循环 */
    private static final int CAUSE_CHAIN_LIMIT = 12;

    /** 标识两侧可能出现的引号：PG 双引号、MySQL 单引号/反引号 */
    private static final String QUOTES = "['\"`]";

    // ---- 参数 / 请求体 ----
    private static final String NAME_MISSING_PARAM = "missingservletrequestparameter";
    private static final String NAME_MISSING_PART = "missingservletrequestpart";
    private static final Pattern MISSING_PARAM = Pattern.compile("Required request parameter '([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern MISSING_HEADER = Pattern.compile("(?:Missing|Required) request header '([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern MISSING_PART = Pattern.compile("Required part '([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern BAD_METHOD = Pattern.compile("Request method '([^']+)' not supported", Pattern.CASE_INSENSITIVE);
    private static final Pattern BAD_CONTENT_TYPE = Pattern.compile("Content type '([^']+)' not supported", Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE_PARAM_NAME = Pattern.compile("(?:Method parameter|parameter) '([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE_TARGET = Pattern.compile("required type[^\\w]*([\\w.$]{1,60})", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATIC_RESOURCE = Pattern.compile("No static resource (\\S{1,80})", Pattern.CASE_INSENSITIVE);

    // ---- JSON ----
    private static final Pattern JSON_FIELD = Pattern.compile("reference chain: [^\\[]*\\[\"([^\"]+)\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_TARGET_TYPE = Pattern.compile("Cannot deserialize value of type[^\\w]*([\\w.$]{1,60})", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_BAD_VALUE_TEXT = Pattern.compile("from (?:String|number) ['\"]([^'\"]{1,40})['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_UNKNOWN_FIELD = Pattern.compile("Unrecognized field \"([^\"]{1,60})\"", Pattern.CASE_INSENSITIVE);

    // ---- 数据库：PostgreSQL 为主，兼容 MySQL 常见报文；标识必须带引号才提取，避免误吞 MyBatis 打印的原始 SQL ----
    private static final Pattern MISSING_COLUMN = Pattern.compile("column" + QUOTES + "([\\w.$]{1,60})" + QUOTES, Pattern.CASE_INSENSITIVE);
    private static final Pattern MISSING_TABLE = Pattern.compile("(?:relation|table|view)" + QUOTES + "?([\\w.$]{1,60})" + QUOTES + "?\\s+(?:does not exist|doesn't exist|not found)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIQUE_NAME = Pattern.compile("(?:unique constraint|unique index|for key)\\s+" + QUOTES + "?([^'\"`\\s]{1,60})", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_NULL_COLUMN = Pattern.compile("null value in column " + QUOTES + "([\\w.$]{1,60})", Pattern.CASE_INSENSITIVE);
    private static final Pattern BAD_INPUT = Pattern.compile("invalid input syntax for type ([\\w ]{1,20}): " + QUOTES + "?([^'\"`\\n|]{1,40})", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATEMENT_TIMEOUT = Pattern.compile("canceling statement due to statement timeout", Pattern.CASE_INSENSITIVE);

    // ---- 图库 ArangoDB：报文形如 Response code: 404, error: 1203, message: collection or view not found ----
    private static final Pattern ARANGO_DETAIL = Pattern.compile("error:\\s*\\d+,\\s*message:\\s*([^|]{1,80})", Pattern.CASE_INSENSITIVE);

    // ---- 部署 / 依赖 ----
    private static final Pattern MISSING_CLASS = Pattern.compile("(?:ClassNotFound|NoClassDefFound|NoSuchMethod|NoSuchField)(?:Error|Exception)?[:\\s]+([\\w/$.]{2,80})", Pattern.CASE_INSENSITIVE);

    // ---- 外部服务地址 ----
    private static final Pattern HOST_PORT = Pattern.compile("/?([a-z0-9._-]{2,60}:\\d{2,5})", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOST_ONLY = Pattern.compile("(?:Failed to connect to |Connect to |UnknownHostException: |for host |host: )\\s*/?([a-z0-9._-]{2,60})", Pattern.CASE_INSENSITIVE);

    // ---- 文件 / 数值 ----
    private static final Pattern FILE_NAME = Pattern.compile("([\\w\\-.\\\\/:+]{2,100}?\\.\\w{1,6})", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_VALUE = Pattern.compile("For input string:\\s*\"([^\"]{1,40})\"", Pattern.CASE_INSENSITIVE);

    private SystemErrorClassifier() {
    }

    /**
     * 生成给客户看的提示语。
     *
     * @param error 未被业务异常包裹的异常
     * @return 一句话提示；需要管理员介入的分类会附带请求号
     */
    public static String classifyMessage(Throwable error) {
        if (error == null) {
            return user("系统处理失败，请重试；持续出现请联系管理员");
        }
        String names = classNames(error);
        String text = chainMessages(error);
        String lower = text.toLowerCase();

        // ---- 连接已被对端关闭：不是故障，也不需要客户处理 ----
        if (has(names, "clientabortexception", "asyncrequestnotusableexception")
                || lower.contains("broken pipe")
                || lower.contains("connection reset by peer")) {
            return user("请求已中断：浏览器或网关提前关闭了连接");
        }

        // ---- 参数 / 请求体本身有问题：客户自己就能改，不带请求号 ----
        if (names.contains(NAME_MISSING_PARAM)) {
            return user("缺少必填参数" + quoted(first(MISSING_PARAM, text)) + "，请补充后重试");
        }
        if (names.contains(NAME_MISSING_PART)) {
            return user("缺少上传文件" + quoted(first(MISSING_PART, text)) + "，请重新选择文件后重试");
        }
        if (has(names, "servletrequestbindingexception")) {
            String header = first(MISSING_HEADER, text);
            return StringUtils.isNotEmpty(header)
                    ? user("缺少必填请求头" + quoted(header) + "，请重新登录后重试")
                    : user("请求参数绑定失败，请检查请求参数或重新登录后重试");
        }
        if (has(names, "methodargumenttypemismatchexception", "typemismatchexception",
                "conversionnotsupportedexception")) {
            return user("参数" + quoted(first(TYPE_PARAM_NAME, text)) + "的类型不正确"
                    + targetHint(first(TYPE_TARGET, text))
                    + "，请检查该参数的填写内容");
        }
        if (has(names, "httpmessagenotreadableexception")) {
            return user(describeBody(lower, text));
        }
        if (has(names, "httprequestmethodnotsupportedexception")) {
            return user("接口调用方式" + quoted(first(BAD_METHOD, text)) + "不被支持，请刷新页面后重试");
        }
        if (has(names, "httpmediatypenotsupportedexception", "httpmediatyperesolverexception")) {
            return user("请求内容类型" + quoted(first(BAD_CONTENT_TYPE, text)) + "不被支持，请使用 application/json");
        }
        if (has(names, "noresourcefoundexception", "nohandlerfoundexception")) {
            return user("访问地址" + quoted(first(STATIC_RESOURCE, text)) + "不存在或已变更，请刷新页面后重试");
        }

        // ---- 服务资源不足 / 排队 / 处理超时 ----
        if (has(names, "java.lang.outofmemoryerror", "java.lang.stackoverflowerror")
                || lower.contains("java heap space")
                || lower.contains("gc overhead limit")
                || lower.contains("unable to create new native thread")) {
            return admin("服务内存或线程资源不足，请稍后重试；持续出现请联系管理员重启服务");
        }
        if (has(names, "rejectedexecutionexception")) {
            return admin("系统当前处理任务较多，请稍后重试");
        }
        if (has(names, "java.util.concurrent.timeoutexception")) {
            return admin("本次处理超时，未在限定时间内完成，请稍后重试或缩小数据范围");
        }

        // ---- 部署包 / 依赖版本问题 ----
        if (has(names, "java.lang.noclassdeffounderror", "java.lang.classnotfoundexception",
                "java.lang.nosuchmethoderror", "java.lang.nosuchfielderror", "nosuchmethodexception",
                "java.lang.unsupportedclassversionerror")) {
            return admin("服务依赖缺失或版本不一致" + tail("：", first(MISSING_CLASS, text))
                    + "，请确认部署包与服务脚本已更新到最新版本");
        }

        // ---- 缓存 / 消息队列：判在数据库之前，Redis 的连接异常在 Spring 里也属于 DAO 异常族 ----
        if (has(names, ".redis.", "lettuce", "jedis")) {
            return admin("缓存服务（Redis）不可用，请稍后重试；持续出现请联系管理员");
        }
        if (has(names, "org.apache.kafka.", "org.springframework.kafka.", "org.springframework.amqp.")) {
            return admin("消息队列服务不可用，请稍后重试；持续出现请联系管理员");
        }

        // ---- 数据库：账号权限 / 唯一约束 / 非空 / 缺字段 / 缺表 / 取值类型 ----
        if (lower.contains("password authentication failed") || lower.contains("no pg_hba.conf entry")
                || lower.contains("access denied for user")) {
            return admin("数据源账号或密码不正确，或该账号没有访问此数据库的权限，请检查数据源配置");
        }
        if (has(names, "duplicatekeyexception") || lower.contains("duplicate key")
                || lower.contains("unique constraint") || lower.contains("duplicate entry")) {
            return user("已有相同的数据" + tail("（唯一约束", first(UNIQUE_NAME, text), "）")
                    + "，请修改名称或去掉重复项后重试");
        }
        if (lower.contains("foreign key")) {
            return user("关联数据不存在，或该数据仍被其他数据引用，请先处理相关数据后重试");
        }
        String notNullColumn = first(NOT_NULL_COLUMN, text);
        if (notNullColumn != null) {
            return admin("必填内容缺失：数据库字段" + quoted(notNullColumn) + "不允许为空");
        }
        String missingColumn = first(MISSING_COLUMN, text);
        if (missingColumn != null || lower.contains("undefinedcolumn") || lower.contains("unknown column")) {
            return admin("数据库缺少字段" + quoted(missingColumn) + "，请确认最新的变更脚本已在该库执行");
        }
        String missingTable = first(MISSING_TABLE, text);
        if (missingTable != null || lower.contains("undefinedtable") || lower.contains("no such table")) {
            return admin("数据库缺少表" + quoted(missingTable) + "，请确认最新的建表脚本已在该库执行");
        }
        Matcher badInput = BAD_INPUT.matcher(text);
        if (badInput.find()) {
            return user("填写内容" + quoted(abbreviate(badInput.group(2), DETAIL_LIMIT))
                    + "不是合法的 " + badInput.group(1).trim() + "，请修改后重试");
        }
        if (STATEMENT_TIMEOUT.matcher(text).find() || has(names, "querytimeoutexception")) {
            return admin("数据查询超时，请缩小查询范围后重试；持续出现请提供请求号给管理员优化");
        }
        if (has(names, "deadlockloserdataaccessexception", "pessimisticlockfailureexception",
                "optimisticlockfailureexception")
                || lower.contains("deadlock detected") || lower.contains("lock wait timeout")) {
            return user("该数据正在被其他操作修改，请稍后重试");
        }
        if (lower.contains("value too long") || lower.contains("data truncation") || lower.contains("data too long")
                || lower.contains("string right truncation")) {
            return user("填写内容长度超出限制，请缩短后重试");
        }
        if (lower.contains("cannot cast") || lower.contains("inconsistent types")
                || lower.contains("operator does not exist")) {
            return admin("数据字段类型与数据库定义不一致，请提供请求号给管理员核对");
        }
        if (has(names, "cannotgetjdbcconnectionexception", "dataaccessresourcefailureexception",
                "transientdataaccessresourceexception")
                || lower.contains("too many connections") || lower.contains("connection is not available")
                || lower.contains("cannot get a connection") || lower.contains("communications link failure")) {
            return admin("数据库连接不可用（连接池已满或数据库拒绝连接），请稍后重试；持续出现请联系管理员检查数据库");
        }
        if (has(names, "org.springframework.dao.", "org.apache.ibatis.", "com.baomidou.mybatisplus.",
                "badsqlgrammarexception", "dataintegrityviolationexception")) {
            return admin("数据读写失败，请提供请求号给管理员定位");
        }

        // ---- 图数据库 ArangoDB ----
        if (has(names, "com.arangodb.")) {
            return admin("图数据库操作失败" + tail("：", first(ARANGO_DETAIL, text))
                    + "，请提供请求号给管理员检查图库集合与连接");
        }

        // ---- 连不上外部服务（外部数据源、外部 API、Kafka Connect、Docker 宿主等） ----
        if (has(names, "java.net.unknownhostexception")) {
            return admin("无法解析服务地址" + quoted(endpoint(text)) + "，请检查配置中的主机名或 IP 是否正确");
        }
        if (has(names, "java.net.connectexception")
                || lower.contains("connection refused") || lower.contains("failed to connect")) {
            return admin("无法连接依赖服务" + quoted(endpoint(text)) + "：对方拒绝连接，请确认该服务已启动");
        }
        if (has(names, "java.net.sockettimeoutexception")
                || lower.contains("read timed out") || lower.contains("connection timed out")) {
            return admin("依赖服务" + quoted(endpoint(text)) + "响应超时，请稍后重试");
        }
        if (has(names, "sslhandshakeexception", "java.security.cert.certpathvalidator", "sslpeerunverifiedexception")) {
            return admin("与依赖服务建立加密连接失败，请检查证书或 HTTPS 配置");
        }

        // ---- 脚本 / 编排编译 ----
        if (has(names, "org.codehaus.groovy.", "groovy.lang.", "javax.script.scriptexception")
                || lower.contains("cannot find symbol")
                || (lower.contains("package ") && lower.contains(" does not exist"))) {
            return user("脚本编译失败，请检查脚本语法与引用的类是否已导入后重试");
        }

        // ---- JSON / 序列化 ----
        if (has(names, "com.fasterxml.jackson.", "com.alibaba.fastjson", "com.jayway.jsonpath.")) {
            return admin("数据格式解析失败" + tail("（字段", first(JSON_FIELD, text), "）")
                    + "，请检查内容是否为合法 JSON、字段类型是否匹配");
        }

        // ---- 文件 / 权限 / 磁盘 ----
        if (has(names, "java.io.filenotfoundexception", "java.nio.file.nosuchfileexception",
                "java.nio.file.nosuchdirectoryexception")) {
            return admin("服务器上找不到所需文件" + tail("：", first(FILE_NAME, text))
                    + "，请检查部署目录与已上传的文件");
        }
        if (has(names, "java.nio.file.accessdeniedexception")
                || lower.contains("permission denied") || lower.contains("read-only file system")) {
            return admin("没有权限读写服务器上的文件，请检查部署目录权限");
        }
        if (lower.contains("no space left on device") || lower.contains("disk full")) {
            return admin("服务器磁盘已满，请清理磁盘空间后重试");
        }

        // ---- 数值 / 计算 ----
        if (has(names, "java.lang.numberformatexception")) {
            return user(quoted(first(NUMBER_VALUE, text)) + "不是合法数字，请检查填写内容");
        }
        if (has(names, "java.lang.arithmeticexception") || lower.contains("/ by zero")) {
            return admin("计算出现除零等异常，请提供请求号给管理员定位");
        }

        // ---- 其余 IO / Socket 异常：只能给出报文摘要，必须排在以上 IOException 子类分支之后 ----
        if (has(names, "java.io.ioexception", "java.net.socketexception")) {
            return admin("调用外部服务失败" + tail("：", abbreviate(rootMessage(error), DETAIL_LIMIT))
                    + "，请确认对端服务可用后重试");
        }

        // ---- 兜底：程序内部异常，客户只能靠请求号报障 ----
        return admin("系统内部错误（" + error.getClass().getSimpleName() + "：" + bugHint(error) + "）"
                + "，请把请求号提供给管理员定位原因");
    }

    /**
     * 需要管理员介入的提示：带请求号。
     * logId 正常由 {@link LoggingFilter} 放入 MDC；上传文件等被过滤器跳过的请求没有 logId，这里临时生成一个。
     * 临时号不会写进 MDC，日志行的 {@code %X{logId}} 仍为空，但全局处理器会把这条提示语作为日志内容连同堆栈一起打印，
     * 按请求号 grep 日志即可定位到那一次异常。
     */
    private static String admin(String message) {
        String logId = MDC.get(LoggingFilter.LOG_ID_KEY);
        if (StringUtils.isEmpty(logId)) {
            logId = IdGenerator.generateLogId();
        }
        return StringUtils.abbreviate(message, PROMPT_LIMIT) + "（请求号：" + logId + "）";
    }

    /** 客户可自助处理的提示：不带请求号 */
    private static String user(String message) {
        return StringUtils.abbreviate(message, PROMPT_LIMIT);
    }

    /** 兜底分类下，把 JVM 内部异常名翻译成客户能懂的成因 */
    private static String bugHint(Throwable error) {
        return switch (error.getClass().getSimpleName()) {
            case "NullPointerException" -> "数据不完整";
            case "ClassCastException" -> "数据类型与预期不符";
            case "ArrayIndexOutOfBoundsException", "IndexOutOfBoundsException" -> "数据条数与预期不符";
            case "ConcurrentModificationException" -> "数据被并发修改";
            case "IllegalStateException" -> "状态与预期不符";
            case "IllegalArgumentException" -> "参数取值不被接受";
            case "UnsupportedOperationException" -> "当前版本不支持该操作";
            default -> "请联系管理员";
        };
    }

    /** 请求体解析失败：优先说清是哪个字段、要什么类型 */
    private static String describeBody(String lowerText, String text) {
        if (lowerText.contains("required request body is missing") || lowerText.contains("no request body")) {
            return "请求内容为空，请重试；持续出现请刷新页面后重试";
        }
        if (lowerText.contains("unexpected end of")) {
            return "请求内容不完整，请重试";
        }
        String unknown = first(JSON_UNKNOWN_FIELD, text);
        if (StringUtils.isNotEmpty(unknown)) {
            return "请求内容包含无法识别的字段" + quoted(unknown) + "，请刷新页面后用最新版本重试";
        }
        String field = first(JSON_FIELD, text);
        if (StringUtils.isEmpty(field)) {
            return "请求内容格式不正确，请检查填写的数据";
        }
        return "请求内容字段" + quoted(field) + "不正确"
                + targetHint(first(JSON_TARGET_TYPE, text))
                + tail("，实际收到", first(JSON_BAD_VALUE_TEXT, text))
                + "，请修改后重试";
    }

    /** 收集异常自身与所有 cause 的父类全限定类名（小写，空格分隔），供类名规则匹配 */
    private static String classNames(Throwable error) {
        StringBuilder sb = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < CAUSE_CHAIN_LIMIT) {
            for (Class<?> type = current.getClass(); type != null; type = type.getSuperclass()) {
                sb.append(type.getName().toLowerCase()).append(' ');
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return sb.toString();
    }

    /** 拼接异常自身与所有 cause 的报文，用于关键字判断与标识提取 */
    private static String chainMessages(Throwable error) {
        StringBuilder sb = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < CAUSE_CHAIN_LIMIT) {
            String message = current.getMessage();
            if (StringUtils.isNotEmpty(message)) {
                sb.append(StringUtils.abbreviate(message, MESSAGE_SEGMENT_LIMIT)).append(" | ");
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return sb.toString();
    }

    /** cause 链最里层的报文：驱动报文的真实原因在这里 */
    private static String rootMessage(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current.getCause() != null && current.getCause() != current && depth++ < CAUSE_CHAIN_LIMIT) {
            current = current.getCause();
        }
        return StringUtils.defaultString(current.getMessage(), "");
    }

    private static boolean has(String names, String... keys) {
        for (String key : keys) {
            if (names.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static String first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? abbreviate(matcher.group(1), DETAIL_LIMIT) : null;
    }

    /** 目标地址：优先 host:port，其次只有主机名 */
    private static String endpoint(String text) {
        String hostPort = first(HOST_PORT, text);
        return StringUtils.isNotEmpty(hostPort) ? hostPort : first(HOST_ONLY, text);
    }

    /** 「，应为 整数 / 文本」：类型提取不到就不说，避免出现空书名号 */
    private static String targetHint(String qualifiedType) {
        String type = simpleTypeName(qualifiedType);
        return StringUtils.isEmpty(type) ? "" : "，应为" + type;
    }

    private static String quoted(String value) {
        return StringUtils.isEmpty(value) ? "" : "「" + value + "」";
    }

    /** 提取到标识时才拼接：前缀 + 「标识」+ 后缀；没提取到返回空串 */
    private static String tail(String prefix, String value) {
        return tail(prefix, value, "");
    }

    private static String tail(String prefix, String value, String suffix) {
        return StringUtils.isEmpty(value) ? "" : prefix + "「" + value + "」" + suffix;
    }

    /** 全限定类名取末段，并映射成客户能懂的取值要求 */
    private static String simpleTypeName(String qualifiedType) {
        if (StringUtils.isEmpty(qualifiedType)) {
            return "";
        }
        String simple = StringUtils.substringAfterLast(qualifiedType, ".");
        if (StringUtils.isEmpty(simple)) {
            simple = qualifiedType;
        }
        return switch (simple) {
            case "Integer", "int", "Long", "long", "Short", "short", "Byte", "byte" -> "整数";
            case "Double", "double", "Float", "float", "BigDecimal" -> "小数";
            case "Boolean", "boolean" -> "true/false";
            case "String" -> "文本";
            default -> simple;
        };
    }

    private static String abbreviate(String value, int limit) {
        return value == null ? null : StringUtils.abbreviate(value.trim(), limit);
    }
}
