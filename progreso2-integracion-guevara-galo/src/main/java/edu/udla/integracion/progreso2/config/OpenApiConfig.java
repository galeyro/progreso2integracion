package edu.udla.integracion.progreso2.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI salud360OpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Salud360 - API de Integración de Citas Médicas")
                        .description(
                            "Sistema de integración para el procesamiento de citas médicas. " +
                            "Implementa patrones de integración empresarial con Apache Camel 4: " +
                            "Point-to-Point (facturación), Publish/Subscribe (eventos de citas) " +
                            "y File Transfer (auditoría CSV y log de rechazos). " +
                            "El broker de mensajería es RabbitMQ gestionado con Docker Compose."
                        )
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Galo Guevara")
                                .email("galo.guevara@udla.edu.ec")
                        )
                );
    }
}
