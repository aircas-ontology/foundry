package com.aircas.ptr.foundry.ontology.service.parser;

import com.aircas.ptr.foundry.ontology.model.dto.ScriptParsedParam;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.utils.SchemaHandleUtil;
import com.aircas.ptr.foundry.ontology.utils.ScriptParamTypeMapper;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工程内类（脚本 {@code import} 进来的 VO/DTO）字段展开器，Java/Groovy 两个解析器共用。
 * <p>
 * 脚本 AST 只认得源码里的类型名，要拿到 object 下一级的结构必须补全全限定名后回落到主应用
 * ClassLoader 反射：{@code Class.forName(name, false, loader)} 只取类结构与字段声明，不触发静态初始化，
 * 因此不执行任何脚本、也不生成字节码。</p>
 * <p>import 走源码文本扫描而非各自 AST 的 import API：Groovy {@code ModuleNode.getImports()} 的返回类型
 * 在不同 Groovy 版本间是 {@code Map<String,ClassNode>} 或 {@code List<ImportNode>}（本工程实测为后者），
 * 文本扫描与语言与版本都无关；{@code import static} 与带尾注释的行不识别，识别不到时退化为不展开。</p>
 */
@Slf4j
final class ProjectClassExpander {

    /** 业务包白名单：只有该包下的类才反射展开下一级字段，JDK/三方类型统一止步于 object。 */
    private static final String BUSINESS_PACKAGE_PREFIX = "com.aircas.ptr.foundry.";

    /** 字段展开的最大层级（脚本内类与工程内类共用），防御深层嵌套 VO 与自引用。 */
    static final int MAX_FIELD_DEPTH = 4;

    /** 泛型类型变量（如 {@code FunctionResultVO<T>} 的 data）擦除后不可知，按该兜底类名给 referenceType。 */
    private static final String ERASED_TYPE = "java.lang.Object";

    /** 显式 import：{@code import com.x.Y}、{@code import com.x.Y;}、{@code import com.x.Y as Z}（后者仅 Groovy）。 */
    private static final Pattern EXPLICIT_IMPORT = Pattern.compile(
            "^import\\s+([\\w$.]+?)(?:\\s+as\\s+([A-Za-z_$][\\w$]*))?\\s*;?\\s*$");

    /** 通配 import：{@code import com.x.*}，捕获组为包名。 */
    private static final Pattern STAR_IMPORT = Pattern.compile(
            "^import\\s+([\\w$.]+)\\.\\*\\s*;?\\s*$");

    private final Map<String, String> importBySimpleName;
    private final List<String> starPackages;
    private final ClassLoader loader;

    ProjectClassExpander(String code) {
        this.loader = getClass().getClassLoader();
        Map<String, String> imports = new LinkedHashMap<>();
        List<String> packages = new ArrayList<>();
        if (code != null) {
            for (String rawLine : code.split("\r?\n")) {
                String line = rawLine.trim();
                if (!line.startsWith("import") || line.startsWith("import static")) {
                    continue;
                }
                Matcher star = STAR_IMPORT.matcher(line);
                if (star.matches()) {
                    packages.add(star.group(1));
                    continue;
                }
                Matcher explicit = EXPLICIT_IMPORT.matcher(line);
                if (explicit.matches()) {
                    String typeName = explicit.group(1);
                    String alias = explicit.group(2);
                    imports.put(alias == null ? simpleName(typeName) : alias, typeName);
                }
            }
        }
        this.importBySimpleName = imports;
        this.starPackages = packages;
    }

