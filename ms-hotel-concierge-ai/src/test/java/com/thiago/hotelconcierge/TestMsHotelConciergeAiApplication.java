package com.thiago.hotelconcierge;

import org.springframework.boot.SpringApplication;

public class TestMsHotelConciergeAiApplication {

	public static void main(String[] args) {
		SpringApplication.from(MsHotelConciergeAiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
