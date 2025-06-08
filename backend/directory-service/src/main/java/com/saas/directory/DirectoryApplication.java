package com.saas.directory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableRetry
public class DirectoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DirectoryApplication.class, args);
    }

}
