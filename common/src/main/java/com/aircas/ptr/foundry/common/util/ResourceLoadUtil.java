package com.aircas.ptr.foundry.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 加载资源文件工具类
 * 
 * @author hhm
 *
 */
public class ResourceLoadUtil {
	private static final Logger logger = LoggerFactory.getLogger(ResourceLoadUtil.class);

	/**
	 * 查找加载配置文件 1.从指定配置文件路径查找config.dir 2.当前jar同级目录 3.从classpath
	 * 
	 * @param filename
	 *            加载文件全名
	 * @return
	 */
	public static InputStream searchConfigFile(String filename) {
		InputStream is = null;

		String dir = System.getProperty("config.dir");

		if (dir != null) {
			String path = dir + "/" + filename;
			if (Files.exists(Paths.get(path))) {
				try {
					is = new FileInputStream(path);
					logger.info("Load Resource File From 'config.dir={}'. Path : {}", dir, path);
				} catch (FileNotFoundException e) {
					logger.warn(e.getMessage(), e);
				}
			}
		}

		if (is == null) {
			is = searchResourceFile(filename);
		}

		return is;
	}

	/**
	 * 取得加载的配置文件路径 1.从指定配置文件路径查找config.dir 2.当前jar同级目录 3.从classpath
	 * 
	 * @param filename
	 *            加载文件全名
	 * @return
	 */
	public static String getConfigFilePath(String filename) {
		String filePath = null;

		// 1.从指定配置文件路径查找
		String configDir = System.getProperty("config.dir");
		if (configDir != null) {
			filePath = configDir + "/" + filename;
			if (Files.exists(Paths.get(filePath))) {
				return filePath;
			}
		}

		// 2.当前jar同级目录 
		String jarDir = null;
		String jarPath = new ResourceLoadUtil().getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
		if (jarPath.endsWith(".jar")) {
			jarPath = jarPath.substring(0, jarPath.lastIndexOf("/"));
			jarDir = convertUrlByOS(jarPath);
			filePath = jarDir + "/" + filename;
		}
		if (filePath != null && Files.exists(Paths.get(filePath))) {
			return filePath;
		}

		// 3.从classpath
		InputStream is = null;
		URL url = ResourceLoadUtil.class.getResource("/" + filename);
		if (url != null) {
			String fp = url.getFile();
			if (fp != null && !"".equals(fp)) {
				filePath = fp;
				is = ResourceLoadUtil.class.getResourceAsStream("/" + filename);
				if (is != null) {
					try {
						is.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					return filePath;
				}
			}
		}

		return filePath;
	}

	/**
	 * 逐级查找资源文件 1.当前jar同级目录 2.从classpath
	 * 
	 * @param filename
	 * @return
	 */
	public static InputStream searchResourceFile(String filename) {
		InputStream is = null;

		is = findFileFromJarDir(filename);
		if (is == null) {
			is = findFileFromClasspath(filename);
		}

		if (is == null) {
			logger.warn("Load Resource File Exception Not Found File : {}", filename);
		}

		return is;
	}

	/**
	 * 从classpath中查找文件
	 * 
	 * @param filename
	 * @return
	 */
	public static InputStream findFileFromClasspath(String filename) {
		InputStream is = null;
		String path = null;
		URL url = ResourceLoadUtil.class.getResource("/" + filename);
		if (url != null) {
			String fp = url.getFile();
			if (fp != null && !"".equals(fp)) {
				path = fp;

				is = ResourceLoadUtil.class.getResourceAsStream("/" + filename);
				if (is != null) {
					logger.info("Load Resource File From Classpath. Path : {} ", path);
				}
			}
		}

		return is;
	}

	/**
	 * 从当前jar包同级目录获取资源文件
	 * 
	 * @param filename
	 * @return
	 */
	public static InputStream findFileFromJarDir(String filename) {
		InputStream is = null;
		String path = null;
		String dir = null;
		String p = new ResourceLoadUtil().getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
		if (p.endsWith(".jar")) {
			p = p.substring(0, p.lastIndexOf("/"));
			dir = convertUrlByOS(p);
			path = dir + "/" + filename;
		}

		if (path != null && Files.exists(Paths.get(path))) {
			try {
				is = new FileInputStream(path);
				logger.info("Load Resource File From Jar Directory. Path : {}", path);
			} catch (FileNotFoundException e) {
				logger.warn(e.getMessage(), e);
			}
		}

		return is;
	}

	public static Properties loadProperties(String filename) {
		String fname = filename + ".properties";
		Properties p = null;
		InputStream is = null;
		try {
			p = new Properties();
			is = ResourceLoadUtil.searchConfigFile(fname);
			if (is != null) {
				p.load(new InputStreamReader(is, "UTF8"));
			}
			logger.info("Properties file '" + fname + "' content : " + p.toString());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw new RuntimeException("Properties file '" + fname + "' Load Exception!\n" + e.getMessage());
		} finally {
			try {
				if (is != null) {
					is.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return p;
	}

	/**
	 * 转换通过class获取文件路径时，windows系统的路径问题
	 * 
	 * @param path
	 * @return
	 */
	private static String convertUrlByOS(String path) {
		String os = System.getProperty("os.name");
		if (os != null && os.indexOf("Windows") != -1) {
			// windows系统是去掉第一个字符 eg:/C:/dir/file.txt
			path = path.substring(1);
		}
		return path;
	}

}
