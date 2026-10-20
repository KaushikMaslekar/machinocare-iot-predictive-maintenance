package com.machinocare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MachinoCare Backend Entry Point.
 * 
 * Orchestrates:
 * 1. MQTT Ingestion Pipeline (Spring Integration)
 * 2. Device & Machine Telemetry Services
 * 3. MongoDB 7.0 Persistence & History
 * 4. REST APIs & Alert Management
 */
@SpringBootApplication
public class MachinoCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(MachinoCareApplication.class, args);
    }
}
