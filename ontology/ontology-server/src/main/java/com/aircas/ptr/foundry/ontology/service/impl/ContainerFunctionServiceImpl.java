package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.composite.CompositeFunctionCatalog;
import com.aircas.ptr.foundry.ontology.config.ContainerOperatorProperties;
import com.aircas.ptr.foundry.ontology.model.dto.RateLimitConfig;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerImageInfo;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterface;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterfaceDoc;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterfaceParam;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerOperatorConfig;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ContainerPhaseEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.ContainerFunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.ContainerProbeParam;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionContainerInstance;
import com.aircas.ptr.foundry.ontology.model.po.FunctionContainerUpload;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.model.po.FunctionRateLimitTemplate;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionParameterVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionContainerInstanceMapper;
import com.aircas.ptr.foundry.ontology.service.ContainerFunctionService;
import com.aircas.ptr.foundry.ontology.service.FunctionParamService;
import com.aircas.ptr.foundry.ontology.service.FunctionRateLimitTemplateService;
import com.aircas.ptr.foundry.ontology.service.OpenApiProbeService;
import com.aircas.ptr.foundry.ontology.utils.ContainerNodeForwarder;
import com.aircas.ptr.foundry.ontology.utils.ContainerNodeIdentity;
import com.aircas.ptr.foundry.ontology.utils.ContainerUploadRegistry;
import com.aircas.ptr.foundry.ontology.utils.DockerImageInspector;
import com.aircas.ptr.foundry.ontology.utils.DockerCommandExecutor;
import com.aircas.ptr.foundry.ontology.utils.DockerRunCommandParser;
import com.aircas.ptr.foundry.ontology.utils.OperatorFunctionFactory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 容器算子注册服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerFunctionServiceImpl implements ContainerFunctionService {

    /** 容器算子不参与版本管理，版本恒为该值 */
    private static final String CONTAINER_VERSION = "1.0.0";

    /** 允许的镜像扩展名 */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".tar", ".tar.gz", ".tgz");
    /** 允许的接口文档扩展名 */
    private static final Set<String> DOC_EXTENSIONS = Set.of(".json", ".yaml", ".yml");

    private final ContainerOperatorProperties properties;
    private final OpenApiProbeService openApiProbeService;

    @Resource
    private FunctionMapper functionMapper;

    @Resource
    private FunctionParamService functionParamService;

    @Resource
    private FunctionContainerInstanceMapper containerInstanceMapper;

    /**
     * 多实例部署下的节点归属三件套：镜像 tar 与 docker 容器只落在收到上传请求那台机器上，
     * 上传时登记归属（{@link ContainerUploadRegistry}）→ 探查/启停据此判定归属（{@link ContainerNodeIdentity}）
     * → 非本节点则把同一请求转发到归属节点（{@link ContainerNodeForwarder}）。
     */
    @Resource
    private ContainerUploadRegistry containerUploadRegistry;

    @Resource
    private ContainerNodeIdentity containerNodeIdentity;

    @Resource
    private ContainerNodeForwarder containerNodeForwarder;

    /** 限流配置模板服务：容器算子每条派生 function 均需写入模板快照 */
    @Resource
    private FunctionRateLimitTemplateService rateLimitTemplateService;

    // 序列化入库时忽略 null：docker_config 只落接口级内容（{"interfaces":[...]}），不携带容器级空字段
    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

    @Override
    public ContainerImageInfo uploadImage(MultipartFile file) {
        String fileName = sanitizeFileName(file);
        PreconditionUtils.checkArgument(hasImageExtension(fileName),
                "镜像文件仅支持 .tar / .tar.gz / .tgz 格式", HttpStatus.BAD_REQUEST);
        long maxBytes = (long) properties.getMaxImageSizeMb() * 1024 * 1024;
        PreconditionUtils.checkArgument(file.getSize() <= maxBytes,
                "镜像文件超过大小上限 " + properties.getMaxImageSizeMb() + "MB", HttpStatus.BAD_REQUEST);

        Path dir = properties.resolveImageDir();
        Path target = dir.resolve(UUID.randomUUID() + fileName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("镜像保存失败：" + e.getMessage());
        }

        DockerImageInspector.ImageMeta meta;
        try {
            meta = DockerImageInspector.inspect(target);
        } catch (RuntimeException e) {
            // 解析失败：仅启用校验时视为非法并清理；关闭校验时记录并放行（本地无法解析镜像包时测试用）
            if (properties.isArmCheckEnabled()) {
                deleteQuietly(target);
                throw e;
            }
            log.warn("架构校验已关闭，镜像架构解析失败但放行：{}", e.getMessage());
            meta = new DockerImageInspector.ImageMeta(DockerImageInspector.UNKNOWN_ARCH, new ArrayList<>());
        }

        String architecture = meta.getArchitecture();
        if (properties.isArmCheckEnabled() && !isArchitectureAllowed(architecture)) {
            deleteQuietly(target);
            // UNKNOWN_ARCH 代表 config 缺失/解析失败，与“架构不对”同位拒绝但归因不同，文案区分便于运维定位
            String message = DockerImageInspector.UNKNOWN_ARCH.equals(architecture)
                    ? "镜像 config 缺失或解析失败，无法识别架构（请确认 tar 为 docker save 导出的完整镜像包）"
                    : "镜像架构不受支持，当前镜像架构为 " + architecture + "，允许的架构："
                            + String.join("/", properties.getAllowedArchitectures());
            throw new BusinessException(message);
        }

        ContainerImageInfo imageInfo = ContainerImageInfo.builder()
                .fileName(fileName)
                .path(target.toAbsolutePath().toString())
                .size(file.getSize())
                .architecture(meta.getArchitecture())
                .repoTags(meta.getRepoTags())
                .build();
        // tar 只落在本节点磁盘：登记归属，后续的探查/创建/启停据此把请求指回这里
        containerUploadRegistry.record(imageInfo.getPath(), FunctionContainerUpload.KIND_IMAGE);
        return imageInfo;
    }

    @Override
    public ContainerInterfaceDoc uploadDoc(MultipartFile file) {
        String fileName = sanitizeFileName(file);
        PreconditionUtils.checkArgument(hasDocExtension(fileName),
                "接口文档仅支持 .json / .yaml / .yml 格式", HttpStatus.BAD_REQUEST);

        Path dir = properties.resolveDocDir();
        Path target = dir.resolve(UUID.randomUUID() + "-" + fileName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("接口文档保存失败：" + e.getMessage());
        }

        ContainerInterfaceDoc docInfo = ContainerInterfaceDoc.builder()
                .fileName(fileName)
                .path(target.toAbsolutePath().toString())
                .format(openApiProbeService.detectFormat(target.toAbsolutePath().toString()))
                .build();
        // 文档同样只落在本节点磁盘：探查接口时要读它，故一并登记归属
        containerUploadRegistry.record(docInfo.getPath(), FunctionContainerUpload.KIND_DOC);
        return docInfo;
    }

    @Override
    public List<ContainerInterface> probeInterfaces(String docPath) {
        // 文档可能在兄弟节点上（上传与探查是两次独立请求，会被负载到不同实例）：按登记反查归属并转发，
        // 对端跑同一个端点、读它自己磁盘上的文件，前端无需感知 node.ip
        FunctionContainerUpload owner = containerUploadRegistry.findOwner(docPath);
        if (owner != null && !containerNodeIdentity.isSelf(owner.getNodeIp())
                && !containerNodeForwarder.isForwardedRequest()) {
            return containerNodeForwarder.forwardList(owner.getNodeIp(), owner.getNodePort(),
                    new ContainerProbeParam(docPath), ContainerInterface.class, "接口探查");
        }
        return openApiProbeService.probe(docPath);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> createContainerFunction(ContainerFunctionCreateParam param) {
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(param.getSelectedInterfaces()),
                "至少勾选一个接口才能生成算子", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(param.getImage() != null && param.getImage().getPath() != null,
                "请先上传镜像", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(Files.exists(Path.of(param.getImage().getPath())),
                "镜像文件不存在，请重新上传", HttpStatus.BAD_REQUEST);

        // 从启动命令解析宿主端口并固化：解析不出显式端口映射则拒绝保存，
        // 避免存入执行期无法定位调用地址的算子（执行时直接读 docker_config.hostPort，不再解析命令）
        DockerRunCommandParser.PortMapping portMapping =
                DockerRunCommandParser.parseFirstPublished(param.getRunCommand());
        PreconditionUtils.checkArgument(portMapping != null && portMapping.getHostPort() != null,
                "启动命令需包含明确的端口映射（-p 宿主端口:容器端口），无法从命令解析出宿主端口：" + param.getRunCommand(),
                HttpStatus.BAD_REQUEST);

        // 设计 C：每个勾选接口各生成一条独立 function 记录（一行算子 = 一个接口），共用同一镜像与接口文档。
        // api = 容器算子 baseApi + "_" + 规整后的探查接口 api；displayName/description = 第一步公共前缀 + 接口信息拼接。
        // 接口 api（形如 "POST /detect"）含空格/斜杠不利于路由与唯一性校验，先规整再拼接。
        // containerKey 为本批次唯一归组键：N 条 function 的 docker_config 均携此值，并对应一条 function_container_instance（一容器）。
        String containerKey = UUID.randomUUID().toString().replace("-", "");

        // 容器级唯一性校验（一容器=一实例，保存期拦截，避免存入 start 才爆的算子）：
        // 多节点部署下端口与容器名按节点隔离（每台机器各有独立 docker daemon），故校验范围限定在本节点；
        // 单节点模式 node_ip 为空，查同为空的历史记录，保持原行为。
        // 1) 容器名：用户 --name 优先，否则平台默认 foundry-ctr-<key>（UUID 天然唯一）；docker 容器名全局唯一，不得与已注册实例重名。
        // 2) 宿主端口：一宿主端口=一容器算子，不得与任何已注册实例的 host_port 重复。
        String userContainerName = DockerRunCommandParser.parseContainerName(param.getRunCommand());
        String containerName = (userContainerName != null && !userContainerName.isBlank())
                ? userContainerName
                : DockerCommandExecutor.defaultContainerName(containerKey);
        LambdaQueryWrapper<FunctionContainerInstance> nodeScope = new LambdaQueryWrapper<>();
        if (containerNodeIdentity.isMultiNode()) {
            nodeScope.eq(FunctionContainerInstance::getNodeIp, containerNodeIdentity.nodeIp());
        } else {
            nodeScope.isNull(FunctionContainerInstance::getNodeIp);
        }
        boolean portTaken = containerInstanceMapper.selectCount(nodeScope
                .eq(FunctionContainerInstance::getHostPort, portMapping.getHostPort())) > 0;
        PreconditionUtils.checkArgument(!portTaken,
                "宿主端口 " + portMapping.getHostPort() + " 已被其他容器算子占用，请更换 -p 端口映射", HttpStatus.BAD_REQUEST);
        LambdaQueryWrapper<FunctionContainerInstance> nameScope = new LambdaQueryWrapper<>();
        if (containerNodeIdentity.isMultiNode()) {
            nameScope.eq(FunctionContainerInstance::getNodeIp, containerNodeIdentity.nodeIp());
        } else {
            nameScope.isNull(FunctionContainerInstance::getNodeIp);
        }
        boolean nameTaken = containerInstanceMapper.selectCount(nameScope
                .eq(FunctionContainerInstance::getContainerName, containerName)) > 0;
        PreconditionUtils.checkArgument(!nameTaken,
                "容器名与已注册容器算子重名：" + containerName + "，请在启动命令中更换 --name", HttpStatus.BAD_REQUEST);

        List<String> createdApis = new ArrayList<>();
        Set<String> batchApis = new HashSet<>();
        // 限流模板快照：容器算子每条派生 function 均写入同一份配置快照
        FunctionRateLimitTemplate rateLimitTemplate = rateLimitTemplateService.getById(param.getRateLimitTemplateId());
        PreconditionUtils.checkArgument(rateLimitTemplate != null,
                "限流模板不存在：" + param.getRateLimitTemplateId(), HttpStatus.BAD_REQUEST);
        String rateLimitConfigJson = buildRateLimitConfigJson(rateLimitTemplate);
        for (ContainerInterface itf : param.getSelectedInterfaces()) {
            String itfApi = itf.getApi() != null ? itf.getApi()
                    : (itf.getMethod() + " " + itf.getPath());
            String itfSegment = sanitizeApiSegment(itfApi);
            PreconditionUtils.checkArgument(!itfSegment.isEmpty(),
                    "接口标识规整后为空，无法生成算子 api：" + itfApi, HttpStatus.BAD_REQUEST);
            String derivedApi = param.getFunctionApi() + "_" + itfSegment;
            PreconditionUtils.checkArgument(batchApis.add(derivedApi),
                    "本批次存在重复接口标识：" + derivedApi, HttpStatus.BAD_REQUEST);
            // api 唯一：容器算子无版本迭代，派生 api 不能与任何已存在函数重复
            boolean apiExists = functionMapper.selectCount(new LambdaQueryWrapper<Function>()
                    .eq(Function::getApi, derivedApi)) > 0;
            PreconditionUtils.checkArgument(!apiExists,
                    "算子 api 已存在：" + derivedApi, HttpStatus.BAD_REQUEST);

            // 设计去重：docker_config 只存本行接口级配置（list of 1），
            // 镜像/接口文档/启动命令/端口等容器级字段只写一次到 function_container_instance。
            ContainerOperatorConfig config = ContainerOperatorConfig.builder()
                    .interfaces(List.of(itf))
                    .build();

            Function function = OperatorFunctionFactory.operatorDefaults(
                            derivedApi, CONTAINER_VERSION, joinDash(param.getDisplayName(), itf.getName()),
                            FunctionTypeEnum.CONTAINER, param.getOntologySpaceId(), FunctionModelEnum.OTHER)
                    .description(joinDesc(param.getDescription(), itf.getDescription()))
                    .containerKey(containerKey)
                    .dockerConfig(writeJson(config))
                    .rateLimitTemplateId(rateLimitTemplate.getId())
                    .rateLimitConfig(rateLimitConfigJson)
                    .build();
            functionMapper.insert(function);

            saveInterfaceParams(function.getId(), itf);
            createdApis.add(derivedApi);
        }
        // 生成一个运行实例行（一个容器算子=一个容器，设计 C 的 N 条 function 通过 containerKey 归组），初始态 CREATED
        // 容器级信息（镜像/接口文档/启动命令/端口）在此仅存一份，为唯一权威来源
        boolean multiNode = containerNodeIdentity.isMultiNode();
        FunctionContainerInstance instance = FunctionContainerInstance.builder()
                .containerKey(containerKey)
                .ontologySpaceId(param.getOntologySpaceId())
                .displayName(param.getDisplayName())
                .containerName(containerName)
                .image(param.getImage() != null ? writeJson(param.getImage()) : null)
                .interfaceDoc(param.getInterfaceDoc() != null ? writeJson(param.getInterfaceDoc()) : null)
                .runCommand(param.getRunCommand())
                .hostPort(portMapping.getHostPort())
                .containerPort(portMapping.getContainerPort())
                // 归属节点 = 实际执行创建的节点（创建请求已按镜像归属转发到这里）：
                // 启停/刷新据此转发，执行期也据此拼容器调用地址；单节点模式留空，保持原行为
                .nodeIp(multiNode ? containerNodeIdentity.nodeIp() : null)
                .nodePort(multiNode ? containerNodeIdentity.nodePort() : null)
                .phase(ContainerPhaseEnum.CREATED)
                .message("容器算子已创建，尚未启动")
                .createTime(new Date())
                .updateTime(new Date())
                .build();
        containerInstanceMapper.insert(instance);
        // 归属已固化到实例行，向导期的上传登记不再需要
        containerUploadRegistry.remove(param.getImage().getPath(),
                param.getInterfaceDoc() != null ? param.getInterfaceDoc().getPath() : null);
        return createdApis;
    }

    /** 单个接口的出入参写入该算子的 function_param（一算子一接口，天然按接口分组） */
    private void saveInterfaceParams(Long functionId, ContainerInterface itf) {
        List<FunctionParamPO> params = new ArrayList<>();
        int inputOrder = 0;
        int outputOrder = 0;
        for (ContainerInterfaceParam p : nullSafe(itf.getInputParameters())) {
            params.add(buildParam(functionId, p, FunctionParamCategoryEnum.INPUT, inputOrder++));
        }
        for (ContainerInterfaceParam p : nullSafe(itf.getOutputParameters())) {
            params.add(buildParam(functionId, p, FunctionParamCategoryEnum.OUTPUT, outputOrder++));
        }
        if (!params.isEmpty()) {
            functionParamService.saveBatch(params);
        }
    }

    /**
     * 规整接口 api 片段用于拼接派生 api：
     * 将 "/"、"\" 与连续空白统一替换为单个下划线，折叠连续下划线/连字符为单个，
     * 并去掉首尾多余分隔符。形如 "POST /detect" -> "POST_detect"。
     */
    private String sanitizeApiSegment(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .replaceAll("[/\\\\\\s]+", "_")     // 斜杠/反斜杠/空白 → 下划线
                .replaceAll("_+", "_")             // 折叠连续下划线
                .replaceAll("-+", "-")             // 折叠连续连字符
                .replaceAll("^[-_]+|[-_]+$", "");  // 去掉首尾分隔符
    }

    /** 公共前缀 + 接口标识用 "-" 拼接（任一为空则取另一个，都空返回 null） */
    private String joinDash(String prefix, String suffix) {
        boolean hasPrefix = prefix != null && !prefix.isBlank();
        boolean hasSuffix = suffix != null && !suffix.isBlank();
        if (!hasPrefix) {
            return hasSuffix ? suffix : null;
        }
        return hasSuffix ? prefix + "-" + suffix : prefix;
    }

    /** 公共描述 + 接口描述用空格拼接 */
    private String joinDesc(String prefix, String suffix) {
        boolean hasPrefix = prefix != null && !prefix.isBlank();
        boolean hasSuffix = suffix != null && !suffix.isBlank();
        if (!hasPrefix) {
            return hasSuffix ? suffix : null;
        }
        return hasSuffix ? prefix + " " + suffix : prefix;
    }

    private FunctionParamPO buildParam(Long functionId, ContainerInterfaceParam p,
                                       FunctionParamCategoryEnum category, int order) {
        return OperatorFunctionFactory.functionParamDefaults(
                        functionId, p.getName(), p.getType(), category, order, p.getDescription())
                .paramSchema(writeJson(Map.of(
                        "in", p.getIn() == null ? "" : p.getIn(),
                        "required", Boolean.TRUE.equals(p.getRequired()),
                        "openapiType", p.getType() == null ? "object" : p.getType())))
                .fields(toFieldsJson(p.getFields()))
                .build();
    }

    /**
     * 子字段落 {@code function_param.fields}：形状必须是 {@link FunctionParameterVO} 列表，
     * 因为 {@code getFunctionDetailByApi} 按该类型反序列化此列（键为 paramName/paramType），
     * 直接序列化探查 DTO 的 name/type 会让详情页子字段读不出名字。
     */
    private String toFieldsJson(List<ContainerInterfaceParam> fields) {
        if (CollectionUtils.isEmpty(fields)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(toFieldVOs(fields));
        } catch (Exception e) {
            log.warn("序列化参数 fields 失败：cause={}", e.getMessage());
            return null;
        }
    }

    private List<FunctionParameterVO> toFieldVOs(List<ContainerInterfaceParam> fields) {
        List<FunctionParameterVO> vos = new ArrayList<>();
        int order = 0;
        for (ContainerInterfaceParam f : fields) {
            List<FunctionParameterVO> children = CollectionUtils.isEmpty(f.getFields())
                    ? null : toFieldVOs(f.getFields());
            vos.add(FunctionParameterVO.builder()
                    .paramName(f.getName())
                    .paramType(CompositeFunctionCatalog.toParamType(f.getType()))
                    .paramOrder(order++)
                    .description(f.getDescription())
                    .paramSchema(writeJson(Map.of(
                            "in", f.getIn() == null ? "" : f.getIn(),
                            "required", Boolean.TRUE.equals(f.getRequired()),
                            "openapiType", f.getType() == null ? "object" : f.getType())))
                    .fields(children)
                    .build());
        }
        return vos;
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BusinessException("容器算子配置序列化失败：" + e.getMessage());
        }
    }

    private String buildRateLimitConfigJson(FunctionRateLimitTemplate template) {
        if (template == null) {
            return null;
        }
        RateLimitConfig config = RateLimitConfig.builder()
                .timeoutMs(template.getTimeoutMs())
                .retryCount(template.getRetryCount())
                .retryIntervalMs(template.getRetryIntervalMs())
                .tpsLimit(template.getTpsLimit())
                .maxConcurrency(template.getMaxConcurrency())
                .enabled(template.getEnabled())
                .build();
        return writeJson(config);
    }

    private List<ContainerInterfaceParam> nullSafe(List<ContainerInterfaceParam> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private String sanitizeFileName(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件为空");
        }
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new BusinessException("文件名为空");
        }
        // 去掉路径成分，防目录穿越
        return java.nio.file.Paths.get(original).getFileName().toString();
    }

    private boolean hasImageExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private boolean hasDocExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return DOC_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    /** 架构是否落在 {@code container-operator.allowed-architectures} 内（大小写不敏感）；清单为空视为不限制 */
    private boolean isArchitectureAllowed(String architecture) {
        List<String> allowed = properties.getAllowedArchitectures();
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        return allowed.stream().anyMatch(item -> item != null && item.trim().equalsIgnoreCase(architecture));
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除无效镜像文件失败：{}", path);
        }
    }

}
