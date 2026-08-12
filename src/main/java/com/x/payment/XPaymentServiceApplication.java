package com.x.payment;

import com.x.payment.config.KhqrPayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(KhqrPayProperties.class)
public class XPaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(XPaymentServiceApplication.class, args);
	}

}
