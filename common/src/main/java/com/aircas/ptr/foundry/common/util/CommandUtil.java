package com.aircas.ptr.foundry.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 命令行执行工具类
 */
public class CommandUtil {

    private static final Logger logger = LoggerFactory.getLogger(CommandUtil.class);

    /**
     * 执行命令行命令
     *
     * @param cmd
     */
    public static void executeCommand(String cmd) {
        executeCommand(cmd, new CommandOutputCallback() {

            @Override
            public boolean deal(String line) {
                return false;
            }

        });
    }

    /**
     * 执行命令行命令，并对输出行回调处理
     *
     * @param cmd
     * @param callback
     */
    public static void executeCommand(String cmd, CommandOutputCallback callback) {

        InputStream fis = null;
        // 用一个读输出流类去读
        InputStreamReader isr = null;
        // 用缓冲器读行
        BufferedReader br = null;
        try {
            logger.debug("executeCommand() cmd : " + cmd);
            Process proc = Runtime.getRuntime().exec(cmd);
            fis = proc.getInputStream();
            // 用一个读输出流类去读
            isr = new InputStreamReader(fis);
            // 用缓冲器读行
            br = new BufferedReader(isr);
            // 直到读完为止
            String line = null;
            while ((line = br.readLine()) != null) {
                logger.debug(line);
                if (callback.deal(line))
                    break;
            }
            proc.destroy();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
                if (isr != null) {
                    isr.close();
                }
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 命令行输出回调接口
     *
     * @author hhm
     */
    public interface CommandOutputCallback {
        /**
         * 输出行回调方法
         *
         * @param line
         * @return
         */
        boolean deal(String line);
    }
}
