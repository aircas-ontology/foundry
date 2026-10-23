package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterface;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterfaceParam;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 容器算子接口文档探查服务：用 swagger-parser 解析 OpenAPI 3.x / Swagger 2.0 文档，
 * 遍历所有 path × method 生成接口列表（名称、api、请求方式、路径、描述、入参、出参）。
 * <p>swagger-parser 会自动把 swagger2 转为 openapi3 并解引用 $ref。</p>
 */
@Slf4j
@Service
public class OpenApiProbeService {

    /** 子字段最多下探的层数：兜住自引用 schema（如 Student.children 仍是 Student）与 resolveFully 展开出的超深结构 */
    private static final int MAX_FIELD_DEPTH = 5;

    /**
     * 探查文档，返回接口列表。
     *
     * @param docPath 已上传文档的服务器存储路径
     * @return 探查到的接口列表（不落库）
     */
    public List<ContainerInterface> probe(String docPath) {
        Path path = Path.of(docPath);
        if (!Files.exists(path)) {
            throw new BusinessException("接口文档不存在，请重新上传：" + docPath);
        }
        String location = path.toUri().toString();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        SwaggerParseResult result = new OpenAPIParser().readLocation(location, null, options);
        OpenAPI openAPI = result.getOpenAPI();
        if (openAPI == null || openAPI.getPaths() == null) {
            String msgs = result.getMessages() == null ? "" : String.join("; ", result.getMessages());
            throw new BusinessException("接口文档解析失败" + (msgs.isBlank() ? "" : "：" + msgs));
        }

        List<ContainerInterface> interfaces = new ArrayList<>();
        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            String urlPath = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();
            Map<PathItem.HttpMethod, Operation> operations = pathItem.readOperationsMap();
            if (operations == null) {
                continue;
            }
            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : operations.entrySet()) {
                String method = opEntry.getKey().name().toUpperCase(Locale.ROOT);
                Operation operation = opEntry.getValue();
                interfaces.add(buildInterface(urlPath, method, operation));
            }
        }
        return interfaces;
    }

    /** 判断原始文档格式（openapi3 / swagger2），读取文本头部关键字 */
    public String detectFormat(String docPath) {
        try {
            String content = Files.readString(Path.of(docPath), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (content.contains("\"swagger\"") || content.contains("swagger:")) {
                return "swagger2";
            }
            return "openapi3";
        } catch (Exception e) {
            return "openapi3";
        }
    }

    private ContainerInterface buildInterface(String urlPath, String method, Operation operation) {
        String api = method + " " + urlPath;
        String name = firstNonBlank(operation.getOperationId(), operation.getSummary(), api);
        String description = firstNonBlank(operation.getSummary(), operation.getDescription(), "");

        List<ContainerInterfaceParam> inputs = new ArrayList<>();
        if (operation.getParameters() != null) {
            for (Parameter parameter : operation.getParameters()) {
                ContainerInterfaceParam param = ContainerInterfaceParam.builder()
                        .name(parameter.getName())
                        .in(parameter.getIn())
                        .required(parameter.getRequired())
                        .type(schemaType(parameter.getSchema()))
                        .description(firstNonBlank(parameter.getDescription(), ""))
                        .build();
                inputs.add(param);
            }
        }
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody != null && requestBody.getContent() != null) {
            Schema<?> bodySchema = pickJsonSchema(requestBody.getContent());
            flattenSchemaInto(bodySchema, "body", inputs);
        }

        List<ContainerInterfaceParam> outputs = new ArrayList<>();
        ApiResponses responses = operation.getResponses();
        if (responses != null) {
            for (Map.Entry<String, ApiResponse> respEntry : responses.entrySet()) {
                if (!isSuccessCode(respEntry.getKey())) {
                    continue;
                }
                ApiResponse response = respEntry.getValue();
                if (response.getContent() == null) {
                    continue;
                }
                Schema<?> respSchema = pickJsonSchema(response.getContent());
                flattenSchemaInto(respSchema, "body", outputs);
                break;
            }
        }

        return ContainerInterface.builder()
                .name(name)
                .api(api)
                .method(method)
                .path(urlPath)
                .description(firstNonBlank(description, ""))
                .inputParameters(inputs)
                .outputParameters(outputs)
                .build();
    }

    /**
     * 把 schema 展开为参数列表并逐层递归：对象取 properties，数组取 items 的 properties，
     * 子字段挂在父参数的 {@code fields} 上。
     * <p>字段名保持文档中的原始名（{@code id}、{@code name}），不拼成 {@code student.id}：
     * 执行期按顶层参数名组装请求体，扁平名会让容器收到字面量键 {@code "student.id"}。</p>
     */
    private void flattenSchemaInto(Schema<?> schema, String in, List<ContainerInterfaceParam> target) {
        target.addAll(describeFields(schema, in, 0, Collections.newSetFromMap(new IdentityHashMap<>())));
    }

    /** 列出 schema 的字段，并递归填充每个字段的 fields；depth 从 0 起算 */
    private List<ContainerInterfaceParam> describeFields(Schema<?> schema, String in, int depth, Set<Schema<?>> path) {
        List<ContainerInterfaceParam> fields = new ArrayList<>();
        Schema<?> carrier = fieldCarrier(schema);
        if (carrier == null || depth >= MAX_FIELD_DEPTH || !path.add(carrier)) {
            return fields;
        }
        Map<String, Schema> props = carrier.getProperties();
        if (props == null || props.isEmpty()) {
            path.remove(carrier);
            return fields;
        }
        List<String> required = carrier.getRequired() == null ? Collections.emptyList() : carrier.getRequired();
        try {
            for (Map.Entry<String, Schema> entry : props.entrySet()) {
                Schema<?> prop = entry.getValue();
                List<ContainerInterfaceParam> children = describeFields(prop, in, depth + 1, path);
                fields.add(ContainerInterfaceParam.builder()
                        .name(entry.getKey())
                        .in(in)
                        .required(required.contains(entry.getKey()))
                        .type(schemaType(prop))
                        .description(firstNonBlank(prop == null ? null : prop.getDescription(), ""))
                        .fields(children.isEmpty() ? null : children)
                        .build());
            }
        } finally {
            path.remove(carrier);
        }
        return fields;
    }

    /**
     * 取 schema 的「字段承载体」：先合并组合 schema，数组再下探到 items。
     * <p>items 只在 {@link ArraySchema} 上提供，非该实例的 array（解析器未归类）按无子字段处理。</p>
     */
    private Schema<?> fieldCarrier(Schema<?> schema) {
        if (schema == null) {
            return null;
        }
        Schema<?> merged = mergeComposed(schema);
        if (merged instanceof ArraySchema arraySchema) {
            return arraySchema.getItems();
        }
        return merged;
    }

    /**
     * 合并 {@code allOf}（退化时取 {@code oneOf} / {@code anyOf}）成员的 properties 与 required。
     * <p>文档常用 {@code allOf: [$ref]} 包一层，{@code resolveFully} 不保证已展平为普通对象 schema。</p>
     */
    private Schema<?> mergeComposed(Schema<?> schema) {
        if (!(schema instanceof ComposedSchema composed)) {
            return schema;
        }
        List<Schema> members = composed.getAllOf() != null ? composed.getAllOf()
                : (composed.getOneOf() != null ? composed.getOneOf() : composed.getAnyOf());
        Map<String, Schema> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        if (members != null) {
            for (Schema<?> member : members) {
                Schema<?> carrier = fieldCarrier(member);
                if (carrier == null) {
                    continue;
                }
                if (carrier.getProperties() != null) {
                    props.putAll(carrier.getProperties());
                }
                if (carrier.getRequired() != null) {
                    required.addAll(carrier.getRequired());
                }
            }
        }
        if (composed.getProperties() != null) {
            props.putAll(composed.getProperties());
        }
        ObjectSchema merged = new ObjectSchema();
        merged.setDescription(composed.getDescription());
        merged.setProperties(props);
        merged.setRequired(required);
        return merged;
    }

    private Schema<?> pickJsonSchema(Content content) {
        if (content == null) {
            return null;
        }
        MediaType mediaType = content.get("application/json");
        if (mediaType == null) {
            mediaType = content.get("application/x-www-form-urlencoded");
        }
        if (mediaType == null && !content.isEmpty()) {
            mediaType = content.values().iterator().next();
        }
        return mediaType == null ? null : mediaType.getSchema();
    }

    private String schemaType(Schema<?> schema) {
        if (schema == null) {
            return "object";
        }
        if (schema.getType() != null) {
            return schema.getType();
        }
        if (schema.getTypes() != null && !schema.getTypes().isEmpty()) {
            return schema.getTypes().iterator().next();
        }
        return "object";
    }

    private boolean isSuccessCode(String code) {
        if (code == null) {
            return false;
        }
        if ("default".equalsIgnoreCase(code)) {
            return true;
        }
        return code.startsWith("2");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
