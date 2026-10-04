package com.example.battleroyal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BattleRoyalApplication {

	public static void main(String[] args) {
		SpringApplication.run(BattleRoyalApplication.class, args);
	}

}
