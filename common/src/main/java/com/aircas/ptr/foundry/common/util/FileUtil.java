package com.aircas.ptr.foundry.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class FileUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);

    // 缓冲区大小
    private static final int BUFFER_SIZE = 20480;

    /**
     * 将文本文件中的内容读入到buffer中
     *
     * @param buffer   buffer
     * @param filePath 文件路径
     * @throws IOException 异常
     * @author cn.outofmemory
     * @date 2013-1-7
     */
    public static void readToBuffer(StringBuffer buffer, String filePath) throws IOException {

        InputStream is = null;
        BufferedReader reader = null;
        try {
            is = new FileInputStream(filePath);
            String line; // 用来保存每行读取的内容
            reader = new BufferedReader(new InputStreamReader(is));
            line = reader.readLine(); // 读取第一行
            while (line != null) { // 如果 line 为空说明读完了
                buffer.append(line); // 将读到的内容添加到 buffer 中
                buffer.append("\n"); // 添加换行符
                line = reader.readLine(); // 读取下一行
            }
        } catch (IOException e) {
            throw e;
        } finally {
            if (is != null) {
                is.close();
            }
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * 读取文本文件内容
     *
     * @param filePath 文件所在路径
     * @return 文本内容
     * @throws IOException 异常
     * @author cn.outofmemory
     * @date 2013-1-7
     */
    public static String readFile(String filePath) throws IOException {
        StringBuffer sb = new StringBuffer();
        FileUtil.readToBuffer(sb, filePath);
        return sb.toString();
    }

    /**
     * 计算传输速率，速率低于1MB，换成KB
     *
     * @param totalByte    文件总大小(字节)
     * @param milliSeconds 耗时(毫秒)
     * @return
     */
    public static String transferSpeed(long totalByte, long milliSeconds) {
        String str = "0 Byte/S";
        if (totalByte > 0 && milliSeconds > 0) {
            double size = Double.valueOf(totalByte);
            double time = Double.valueOf(milliSeconds);
            double result = size * 1000 / time;
            str = String.format("%.1f", result) + " Byte/S";
            if (result > 1024) {
                result = result / 1024;
                str = String.format("%.1f", result) + " KB/S";
            }
            if (result > 1024) {
                result = result / 1024;
                str = String.format("%.1f", result) + " MB/S";
            }
            if (result > 1024) {
                result = result / 1024;
                str = String.format("%.1f", result) + " GB/S";
            }
        }
        return str;
    }

    /**
     * 文件拷贝
     *
     * @param sourcePath 源文件路径
     * @param destPath   目标文件路径
     */
    public static void copyFile(String sourcePath, String destPath) {
        File file = new File(destPath);
        if (!file.exists()) {
            logger.error("目标文件夹：{} 不存在！ 重新创建！", destPath);
            file.mkdirs();
        }
        int byteread = 0;
        File oldFile = new File(sourcePath);
        File newFile = new File(destPath + sourcePath.substring(sourcePath.lastIndexOf(File.separator), sourcePath.length()));
        if (oldFile.length() == newFile.length()) {
            logger.error("文件 {} 已存在！", newFile);
            return;
        }
        try {
            if (oldFile.exists()) {
                destPath = destPath + File.separator + oldFile.getName();
                InputStream inputStream = new FileInputStream(oldFile);
                FileOutputStream fos = new FileOutputStream(destPath);
                byte[] buffer = new byte[1024];
                while ((byteread = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, byteread);
                }
                if (inputStream != null) {
                    inputStream.close();
                }
                if (fos != null) {
                    fos.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("复制文件出错！");
        }

    }

    /**
     * 文件剪切
     *
     * @param sourcePath 源文件路径
     * @param destPath   目标文件路径
     */
    public static void moveFile(String sourcePath, String destPath) {
        copyFile(sourcePath, destPath);
        if (!sourcePath.substring(0, sourcePath.lastIndexOf(File.separator)).equals(destPath)) {
            delFile(sourcePath);
        }
    }

    /**
     * 文件删除
     *
     * @param filePath 文件路径
     */
    public static boolean delFile(String filePath) {
        logger.debug("Delete File : " + filePath);
        return delFile(new File(filePath));
    }

    /**
     * 文件删除
     *
     * @param filePath 文件路径
     */
    public static boolean delFile(File filePath) {

        if (!filePath.exists()) {
            logger.error("待删除的文件/文件夹不存在！ 【{}】", filePath.getAbsolutePath());
            return false;
        }

        if (filePath.isFile()) {
            return filePath.delete();
        } else {
            File[] files = filePath.listFiles();
            for (File f : files) {
                delFile(f);
            }
            return filePath.delete();
        }

    }

    /**
     * 文件删除
     *
     * @param file 文件路径
     */
    public static boolean isInUse(File file) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "rw");
            FileChannel fc = raf.getChannel();
            FileLock fl = fc.tryLock();
            if (fl.isValid()) {
                fl.close();
                fc.close();
                return false;
            }
        } catch (Exception e) {
            logger.error(e.toString());
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return true;
    }

    /**
     * 保存输入流到文件，inputstream并未关闭
     *
     * @param inputStream
     * @param toFile
     */
    public static void streamToFile(InputStream inputStream, File toFile) {
        try {
            FileOutputStream fos = new FileOutputStream(toFile);
            byte[] buf = new byte[BUFFER_SIZE];
            int i;
            while ((i = inputStream.read(buf)) != -1) {
                fos.write(buf, 0, i);
            }
            fos.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 保存输入流到文件，并关闭流
     *
     * @param inputStream
     * @param toFile
     */
    public static void streamToFileAndClose(InputStream inputStream, File toFile) {
        try {
            FileOutputStream fos = new FileOutputStream(toFile);
            byte[] buf = new byte[BUFFER_SIZE];
            int i;
            while ((i = inputStream.read(buf)) != -1) {
                fos.write(buf, 0, i);
            }
            fos.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 文件追加写入字符串
     *
     * @param file
     * @param str
     */
    public static void appendWrite(File file, String str) {
        try {
            if (!file.exists()) {
                file.createNewFile();
            }

            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
            FileWriter writer = new FileWriter(file, true);
            writer.write(str);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取当前目录及子目录下所有文件
     *
     * @param dirPath
     * @return
     */
    public static List<File> listAllFiles(String dirPath, FileFilter filter) {
        List<File> list = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            logger.error("Directory not exist! Dir : {}", dirPath);
            return null;
        }

        Queue<File> subDirs = new LinkedBlockingQueue<>();
        for (File f : dir.listFiles(filter)) {
            if (f.isDirectory()) {
                subDirs.offer(f);
            } else {
                list.add(f);
            }
        }
        for (; !subDirs.isEmpty(); ) {
            File d = subDirs.poll();
            for (File f : d.listFiles(filter)) {
                if (f.isDirectory()) {
                    subDirs.offer(f);
                } else {
                    list.add(f);
                }
            }
        }

        return list;
    }

    /**
     * 获取当前目录及子目录下所有文件
     *
     * @param dirPath
     * @return
     */
    public static List<File> listAllFiles(String dirPath) {
        List<File> list = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }

        Queue<File> subDirs = new LinkedBlockingQueue<>();
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                subDirs.offer(f);
            } else {
                list.add(f);
            }
        }
        for (; !subDirs.isEmpty(); ) {
            File d = subDirs.poll();
            for (File f : d.listFiles()) {
                if (f.isDirectory()) {
                    subDirs.offer(f);
                } else {
                    list.add(f);
                }
            }
        }

        return list;
    }

    /**
     * 目录深度查询包括关键字key的文件
     *
     * @param dirPath
     * @param key
     * @return
     */
    public static List<File> searchFile(String dirPath, final String key) {
        FileFilter filter = new FileFilter() {
            public boolean accept(File file) {
                if (file.isFile()) {
                    if (file.getName().toLowerCase().contains(key.toLowerCase())) {
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    return true;
                }
            }
        };
        return listAllFiles(dirPath, filter);
    }

    /**
     * 指定大小，读取文件内容到byte数组中
     */
    public static String readFile(String filepath, int bufferSize) {
        long st = System.currentTimeMillis();
        File file = new File(filepath);
        if (!file.exists()) {
            logger.warn("file is not exist! path:" + file.getAbsolutePath());
            return null;
        }

        logger.info("open file and read path:" + file.getAbsolutePath());
        char[] buffer = new char[bufferSize];
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
            reader.read(buffer, 0, bufferSize);
            reader.close();
        } catch (Exception e) {
            logger.error("readFile() exception. path:" + file.getAbsolutePath(), e);
        }
        // System.out.println("Cost Time : " + (System.currentTimeMillis() -
        // st));
        return new String(buffer);
    }

    /**
     * 指定行数，读取文件内容到字符串中
     */
    public static String readFileLine(String filepath, int lineCount) {
        long st = System.currentTimeMillis();
        File file = new File(filepath);
        if (!file.exists()) {
            logger.warn("file is not exist! path:" + file.getAbsolutePath());
            return null;
        }

        // logger.info("open file and read path:" + file.getAbsolutePath());
        StringBuffer sb = new StringBuffer();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
            String line = null;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
                count++;
                if (count >= lineCount) {
                    break;
                }
            }
            reader.close();
        } catch (Exception e) {
            logger.error("readFile() exception. path:" + file.getAbsolutePath(), e);
        }
        // System.out.println("Cost Time : " + (System.currentTimeMillis() -
        // st));
        return new String(sb);
    }

    /**
     * 指定行数，读取文件内容到字符串中
     */
    public static List<String> readFileLines(String filepath, int lineCount) {
        // long st = System.currentTimeMillis();
        File file = new File(filepath);
        if (!file.exists()) {
            logger.warn("file is not exist! path:" + file.getAbsolutePath());
            return null;
        }

        List<String> result = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
            String line = null;
            int fileLineCount = getLineCount(filepath);
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (count >= fileLineCount - lineCount) {
                    result.add(line);
                }
                count++;
            }
            reader.close();
        } catch (Exception e) {
            logger.error("readFileLines() exception. path:" + file.getAbsolutePath(), e);
        }
        // System.out.println("Cost Time : " + (System.currentTimeMillis() -
        // st));
        return result;
    }

    /**
     * 指定行数，读取文件内容到字符串中
     */
    public static List<String> readFileLines(String filepath, int lineCount, String charset) {
        // long st = System.currentTimeMillis();
        File file = new File(filepath);
        if (!file.exists()) {
            logger.warn("file is not exist! path:" + file.getAbsolutePath());
            return null;
        }

        List<String> result = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
            String line = null;
            int fileLineCount = getLineCount(filepath);
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (count >= fileLineCount - lineCount) {
                    result.add(line);
                }
                count++;
            }
            reader.close();
        } catch (Exception e) {
            logger.error("readFileLines() exception. path:" + file.getAbsolutePath(), e);
        }
        // System.out.println("Cost Time : " + (System.currentTimeMillis() -
        // st));
        return result;
    }

    /**
     * 获取文件行数
     */
    public static int getLineCount(String filepath) {
        long st = System.currentTimeMillis();
        File file = new File(filepath);
        if (!file.exists()) {
            logger.warn("file is not exist! path:" + file.getAbsolutePath());
            return 0;
        }
        int count = 0;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
            while ((reader.readLine()) != null) {
                count++;
            }
            reader.close();
        } catch (Exception e) {
            logger.error("getLineCount() exception. path:" + file.getAbsolutePath(), e);
        }
        // System.out.println("Cost Time : " + (System.currentTimeMillis() -
        // st));
        return count;
    }


    /**
     * 打开文件并逐行读取内容
     *
     * @param filepath
     * @param callback
     */
    public static void openReadLine(String filepath, FileReadLineCallback callback) {

        File file = new File(filepath);
        if (!file.exists()) {
            logger.warn("file is not exist! path:" + file.getAbsolutePath());
            return;
        }

        logger.info("open file and read path:" + file.getAbsolutePath());

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
            String line = null;
            while ((line = reader.readLine()) != null) {
                callback.readline(line);
            }

            reader.close();
        } catch (Exception e) {
            logger.error("openReadLine() exception. path:" + file.getAbsolutePath(), e);
        }
    }

    /**
     * 读文件回调接口
     *
     * @author iecas
     */
    public interface FileReadLineCallback {
        /**
         * 读文件每行回调方法
         *
         * @param line
         */
        void readline(String line);
    }

    /**
     * 文件拷贝
     *
     * @param finalFilePath 文件目的路径
     * @param file          源文件
     * @return String
     */
    public static String copyFile(String finalFilePath, File file) {

        try {
            //创建目录
            createDir(finalFilePath);
            if (!file.exists()) {
                logger.info(file.getName() + "文件不存在......");
                return "";
            }
            if (isEngross(file, 12)) {
                File dest = new File((finalFilePath.endsWith(File.separator) ? finalFilePath : finalFilePath + File.separator) + file.getName());
                Files.copy(file.toPath(), dest.toPath());
                logger.info(file.getName() + "文件被拷贝完成......");
            } else {
                logger.warn("复制文件错误--》文件一直占用中；");
                return "";
            }
        } catch (IOException e) {
            logger.error("复制文件错误--》" + e.getMessage());
        }
        return finalFilePath + file.getName();
    }


    /**
     * 创建目录
     *
     * @param destDirName
     * @return
     */
    public static boolean createDir(String destDirName) {
        File dir = new File(destDirName);
        if (dir.exists()) {
            // 判断目录是否存在
            logger.warn("创建目录失败，目标目录已存在！");
            return false;
        }
        if (!destDirName.endsWith(File.separator)) {
            // 结尾是否以"/"结束
            destDirName = destDirName + File.separator;
        }
        if (dir.mkdirs()) {
            // 创建目标目录
            logger.info("创建目录成功！" + destDirName);
            return true;
        } else {
            logger.error("创建目录失败！");
            return false;
        }
    }

    /**
     * 判断文件是否占用(等待释放)
     *
     * @param file 源文件
     * @return 文件锁定结果
     */
    public static boolean isEngross(File file) {

        return isEngross(file, Integer.MAX_VALUE);
    }


    /**
     * 尝试锁定文件，文件占用后等待，每次等待5秒
     *
     * @param file     源文件
     * @param tryTimes 尝试次数
     * @return 文件锁定结果
     */
    public static boolean isEngross(File file, int tryTimes) {

        int times = 0;
        while (times < tryTimes) {
            times++;
            try {
                RandomAccessFile raf = new RandomAccessFile(file, "rw");
                FileChannel fc = raf.getChannel();
                FileLock fl = fc.tryLock();
                if (fl.isValid()) {
                    fl.close();
                    fc.close();
                    raf.close();
                    return true;
                }
            } catch (Exception e) {
                logger.info("文件占用中--》" + e.getMessage());
                try {
                    logger.info("文件占用中--》休息5秒");
                    Thread.sleep(5000);
                    logger.info("文件占用中--》休息5秒完成");
                } catch (InterruptedException e1) {
                    logger.info("文件占用中--》休息5秒失败--》" + e.getMessage());
                }
            }
        }
        return false;
    }

    /**
     * 删除文件
     *
     * @param file
     * @return
     */
    public static Boolean deleteOneFile(File file) {
        if (!file.exists()) {
            logger.error("待删除的文件/文件夹不存在！ 【{}】", file.getAbsolutePath());
            return false;
        }
        if (isEngross(file)) {
            return file.delete();
        } else {
            logger.error("文件占用中，删除失败" + file.getName());
            return false;
        }
    }


    /**
     * 获取文件路径中所有的文件
     * 若文件不存在，返回null
     *
     * @param srcDir 文件夹路径
     * @return
     */
    public static List<String> allFiles(String srcDir) {

        List<String> files = new ArrayList<>();
        File srcFile = new File(srcDir);
        if (!srcFile.exists()) {
            return null;
        }
        for (File item : srcFile.listFiles()) {
            if (item.isDirectory()) {
                List<String> items = allFiles(item.getPath());
                files.addAll(items);
            } else if (item.isFile()) {
                files.add(item.getPath());
            }
        }
        return files;
    }

    /**
     * 将路径中原来的转义符去掉
     * \\ -> \
     *
     * @param path 路径
     * @return 更新后的路径
     */
    public static String formatPath(String path) {

        return path.replace("\\\\", "\\");
    }
}