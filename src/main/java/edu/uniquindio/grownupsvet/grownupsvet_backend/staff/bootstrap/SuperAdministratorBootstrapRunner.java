package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(SuperAdministratorBootstrapProperties.class)
public class SuperAdministratorBootstrapRunner implements ApplicationRunner {
    private final SuperAdministratorBootstrapService service;

    public SuperAdministratorBootstrapRunner(SuperAdministratorBootstrapService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        service.initialize();
    }
}
