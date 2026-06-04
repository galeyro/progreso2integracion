package edu.udla.integracion.progreso2.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class CitaIntegrationRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:startIntegration")
            .routeId("citaIntegrationRoute")
            .log("Procesando cita recibida: ${body}")

            // =========================================================================
            // PLACEHOLDERS / GANCHOS DE INTEGRACIÓN (Siguientes Pasos)
            // =========================================================================
            
            // TODO: Enviar mensaje al canal Punto a Punto en RabbitMQ (billing.queue)
            // Ejemplo futuro: .to("spring-rabbitmq:billing-exchange?routingKey=billing-routing-key")

            // TODO: Publicar mensaje en el canal Pub/Sub en RabbitMQ (appointments.events)
            // Ejemplo futuro: .to("spring-rabbitmq:events-exchange?routingKey=events-routing-key")

            // TODO: Escribir la cita en formato CSV en la ruta local configurada:
            // Path: 'data/outbox/auditoria-citas.csv'
            
            // =========================================================================

            .end();
    }
}
