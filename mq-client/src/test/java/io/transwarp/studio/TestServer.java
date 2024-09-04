package io.transwarp.studio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"io.transwarp.studio"})
public class TestServer {
  public static void main(String[] args) {
    try{
      SpringApplication.run(TestServer.class, args);
    }catch (Exception ex){
      ex.printStackTrace();
    }
  }
}
