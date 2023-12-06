package com.aircas.ptr.foundry.common.util;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

public class ShellUtil {

	private static final Logger logger = LoggerFactory.getLogger(ShellUtil.class);

	private static Set<String> localIps = IpUtil.getLocalIps();

	// 運行腳本
	public static String runShell(String ip, String username, String password, String shell) {
		long st = System.currentTimeMillis();
		StringBuffer sb = new StringBuffer();

		logger.debug("Local Ips : {}, IP : {}", localIps, ip);

		/// 所执行脚本与管理服务在同一机器，直接执行SHell脚本，不需要通过SSH远程登录
		if (localIps.contains(ip)) {
			CommandUtil.executeCommand(shell, new CommandUtil.CommandOutputCallback() {
				@Override
				public boolean deal(String line) {
					sb.append(line + "\n");
					return false;
				}
			});
			logger.debug("本机执行脚本！！ 脚本：{}，执行结果：{}", shell, sb.toString());
		} else {
			logger.debug("远程执行脚本！！  IP：{}，用户名：{}，密码：{}, 脚本：{}", ip, username, password, shell);

			/// 先检查网络是否通
			boolean isReachable;
			try {
				isReachable = InetAddress.getByName(ip).isReachable(3000);
				if (!isReachable) {
					/// 网络不通，一般是机器IP地址错误或未开机
					logger.error("网络连接失败！！  请检查服务器地址！！！ IP：{}, 脚本：{}", ip, shell);
					return null;
				}
			} catch (UnknownHostException e1) {
				e1.printStackTrace();
				return null;
			} catch (IOException e1) {
				e1.printStackTrace();
				return null;
			}

			/// 检查网络无问题后，使用 SSH 远程连接
			Connection conn = new Connection(ip);
			try {
				try {
					conn.connect();
				} catch (Exception e) {
					logger.debug("服务 : {}，Connection Faild.  Error : {}", shell, e.toString());
					if (e.toString().contains("refused") || e.toString().contains("timed out")
							|| e.toString().contains("NoRouteToHostException")) {
						/// SSH 远程连接异常，一般是机器IP地址错误、未开机或SSH服务问题
						logger.error("远程连接失败！！ IP：{}，用户名：{}，密码：{}, 脚本：{}", ip, username, password, shell);
						return null;
					}
					// logger.error(e.toString());
					return "Connection Error";
				}
				
				boolean isAuthenticated = conn.authenticateWithPassword(username, password);
				
				if (!isAuthenticated) {
					// 第一次远程登录失败，使用解密后的密码再次登录
					String dencryptPassword = EncryptUtil.encryptAndDencrypt(password);
					isAuthenticated = conn.authenticateWithPassword(username, dencryptPassword);
					if (!isAuthenticated) {
						logger.error("用户名密码错误！！ IP：{}，用户名：{}，密码：{}, 脚本：{}", ip, username, password, shell);
						return null;
					}
				}

				Session sess = conn.openSession();
				if (sess == null) {
					logger.error("Session Is Null!");
					return "Session Is Null!";
				}
				String evn = "source /etc/profile";
				// 只允許使用一行命令，即ssh对象只能使用一次execCommand这个方法，多次使用会出现异常
				// sess.execCommand(evn);
				// 使用多个命令用分号隔开
				sess.execCommand(evn + ";" + shell);
				BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(sess.getStdout()));
				String line = null;
				while (stdoutReader != null && (line = stdoutReader.readLine()) != null) {
					sb.append(line + "\n");
				}
				if (sess != null) {
					sess.close();
				}

			} catch (Exception e) {
				logger.error("脚本运行错误！！ IP：{}，用户名：{}，密码：{}, 脚本：{}", ip, username, password, shell);
				// logger.error(e.getMessage());
				return null;
			} finally {
				if (conn != null) {
					conn.close();
				}
			}
		}

		logger.debug("runShell() End. Cost Time : {}. \t IP : {}. \t Command : {}. \t Result : {}",
				(System.currentTimeMillis() - st), ip, shell, sb.toString());
		return sb.toString();
	}

	public static void main(String[] args) throws IOException {
		String ip = "192.168.2.5";
		String shell = "/901/ts-service/ts-service-task/startup.sh";
		String pids = runShell(ip, "root", "9:;<=>", shell);
		System.out.println(pids);
	}

}
