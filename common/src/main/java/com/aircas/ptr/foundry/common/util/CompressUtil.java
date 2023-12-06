package com.aircas.ptr.foundry.common.util;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 文件压缩
 */
public class CompressUtil {

    private static final Logger LOG = LoggerFactory.getLogger(CompressUtil.class);

    private static final int BUFFER_SIZE = 2 * 1024;

    /**
     * 压缩本地文件
     */
    public static void toZip(String srcDir, OutputStream out, boolean KeepDirStructure) {
        long start = System.currentTimeMillis();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            File sourceFile = new File(srcDir);
            compressToZip(sourceFile, zos, sourceFile.getName(), KeepDirStructure);
            long end = System.currentTimeMillis();
            delTempChild(sourceFile);
            System.out.println("压缩完成，耗时：" + (end - start) + " ms");
        } catch (Exception e) {
            throw new RuntimeException("zip error from ZipUtils", e);
        }
    }

    /**
     * 递归删除文件夹
     *
     * @param file 待删除文件,如File(C:\Users\test)
     */
    public static void delTempChild(File file) {
        if (file.isDirectory()) {
            String[] children = file.list();//获取文件夹下所有子文件夹
            for (String child : children) {
                delTempChild(new File(file, child));
            }
        }
        file.delete();
    }

    private static void compressToZip(File sourceFile,
                                      ZipOutputStream zos,
                                      String name,
                                      boolean KeepDirStructure) throws Exception {
        byte[] buf = new byte[BUFFER_SIZE];
        if (sourceFile.isFile()) {
            zos.putNextEntry(new ZipEntry(name));
            int len;
            FileInputStream in = new FileInputStream(sourceFile);
            while ((len = in.read(buf)) != -1) {
                zos.write(buf, 0, len);
            }
            zos.closeEntry();
            in.close();
        } else {
            File[] listFiles = sourceFile.listFiles();
            if (listFiles == null || listFiles.length == 0) {
                if (KeepDirStructure) {
                    zos.putNextEntry(new ZipEntry(name + "/"));
                    zos.closeEntry();
                }
            } else {
                for (File file : listFiles) {
                    if (KeepDirStructure) {
                        compressToZip(file, zos, name + "/" + file.getName(), KeepDirStructure);
                    } else {
                        compressToZip(file, zos, file.getName(), KeepDirStructure);
                    }
                }
            }
        }
    }

    /**
     * 解压文件到指定目录
     */
    public static void unZipFile(String zipPath, String descDir) throws IOException {
        LOG.info("文件:{}. 解压路径:{}. 解压开始.", zipPath, descDir);
        long start = System.currentTimeMillis();
        try {
            File zipFile = new File(zipPath);
            System.err.println(zipFile.getName());
            if (!zipFile.exists()) {
                throw new IOException("需解压文件不存在.");
            }
            File pathFile = new File(descDir);
            if (!pathFile.exists()) {
                pathFile.mkdirs();
            }
            ZipFile zip = new ZipFile(zipFile, Charset.forName("GBK"));
            for (Enumeration entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                String zipEntryName = entry.getName();
                System.err.println(zipEntryName);
                InputStream in = zip.getInputStream(entry);
                String outPath = (descDir + File.separator + zipEntryName).replaceAll("\\*", "/");
                System.err.println(outPath);
                // 判断路径是否存在,不存在则创建文件路径
                File file = new File(outPath.substring(0, outPath.lastIndexOf('/')));
                if (!file.exists()) {
                    file.mkdirs();
                }
                // 判断文件全路径是否为文件夹,如果是上面已经上传,不需要解压
                if (new File(outPath).isDirectory()) {
                    continue;
                }
                // 输出文件路径信息
                OutputStream out = new FileOutputStream(outPath);
                byte[] buf1 = new byte[1024];
                int len;
                while ((len = in.read(buf1)) > 0) {
                    out.write(buf1, 0, len);
                }
                in.close();
                out.close();
            }
            LOG.info("文件:{}. 解压路径:{}. 解压完成. 耗时:{}ms. ", zipPath, descDir, System.currentTimeMillis() - start);
        } catch (Exception e) {
            LOG.info("文件:{}. 解压路径:{}. 解压异常:{}. 耗时:{}ms. ", zipPath, descDir, e, System.currentTimeMillis() - start);
            throw new IOException(e);
        }
    }

    public static File byteToFile(byte[] bfile, String fileName) {
        BufferedOutputStream bos = null;
        FileOutputStream fos = null;
        File file = null;
        try {
            file = new File(fileName);
            fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            bos.write(bfile);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
        return file;
    }

    /**
     * 7z文件压缩入口
     *
     * @param inputFile      待压缩文件夹/文件名
     * @param outputFilePath 生成的压缩包路径
     * @throws Exception
     */
    public static void to7z(String inputFile, String outputFilePath) throws Exception {
        long start = System.currentTimeMillis();
        File input = new File(inputFile);
        if (!input.exists()) {
            throw new Exception(input.getPath() + "待压缩文件不存在");
        }
        String outputFile = outputFilePath + File.separator + input.getName() + ".7z";//文件路径 + "\\" +文件名.7z
        SevenZOutputFile out = new SevenZOutputFile(new File(outputFile));

        compressTo7z(out, input, null);
        out.close();
        delTempChild(input);
        long end = System.currentTimeMillis();
        System.out.println("压缩完成，耗时：" + (end - start) + " ms");
    }


    /**
     * 7z递归压缩
     *
     * @param out   7z输出流,SevenZOutputFile类型
     * @param input 待压缩文件，如File("C:\Users\test")
     * @param name  待压缩文件的名字
     * @throws IOException
     */
    public static void compressTo7z(SevenZOutputFile out, File input, String name) throws IOException {
        if (name == null) {
            name = input.getName();
        }
        SevenZArchiveEntry entry = null;
        //如果路径为目录（文件夹）
        if (input.isDirectory()) {
            //取出文件夹中的文件（或子文件夹）
            File[] flist = input.listFiles();

            if (flist.length == 0)//如果文件夹为空，则只需在目的地.7z文件中写入一个目录进入
            {
                entry = out.createArchiveEntry(input, name + "/");
                out.putArchiveEntry(entry);
            } else//如果文件夹不为空，则递归调用compressTo7z，文件夹中的每一个文件（或文件夹）进行压缩
            {
                for (int i = 0; i < flist.length; i++) {
                    compressTo7z(out, flist[i], name + "/" + flist[i].getName());
                }
            }
        } else//如果不是目录（文件夹），即为文件，则先写入目录进入点，之后将文件写入7z文件中
        {
            FileInputStream fos = new FileInputStream(input);
            BufferedInputStream bis = new BufferedInputStream(fos);
            entry = out.createArchiveEntry(input, name);
            out.putArchiveEntry(entry);
            int len = -1;
            //将源文件写入到7z文件中
            byte[] buf = new byte[1024];
            while ((len = bis.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            bis.close();
            fos.close();
            out.closeArchiveEntry();
        }
    }

    /**
     * 7z解压
     *
     * @param inputFile   待解压文件绝对路径，如C:\Users\test.7z
     * @param destDirPath 解压路径,例如:C:\Users
     */
    public static void uncompress7z(String inputFile, String destDirPath) throws Exception {
        File srcFile = new File(inputFile);//获取当前压缩文件
        // 判断源文件是否存在
        if (!srcFile.exists()) {
            throw new Exception(srcFile.getPath() + "所指文件不存在");
        }
        //开始解压
        SevenZFile zIn = new SevenZFile(srcFile);
        SevenZArchiveEntry entry = null;
        File file = null;
        while ((entry = zIn.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                file = new File(destDirPath, entry.getName());
                if (!file.exists()) {
                    new File(file.getParent()).mkdirs();//创建此文件的上级目录
                }
                OutputStream out = new FileOutputStream(file);
                BufferedOutputStream bos = new BufferedOutputStream(out);
                int len = -1;
                byte[] buf = new byte[1024];
                while ((len = zIn.read(buf)) != -1) {
                    bos.write(buf, 0, len);
                }
                // 关流顺序，先打开的后关闭
                bos.close();
                out.close();
            }
        }
    }

    /**
     * 打包到Tar入口，调用compreessToTar
     *
     * @param srcDir 待压缩文件绝对路径,如C:\Users\test.7z
     * @throws Exception
     */
    public static void toTar(String srcDir) throws Exception {
        long start = System.currentTimeMillis();
        File input = new File(srcDir);
        if (!input.exists()) {
            throw new Exception(input.getPath() + "待压缩文件不存在");
        }

        TarArchiveOutputStream tos = new TarArchiveOutputStream(new FileOutputStream(input.getAbsolutePath() + ".tar"));
        String fileName = input.getName();
        compressToTar(tos, input, fileName);

        tos.close();
        delTempChild(input);
        long per = System.currentTimeMillis() - start;
        System.out.println("总用时:" + per);
    }

    /**
     * 打包到tar
     *
     * @param tos      Tar输出流
     * @param input    待压缩文件，如:C:\Users\test
     * @param fileName 待压缩文件名，如:test
     * @throws IOException
     */
    private static void compressToTar(TarArchiveOutputStream tos, File input, String fileName) throws IOException {
        TarArchiveEntry tEntry = null;
        if (fileName == null)
            fileName = input.getName();
        if (input.isDirectory()) {//如果是文件夹
            File[] flist = input.listFiles();
            if (flist.length == 0) {
                //tos.putArchiveEntry(new ArchiveEntry(input.getName() + File.separator));
                tEntry = new TarArchiveEntry(fileName + File.separator);
                tos.putArchiveEntry(tEntry);
                tos.closeArchiveEntry();
            } else {
                for (File file : flist) {
                    compressToTar(tos, file, fileName + File.separator + file.getName());
                }
            }
            //TarArchiveEntry tEntry = new TarArchiveEntry(outputFilePath + File.separator + input.getName());
        } else {//如果是文件
            tEntry = new TarArchiveEntry(fileName);
            tEntry.setSize(input.length());
            tos.putArchiveEntry(tEntry);
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(input));
            byte[] buffer = new byte[1024];
            int read = -1;
            while ((read = bis.read(buffer)) != -1) {
                tos.write(buffer, 0, read);
            }
            bis.close();
            tos.closeArchiveEntry();//这里必须写，否则会失败
        }
    }

    /**
     * tar解包
     *
     * @param inputFile   待解压文件，File类型,如File("C:\Users\test.tar")
     * @param destDirPath 解压到此路径，如C:\Users
     * @throws Exception
     */
    public static void uncompressTar(String inputFile, String destDirPath) throws Exception {
        File srcFile = new File(inputFile);
        if (!srcFile.exists()) {
            throw new Exception(srcFile.getPath() + "所指文件不存在");
        }
        File pathFile = new File(destDirPath);
        if (!pathFile.exists()) {
            pathFile.mkdirs();
        }
        TarArchiveInputStream tis = new TarArchiveInputStream(new FileInputStream(new File(inputFile)));
        TarArchiveEntry tEntry = null;

        while ((tEntry = tis.getNextTarEntry()) != null) {
            if (!tEntry.isDirectory()) {
                File dirFile = new File(destDirPath, tEntry.getName());
                if (!dirFile.exists()) {
                    new File(dirFile.getParent()).mkdirs();//创建此文件的上级目录
                }
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dirFile));
                int cot = -1;
                byte data[] = new byte[1024];
                while ((cot = tis.read(data, 0, 1024)) != -1) {
                    bos.write(data, 0, cot);
                }
                bos.close();
                //tis.close();
            }
        }
    }

    /**
     * 压缩文件入口
     *
     * @param srcDir 待压缩文件绝对路径
     * @param type   压缩到什么格式，可选zip,7z,tar
     * @throws Exception
     */
    public static void compress(String srcDir, String type) throws Exception {
        if (type.equals("zip")) {
            File srcFile = new File(srcDir);
            String targetFileName = srcFile.getParent() + File.separator + srcFile.getName() + ".zip";
            FileOutputStream fos = new FileOutputStream(new File(targetFileName));
            toZip(srcDir, fos, true);
            fos.close();
        } else if (type.equals("7z")) {
            File srcFile = new File(srcDir);
            String tarPath = srcFile.getParent();
            to7z(srcDir, tarPath);
        } else {
            toTar(srcDir);
        }
    }

    /**
     * 解压入口
     *
     * @param compressedFilePath 待解压的压缩文件的路径
     * @throws Exception
     */
    public static void uncompress(String compressedFilePath) throws Exception {
        File file = new File(compressedFilePath);
        String[] typeArray = file.getName().split("\\.");
//        System.out.println(typeArray.length);
//        System.out.println(typeArray[typeArray.length-1]);
        String type = typeArray[typeArray.length - 1];
        if (type.equals("zip")) {
            unZipFile(compressedFilePath, file.getParent());
        } else if (type.equals("7z")) {
            uncompress7z(compressedFilePath, file.getParent());
        } else {
            uncompressTar(compressedFilePath, file.getParent());
        }
    }

    /**
     * 压缩成tar.gz
     *
     * @param compressFilePath
     * @throws IOException
     */
    public static void toTarGZ(String compressFilePath) throws IOException {
        File f2 = new File(compressFilePath);
        File outFile = new File(f2.getAbsolutePath() + ".tar.gz");
        if (!outFile.exists()) {
            outFile.createNewFile();
        }
        FileOutputStream fos = new FileOutputStream(outFile);
        TarArchiveOutputStream taos = new TarArchiveOutputStream(new GZIPOutputStream(new BufferedOutputStream(fos)));
        taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
        taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        addFilesToCompression(taos, f2, ".");
        taos.close();
        fos.close();
    }

    private static void addFilesToCompression(TarArchiveOutputStream taos, File file, String dir) throws IOException {
        taos.putArchiveEntry(new TarArchiveEntry(file, dir));
        if (file.isFile()) {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            IOUtils.copy(bis, taos);
            taos.closeArchiveEntry();
            bis.close();
        } else if (file.isDirectory()) {
            taos.closeArchiveEntry();
            for (File childFile : file.listFiles()) {
                addFilesToCompression(taos, childFile, file.getName());
            }
        }
    }
}
