package edu.udla.integracion.progreso2.routes;

import edu.udla.integracion.progreso2.model.AppointmentEvent;
import edu.udla.integracion.progreso2.model.CitaRequest;
import edu.udla.integracion.progreso2.model.BillingMessage;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class CitaIntegrationRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:startIntegration")
            .routeId("citaIntegrationRoute")
            .log("Procesando cita recibida: ${body}")

            // Guardar el objeto CitaRequest original en las propiedades del exchange
            .setProperty("originalRequest", body())

            // =========================================================================
            // RF2: Integración con sistema de facturación usando Point-to-Point
            // =========================================================================
            .process(exchange -> {
                CitaRequest req = exchange.getIn().getBody(CitaRequest.class);
                BillingMessage billing = BillingMessage.builder()
                        .idCita(req.getIdCita())
                        .paciente(req.getPaciente())
                        .especialidad(req.getEspecialidad())
                        .valor(req.getValor())
                        .build();
                exchange.getIn().setBody(billing);
            })
            .marshal().json(JsonLibrary.Jackson)
            .to("spring-rabbitmq:billing-exchange?routingKey=billing-routing-key")
            .log("Mensaje P2P de facturación enviado exitosamente a RabbitMQ.")

            // Restaurar el payload original en el body para las siguientes etapas
            .setBody(exchangeProperty("originalRequest"))

            // =========================================================================
            // RF3: Distribución de evento usando Publish/Subscribe
            // =========================================================================
            .process(exchange -> {
                CitaRequest req = exchange.getIn().getBody(CitaRequest.class);
                AppointmentEvent event = AppointmentEvent.builder()
                        .idCita(req.getIdCita())
                        .paciente(req.getPaciente())
                        .correo(req.getCorreo())
                        .especialidad(req.getEspecialidad())
                        .fechaCita(req.getFechaCita())
                        .sede(req.getSede())
                        .build();
                exchange.getIn().setBody(event);
            })
            .marshal().json(JsonLibrary.Jackson)
            .to("spring-rabbitmq:appointments.events")
            .log("Evento Pub/Sub publicado exitosamente a RabbitMQ.")

            // Restaurar el payload original en el body para las siguientes etapas
            .setBody(exchangeProperty("originalRequest"))

            // TODO: Escribir la cita en formato CSV en la ruta local configurada:
            // Path: 'data/outbox/auditoria-citas.csv'
            
            // =========================================================================

            .end();
    }
}
