package com.thiago.hotelinfo;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires PostgreSQL - run with docker-compose up postgres")
class MsHotelInfoApplicationTests {
    @Test
    void contextLoads() {}
}
