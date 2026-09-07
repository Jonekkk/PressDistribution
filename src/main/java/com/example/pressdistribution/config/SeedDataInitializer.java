package com.example.pressdistribution.config;

import com.example.pressdistribution.service.SeedDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SeedDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataInitializer.class);

    private final SeedProperties properties;
    private final SeedDataService seedDataService;

    public SeedDataInitializer(SeedProperties properties, SeedDataService seedDataService) {
        this.properties = properties;
        this.seedDataService = seedDataService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.debug("Seed data loading is disabled (app.seed.enabled=false)");
            return;
        }
        log.info("Seed data loading is enabled — starting seed data initialization");
        seedDataService.loadSeedData();
    }
}
