package com.atc.trajectory4d;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class Trajectory4dApplication {

    public static void main(String[] args) {
        SpringApplication.run(Trajectory4dApplication.class, args);
    }
}
