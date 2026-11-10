package com.campus.repair;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CpApplication {

    public static void main(String[] args) {
        SpringApplication.run(CpApplication.class, args);
    }
}
