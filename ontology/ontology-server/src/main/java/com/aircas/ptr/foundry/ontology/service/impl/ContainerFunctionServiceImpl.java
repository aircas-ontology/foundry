package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.config.ContainerOperatorProperties;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerImageInfo;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterface;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterfaceDoc;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterfaceParam;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerOperatorConfig;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.ContainerFunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionMapper;
import com.aircas.ptr.foundry.ontology.service.ContainerFunctionService;
import com.aircas.ptr.foundry.ontology.service.FunctionParamService;
import com.aircas.ptr.foundry.ontology.service.OpenApiProbeService;
import com.aircas.ptr.foundry.ontology.constant.FunctionConstant;
import com.aircas.ptr.foundry.ontology.utils.DockerImageInspector;
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
    /** ARM 架构标识 */
    private static final String ARM64 = "arm64";

    private final ContainerOperatorProperties properties;
    private final OpenApiProbeService openApiProbeService;

    @Resource
    private FunctionMapper functionMapper;

    @Resource
    private FunctionParamService functionParamService;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
            // 解析失败：仅启用校验时视为非法并清理；关闭校验时记录并放行（本地无 ARM 环境测试）
            deleteQuietly(target);
            if (properties.isArmCheckEnabled()) {
                throw e;
            }
            log.warn("ARM 校验已关闭，镜像架构解析失败但放行：{}", e.getMessage());
            meta = new DockerImageInspector.ImageMeta("unknown", new ArrayList<>());
        }

        if (properties.isArmCheckEnabled() && !ARM64.equals(meta.getArchitecture())) {
            deleteQuietly(target);
            throw new BusinessException("仅支持 ARM64 镜像，当前镜像架构为 " + meta.getArchitecture());
        }

        return ContainerImageInfo.builder()
                .fileName(fileName)
                .path(target.toAbsolutePath().toString())
                .size(file.getSize())
                .architecture(meta.getArchitecture())
                .repoTags(meta.getRepoTags())
                .build();
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

        return ContainerInterfaceDoc.builder()
                .fileName(fileName)
                .path(target.toAbsolutePath().toString())
                .format(openApiProbeService.detectFormat(target.toAbsolutePath().toString()))
                .build();
    }

    @Override
    public List<ContainerInterface> probeInterfaces(String docPath) {
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

        // 设计 C：每个勾选接口各生成一条独立 function 记录（一行算子 = 一个接口），共用同一镜像与接口文档。
        // api = 容器算子 baseApi + "-" + 探查接口 api；displayName/description = 第一步公共前缀 + 接口信息拼接。
        List<String> createdApis = new ArrayList<>();
        Set<String> batchApis = new HashSet<>();
        for (ContainerInterface itf : param.getSelectedInterfaces()) {
            String itfApi = itf.getApi() != null ? itf.getApi()
                    : (itf.getMethod() + " " + itf.getPath());
            String derivedApi = param.getFunctionApi() + "-" + itfApi;
            PreconditionUtils.checkArgument(batchApis.add(derivedApi),
                    "本批次存在重复接口标识：" + derivedApi, HttpStatus.BAD_REQUEST);
            // api 唯一：容器算子无版本迭代，派生 api 不能与任何已存在函数重复
            boolean apiExists = functionMapper.selectCount(new LambdaQueryWrapper<Function>()
                    .eq(Function::getApi, derivedApi)) > 0;
            PreconditionUtils.checkArgument(!apiExists,
                    "算子 api 已存在：" + derivedApi, HttpStatus.BAD_REQUEST);

            // 该行算子的 docker_config 只存它自己这一个接口（list of 1），执行时取 interfaces[0]
            ContainerOperatorConfig config = ContainerOperatorConfig.builder()
                    .image(param.getImage())
                    .interfaceDoc(param.getInterfaceDoc())
                    .interfaces(List.of(itf))
                    .build();

            Function function = Function.builder()
                    .api(derivedApi)
                    .version(CONTAINER_VERSION)
                    .publish(FunctionConstant.PUBLISH_UNPUBLISHED)
                    .displayName(joinDash(param.getDisplayName(), itf.getName()))
                    .description(joinDesc(param.getDescription(), itf.getDescription()))
                    .type(FunctionTypeEnum.CONTAINER)
                    .model(FunctionModelEnum.OTHER)
                    .status(Status.ENABLE.getValue())
                    .ontologySpaceId(param.getOntologySpaceId())
                    .dockerConfig(writeJson(config))
                    .build();
            functionMapper.insert(function);

            saveInterfaceParams(function.getId(), itf);
            createdApis.add(derivedApi);
        }
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
        return FunctionParamPO.builder()
                .functionId(functionId)
                .paramName(p.getName())
                .paramType(mapType(p.getType()))
                .category(category)
                .paramOrder(order)
                .description(p.getDescription())
                .paramSchema(writeJson(Map.of(
                        "in", p.getIn() == null ? "" : p.getIn(),
                        "required", Boolean.TRUE.equals(p.getRequired()),
                        "openapiType", p.getType() == null ? "object" : p.getType())))
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    /** OpenAPI schema type → FunctionParamTypeEnum，未知归 OBJECT */
    private FunctionParamTypeEnum mapType(String type) {
        if (type == null) {
            return FunctionParamTypeEnum.OBJECT;
        }
        try {
            return FunctionParamTypeEnum.fromJson(type.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FunctionParamTypeEnum.OBJECT;
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BusinessException("容器算子配置序列化失败：" + e.getMessage());
        }
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

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除无效镜像文件失败：{}", path);
        }
    }

}
