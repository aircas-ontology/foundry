package com.aircas.ptr.foundry.ontology.script.security.model;

/**
 * 脚本安全威胁分类。
 */
public enum SecurityCategory {

    /** 命令执行 */
    COMMAND,

    /** 文件读写 / 删除 */
    FILE,

    /** 网络外连 */
    NETWORK,

    /** 反射越权 */
    REFLECTION,

    /** 类加载器滥用 */
    CLASSLOADER,

    /** JVM 控制（退出进程、加载 native 库） */
    JVM,

    /** 危险注解（编译期副作用） */
    ANNOTATION,

    /** 平台内部能力调用 */
    INTERNAL,

    /** 资源耗尽（DoS） */
    RESOURCE,

    /** 脚本体积 / 结构 */
    SIZE,

    /** 解析器 / 扫描器自身异常（fail-closed） */
    SCANNER
}
