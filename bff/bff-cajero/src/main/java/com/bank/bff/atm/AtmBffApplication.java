package com.bank.bff.atm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
        "com.bank.bff.atm", "com.bank.bff.client", "com.bank.bff.security", "com.bank.bff.api"
        },
        exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
public class AtmBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(AtmBffApplication.class, args);
    }
}
