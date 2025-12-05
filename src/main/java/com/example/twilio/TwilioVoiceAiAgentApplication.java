package com.example.twilio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example.twilio","com.example.salesforce"})
public class TwilioVoiceAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TwilioVoiceAiAgentApplication.class, args);
    }
}