    /**
     * 把 AST 里的类型名（可能是简单名，也可能已带包名）解析为业务包下的类；
     * 非业务包类型（JDK/三方）、数组写法与解析不到的名字一律返回 null，保持「object 无下一级」的原口径。
     */
    Class<?> resolve(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return null;
        }
        String base = stripGenerics(typeName.trim());
        if (base.isEmpty() || base.endsWith("[]")) {
            return null;
        }
        for (String candidate : candidateNames(base)) {
            if (!candidate.startsWith(BUSINESS_PACKAGE_PREFIX)) {
                continue;
            }
            try {
                return Class.forName(candidate, false, loader);
            } catch (ClassNotFoundException | LinkageError e) {
                log.debug("脚本参数类型候选加载失败：{}（{}）", candidate, e.getMessage());
            }
        }
        return null;
    }

    /** 先 {@link #resolve} 再展开；解析不到返回 {@code null}（区别于空列表，调用方据此判定「没有下一级」）。 */
    List<ScriptParsedParam> expandFieldsByName(String typeName, FunctionParamCategoryEnum category,
                                               Set<String> visited, int depth) {
        Class<?> projectClass = resolve(typeName);
        return projectClass == null ? null : expandFields(projectClass, category, visited, depth);
    }

    /**
     * 反射展开工程内类的实例字段（含父类，跳过 static/synthetic），递归进业务包字段类型。
     * <p>字段类型文本取 {@code Type.getTypeName()}（带包名与泛型）：{@code rawType} 用它，referenceType 才是
     * 可被 {@code Class.forName} 加载的全限定名；类型族判定要先剥包名，因为 {@link ScriptParamTypeMapper} 只认简单名。</p>
     */
    List<ScriptParsedParam> expandFields(Class<?> clazz, FunctionParamCategoryEnum category,
                                         Set<String> visited, int depth) {
        List<ScriptParsedParam> fields = new ArrayList<>();
        if (clazz == null || depth > MAX_FIELD_DEPTH || !visited.add(clazz.getName())) {
            return fields;
        }
        for (Field field : SchemaHandleUtil.getInstanceFields(clazz)) {
            Type generic = field.getGenericType();
            String typeText = generic instanceof TypeVariable ? ERASED_TYPE : generic.getTypeName();
            ScriptParsedParam.ScriptParsedParamBuilder fb = ScriptParsedParam.builder()
                    .name(field.getName())
                    .type(ScriptParamTypeMapper.map(dequalify(typeText)))
                    .rawType(typeText)
                    .category(category)
                    .source("ANNOTATION");
            Class<?> nested = businessClassOf(generic);
            if (nested != null) {
                fb.fields(expandFields(nested, category, visited, depth + 1));
            }
            fields.add(fb.build());
        }
        return fields;
    }

    /** 候选全限定名列表：显式 import 优先，其次通配包拼接，最后原样（已是全限定名的场景）。 */
    private List<String> candidateNames(String base) {
        List<String> candidates = new ArrayList<>();
        String imported = importBySimpleName.get(simpleName(base));
        if (imported != null) {
            candidates.add(imported);
        }
        for (String pkg : starPackages) {
            candidates.add(pkg + "." + simpleName(base));
        }
        if (!candidates.contains(base)) {
            candidates.add(base);
        }
        return candidates;
    }

    /**
     * 反射字段类型对应的业务类：数组取组件类型、泛型集合取元素类型、Map 取值类型、通配符取上界；
     * 类型变量（{@code T}）擦除后不可知，返回 null 由 {@code @result:类型} 指令或前端补充。
     */
    private Class<?> businessClassOf(Type type) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                return businessClassOf(clazz.getComponentType());
            }
            return isBusinessClass(clazz) ? clazz : null;
        }
        if (type instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            if (!(raw instanceof Class<?> rawClass)) {
                return null;
            }
            if (Collection.class.isAssignableFrom(rawClass) || Map.class.isAssignableFrom(rawClass)) {
                Type[] args = parameterized.getActualTypeArguments();
                if (args.length == 0) {
                    return null;
                }
                // List<E> 取 E，Map<K,V> 取 V（键恒为字符串语义，值才是业务结构）
                return businessClassOf(Map.class.isAssignableFrom(rawClass) ? args[args.length - 1] : args[0]);
            }
            return isBusinessClass(rawClass) ? rawClass : null;
        }
        if (type instanceof GenericArrayType arrayType) {
            return businessClassOf(arrayType.getGenericComponentType());
        }
        if (type instanceof WildcardType wildcard) {
            Type[] upper = wildcard.getUpperBounds();
            return upper != null && upper.length > 0 ? businessClassOf(upper[0]) : null;
        }
        return null;
    }

    /** 业务包下的具体类才展开字段；枚举/JDK/三方类型止步。 */
    private boolean isBusinessClass(Class<?> clazz) {
        return clazz != null && !clazz.isPrimitive() && !clazz.isEnum()
                && clazz.getName().startsWith(BUSINESS_PACKAGE_PREFIX);
    }

    /** 去掉泛型实参，只留裸类型名（{@code FunctionResultVO<Integer>} → {@code FunctionResultVO}）。 */
    private static String stripGenerics(String typeText) {
        int angle = typeText.indexOf('<');
        return angle > 0 ? typeText.substring(0, angle).trim() : typeText;
    }

    /** 剥掉包名限定：{@code java.util.List<com.x.Y>} → {@code List<Y>}，供 ScriptParamTypeMapper 按简单名判类型族。 */
    private static String dequalify(String typeText) {
        return typeText == null ? "" : typeText.replaceAll("(?:[A-Za-z_$][\\w$]*\\.)+", "");
    }

    private static String simpleName(String fullName) {
        if (fullName == null) {
            return "";
        }
        int dot = fullName.lastIndexOf('.');
        return dot >= 0 ? fullName.substring(dot + 1) : fullName;
    }
}
