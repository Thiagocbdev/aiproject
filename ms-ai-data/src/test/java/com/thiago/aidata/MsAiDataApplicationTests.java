package com.thiago.aidata;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires Redis Stack and PostgreSQL - run with docker-compose up redis postgres")
class MsAiDataApplicationTests {

    @Test
    void contextLoads() {
    }
}
