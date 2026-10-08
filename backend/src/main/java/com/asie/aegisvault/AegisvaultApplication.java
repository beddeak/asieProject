package com.asie.aegisvault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class AegisvaultApplication {

  public static void main(String[] args) {
    SpringApplication.run(AegisvaultApplication.class, args);
  }
}
