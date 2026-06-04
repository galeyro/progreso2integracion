package edu.udla.integracion.progreso2.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public ApplicationRunner initializeRabbitAdmin(RabbitAdmin rabbitAdmin) {
        return args -> {
            try {
                System.out.println(">> Inicializando colas y exchanges en RabbitMQ de forma proactiva...");
                rabbitAdmin.initialize();
                System.out.println(">> ¡Inicialización de RabbitMQ completada con éxito!");
            } catch (Exception e) {
                System.err.println(">> Advertencia: No se pudo conectar a RabbitMQ en el arranque: " + e.getMessage());
            }
        };
    }

    // =========================================================================
    // Canal Point-to-Point (Facturación)
    // =========================================================================

    @Bean
    public Queue billingQueue() {
        return new Queue("billing.queue", true, false, false);
    }

    @Bean
    public DirectExchange billingExchange() {
        return new DirectExchange("billing-exchange");
    }

    @Bean
    public Binding billingBinding(Queue billingQueue, DirectExchange billingExchange) {
        return BindingBuilder.bind(billingQueue)
                .to(billingExchange)
                .with("billing-routing-key");
    }

    // =========================================================================
    // Canal Publish/Subscribe (Eventos de Citas)
    // =========================================================================

    @Bean
    public FanoutExchange appointmentsEventsExchange() {
        return new FanoutExchange("appointments.events");
    }

    @Bean
    public Queue notificationsQueue() {
        return new Queue("notifications.queue", true, false, false);
    }

    @Bean
    public Queue analyticsQueue() {
        return new Queue("analytics.queue", true, false, false);
    }

    @Bean
    public Binding notificationsBinding(Queue notificationsQueue, FanoutExchange appointmentsEventsExchange) {
        return BindingBuilder.bind(notificationsQueue)
                .to(appointmentsEventsExchange);
    }

    @Bean
    public Binding analyticsBinding(Queue analyticsQueue, FanoutExchange appointmentsEventsExchange) {
        return BindingBuilder.bind(analyticsQueue)
                .to(appointmentsEventsExchange);
    }
}
