package com.youthfit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class YouthfitApplication {

    public static void main(String[] args) {
        SpringApplication.run(YouthfitApplication.class, args);
    }

}
