package com.apitesthub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.apitesthub.mapper")
public class ApitestHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApitestHubApplication.class, args);
    }
}
