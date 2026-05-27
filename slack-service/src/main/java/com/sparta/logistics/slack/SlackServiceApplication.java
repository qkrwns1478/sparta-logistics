package com.sparta.logistics.slack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.sparta.logistics.slack",
        "com.sparta.logistics.common"
})
public class SlackServiceApplication {

    public static void main(String[] args) {

        SpringApplication.run(SlackServiceApplication.class, args);
    }

}
