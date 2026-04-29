package com.superchat.auth.config;

import com.superchat.auth.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoUserInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DemoUserInitializer.class);

    private final UserService userService;

    public DemoUserInitializer(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(String... args) {
        String demoPhone = "demo";
        if (userService.findByPhone(demoPhone).isEmpty()) {
            userService.registerUser(demoPhone, "demo", "Demo");
            logger.info("Seeded default demo user");
        }
    }
}