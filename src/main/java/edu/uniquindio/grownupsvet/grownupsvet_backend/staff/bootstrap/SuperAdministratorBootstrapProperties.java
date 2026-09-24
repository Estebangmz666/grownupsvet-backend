package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Opt-in provisioning; values come from the environment and must never be logged. */
@ConfigurationProperties("grownupsvet.staff.bootstrap")
public record SuperAdministratorBootstrapProperties(@DefaultValue("false") boolean enabled,
                                                    @DefaultValue("") String email,
                                                    @DefaultValue("") String password) {
    @Override
    public String toString() {
        return "SuperAdministratorBootstrapProperties[enabled=" + enabled + ", credentials=[REDACTED]]";
    }
}
