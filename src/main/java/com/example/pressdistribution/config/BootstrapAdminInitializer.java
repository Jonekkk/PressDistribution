package com.example.pressdistribution.config;

import com.example.pressdistribution.service.BootstrapAdminService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final BootstrapAdminService bootstrapAdminService;

    public BootstrapAdminInitializer(BootstrapAdminService bootstrapAdminService) {
        this.bootstrapAdminService = bootstrapAdminService;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrapAdminService.createBootstrapAdminIfNeeded();
    }
}
