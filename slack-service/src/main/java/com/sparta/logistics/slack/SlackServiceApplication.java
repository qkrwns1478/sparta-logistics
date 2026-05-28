package com.sparta.logistics.slack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication(scanBasePackages = {
        "com.sparta.logistics.slack",
        "com.sparta.logistics.common"
})
public class SlackServiceApplication {

    public static void main(String[] args) {

        SpringApplication.run(SlackServiceApplication.class, args);
    }

}
