package com.aircas.ptr.foundry.common.util;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RAR工具类
 *
 * @author yangjian
 */
public class RarUtils {

    private static Logger logger = LoggerFactory.getLogger(RarUtils.class);

    private static final String FILE_TYPE = ".rar";
    private static final String LINUX = "linux";
    private static final String WINDOWS = "windows";
    private static final String OS_TYPE = System.getProperty("os.name").toLowerCase();
    private static final String UNRAR_PATH = PropertiesUtil.getProperty("application.yml", "unrar-path");

    /**
     * 解压rar文件
     *
     * @param srcpath 文件路径
     * @param dstpath 解压路径
     * @return 解压后的文件夹路径
     */
    public static String unrar(String srcpath, String dstpath) {
        String fileName = "";
        if (!srcpath.toLowerCase().endsWith(FILE_TYPE)) {
            logger.error("文件 {} 类型错误，不是  {} 类型文件，解压缩失败！", srcpath, FILE_TYPE);
            return fileName;
        }
        File file = new File(srcpath);
        if (!file.exists()) {
            logger.error("文件  {} 不存在，解压缩失败！", srcpath);
            return fileName;
        }

        String fileRarName = file.getName();

        dstpath = dstpath + File.separator + fileRarName.replaceAll(FILE_TYPE, "");
        File file1 = new File(dstpath);
        if (!file1.exists()) {
            file1.mkdirs();
        }
        File fol = null;
        File out = null;
        Archive a = null;
        try {
            a = new Archive(file);
            FileHeader fh = a.nextFileHeader();
            while (fh != null) {
                if (fh.isDirectory()) {
                    if (existZH(fh.getFileNameW())) {
                        fileName = dstpath + File.separator + fh.getFileNameW();
                        fol = new File(fileName);
                    } else {
                        fileName = dstpath + File.separator + fh.getFileNameString();
                        fol = new File(fileName);
                    }
                    fol.mkdirs();
                } else {
                    if (existZH(fh.getFileNameW())) {
                        fileName = dstpath + File.separator + fh.getFileNameW().trim();
                        out = new File(fileName);
                    } else {
                        fileName = dstpath + File.separator + fh.getFileNameString().trim();
                        out = new File(fileName);
                    }
                    if (!out.exists()) {
                        if (!out.getParentFile().exists()) {
                            out.getParentFile().mkdirs();
                        }
                        out.createNewFile();
                    }
                    FileOutputStream os = new FileOutputStream(out);
                    a.extractFile(fh, os);
                    os.close();
                }
                fh = a.nextFileHeader();
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("文件 {} 解压缩失败！", srcpath);
        } finally {
            try {
                if (a != null) {
                    a.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return dstpath;
    }


    /**
     * 解压rar文件，调用unrar
     *
     * @param srcpath 文件路径
     * @param dstpath 解压路径
     * @return 解压后的文件夹路径
     */
    public static boolean unrarWithExe(String srcpath, String dstpath) {

        if (!srcpath.toLowerCase().endsWith(FILE_TYPE)) {
            logger.error("文件 {} 类型错误，不是  {} 类型文件，解压缩失败！", srcpath, FILE_TYPE);
            return false;
        }
        File srcFile = new File(srcpath);
        if (!srcFile.exists()) {
            logger.error("文件  {} 不存在，解压缩失败！", srcpath);
            return false;
        }
        File desFile = new File(dstpath);
        if (!desFile.exists()) {
            desFile.mkdirs();
        }
        String cmd = null;
        if (OS_TYPE.toLowerCase().contains(LINUX)) {
            cmd = UNRAR_PATH + " x " + srcFile.getAbsolutePath() + " " + dstpath;
        } else if (OS_TYPE.toLowerCase().contains(WINDOWS)) {
            cmd = UNRAR_PATH + " X -o+ " + srcFile.getAbsolutePath() + " " + dstpath;
        } else {
            return false;
        }
        boolean result = true;
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            if (process.waitFor() != 0) {
                result = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 校验字符串中是否存在中文字符
     *
     * @param str
     */
    public static boolean existZH(String str) {
        String regEx = "[\\u4e00-\\u9fa5]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(str);
        return m.find();
    }

}
