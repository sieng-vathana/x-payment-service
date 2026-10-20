package com.x.payment;

import com.x.payment.config.BakongApiProperties;
import com.x.payment.config.KhqrPayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({KhqrPayProperties.class, BakongApiProperties.class})
public class XPaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(XPaymentServiceApplication.class, args);
	}

}
