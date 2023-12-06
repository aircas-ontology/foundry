package com.aircas.ptr.foundry.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class PropertiesUtil {

    private static Logger logger = LoggerFactory.getLogger(PropertiesUtil.class);

    // 缓存加载的.properties配置文件
    private static Map<String, Properties> propMap = new HashMap<>();

    //获取配置文件中的属性
    public static String getProperty(String filename, String key) {

        String value = null;
        Properties p = loadProperties(filename);
        if (p != null) {
            value = p.getProperty(key);
        }
        return value;
    }

    //加载读取配置文件
    private static Properties loadProperties(String fileName) {

        Properties p;
        if (propMap.containsKey(fileName)) {
            p = propMap.get(fileName);
        } else {
            try (InputStream is = searchFile(fileName)) {
                p = new Properties();
                if (is != null) {
                    p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                }
                propMap.put(fileName, p);
            } catch (Exception e) {
                throw new RuntimeException("Properties file '" + fileName + "' Load Exception!\n" + e.getMessage());
            }
        }
        return p;
    }


    //查找文件，返回字节流输入流
    public static InputStream searchFile(String fileName) {

        InputStream is = null;

        //先在当前项目所在目录下的config文件夹中查找（运行jar包，寻找和jar包同一目录的config文件夹）
        String dir = System.getProperty("user.dir");
        if (dir != null) {
            String path = dir + File.separator + "config" + File.separator + fileName;
            if (Files.exists(Paths.get(path))) {
                try {
                    is = new FileInputStream(path);
                    logger.info("1 -- Load " + fileName + " From : " + path);
                    return is;
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        //还没找到，在当前jar同级目录中查找
        String path = null;
        String p = PropertiesUtil.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (p.endsWith(".jar")) {
            p = p.substring(0, p.lastIndexOf("/"));
            dir = convertUrlByOS(p);
            path = dir + "/" + fileName;
        }
        if (path != null && Files.exists(Paths.get(path))) {
            try {
                is = new FileInputStream(path);
                logger.info("2 -- Load " + fileName + " From : " + path);
                return is;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        //还没找到，在classpath下查找
        ClassLoader classLoader = PropertiesUtil.class.getClassLoader();
        URL resource = classLoader.getResource(fileName);
        if (resource != null) {
            path = resource.getPath();
            is = classLoader.getResourceAsStream(fileName);
            logger.info("3 -- Load " + fileName + " From : " + path);
        } else {
            logger.error("Load Resource File Error");
        }

        return is;
    }

    //转换通过class获取文件路径时，windows系统的路径问题
    private static String convertUrlByOS(String path) {
        String os = System.getProperty("os.name");
        if (os != null && os.contains("Windows")) {
            // windows系统是去掉第一个字符 eg:/C:/dir/file.txt
            path = path.substring(1);
        }
        return path;
    }

}
