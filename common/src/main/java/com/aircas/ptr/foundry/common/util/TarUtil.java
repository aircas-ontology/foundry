package com.aircas.ptr.foundry.common.util;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class TarUtil {

    private static final Logger LOG = LoggerFactory.getLogger(TarUtil.class);

    public static void packFiles(String srcPath, String targetFile) {
        File src = new File(srcPath);
        if (src.exists() && src.isDirectory()) {
            packFiles(src.listFiles(), targetFile);
        }
    }

    public static void packFiles(File[] srcFiles, String targetFile) {
        if (srcFiles == null) {
            return;
        }
        File target = new File(targetFile);
        long startTime = System.currentTimeMillis();
        LOG.info("**********打包开始[" + targetFile + "]**********");
        try (
                FileOutputStream out = new FileOutputStream(target);
                TarArchiveOutputStream os = new TarArchiveOutputStream(out)
        ) {
            os.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            int fileCount = 0;
            for (File srcFile : srcFiles) {
                fileCount += putArch(os, srcFile);
            }
            os.flush();
            os.close();
            long endTime = System.currentTimeMillis();
            LOG.info("**********打包完成[" + targetFile + "]**********");
            LOG.info("用时[" + (endTime - startTime) / 1000.0 + "]秒，打包后文件大小[" + targetFile.length() + "]字节，共计[" + fileCount + "]个文件");
        } catch (IOException e) {
            e.printStackTrace();
            LOG.error("*********!打包失败[" + targetFile + "]**********");
        }
    }

    private static int putArch(TarArchiveOutputStream os, File file) {
        return putArch(os, "", file);
    }

    private static int putArch(TarArchiveOutputStream os, String subPath, File file) {
        if (file.isDirectory()) {
            return putArchDirectory(os, subPath, file);
        } else {
            return putArchFile(os, subPath, file);
        }
    }

    private static int putArchDirectory(TarArchiveOutputStream os, String subPath, File file) {
        int count = 0;
        if (file == null) {
            return count;
        }
        File[] files = file.listFiles();
        if (files == null) {
            return count;
        }
        for (File subFile : files) {
            count += putArch(os, subPath + file.getName() + "/", subFile);
        }
        return count;
    }

    private static int putArchFile(TarArchiveOutputStream os, String subPath, File file) {
        String archFileName = subPath + file.getName();
        try {
            os.putArchiveEntry(new TarArchiveEntry(file, archFileName));
            IOUtils.copy(new BufferedInputStream(new FileInputStream(file)), os);
            os.closeArchiveEntry();
            LOG.debug("*****添加文件[" + archFileName + "]完成。");
            return 1;
        } catch (IOException e) {
            LOG.debug("*****添加文件[" + archFileName + "]时出现异常!");
            e.printStackTrace();
            return 0;
        }
    }

    public static void unpackFiles(File tarFile, String targetPath) {
        long startTime = System.currentTimeMillis();
        LOG.debug("----------解包开始[" + targetPath + "]----------");
        try (
                FileInputStream fis = new FileInputStream(tarFile);
                BufferedInputStream bis = new BufferedInputStream(fis);
                TarArchiveInputStream tais = new TarArchiveInputStream(bis)
        ) {
            TarArchiveEntry entry;
            while ((entry = tais.getNextTarEntry()) != null) {
                String dir = targetPath + File.separator + entry.getName();
                File dirFile = new File(dir);
                if (!dirFile.getParentFile().exists()) {
                    dirFile.getParentFile().mkdirs();
                }
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dirFile));
                IOUtils.copy(tais, bos);
                LOG.debug("-----解压文件[" + dir + "]完成。");
            }
            tais.close();
            long endTime = System.currentTimeMillis();
            LOG.debug("----------解压完成[" + targetPath + "]----------");
            LOG.debug("用时[" + (endTime - startTime) / 1000.0 + "]秒，解压前文件大小[" + tarFile.length() + "]字节，共计[" + "?" + "]个文件");
        } catch (IOException e) {
            e.printStackTrace();
            LOG.debug("---------!解压失败[" + targetPath + "]----------");
        }
    }

}
