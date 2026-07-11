package com.thiago.hotelconcierge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class MsHotelConciergeAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MsHotelConciergeAiApplication.class, args);
    }
}
