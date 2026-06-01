package br.com.fiap.feedback;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.TimeZone;

@ApplicationScoped
public class ApplicationConfig {

    public static final String BRAZIL_ZONE = "America/Sao_Paulo";

    void onStart(@Observes StartupEvent event) {
        TimeZone.setDefault(TimeZone.getTimeZone(BRAZIL_ZONE));
    }
}
