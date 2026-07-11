package com.thiago.hotelinfo.config;

import com.thiago.hotelinfo.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {
    private final ServiceTypeRepository serviceTypeRepository;

    @Bean
    ApplicationRunner logStartup() {
        return args -> log.info("ms-hotel-info started. Service types loaded: {}", serviceTypeRepository.count());
    }
}
