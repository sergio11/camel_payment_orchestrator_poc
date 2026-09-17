package com.poc.processor.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class AuditPipelineRouteUnitTest {

    private CamelContext context;
    private ProducerTemplate producerTemplate;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        Properties props = new Properties();
        props.setProperty("kafka.topic.audit", "payments.events.audit");
        context.getPropertiesComponent().setOverrideProperties(props);

        KafkaComponent kafka = new KafkaComponent();
        KafkaConfiguration kafkaConfig = new KafkaConfiguration();
        kafkaConfig.setBrokers("localhost:9092");
        kafka.setConfiguration(kafkaConfig);
        context.addComponent("kafka", kafka);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        AuditPipelineRoute route = new AuditPipelineRoute();
        setField(route, "objectMapper", objectMapper);

        context.addRoutes(route);
        context.start();
        producerTemplate = context.createProducerTemplate();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (producerTemplate != null) producerTemplate.stop();
        if (context != null) context.stop();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("audit-pipeline route should be registered")
    void testRouteRegistered() {
        assertNotNull(context.getRoute("audit-pipeline"));
    }

    @Test
    @DisplayName("audit-pipeline should have the correct route ID")
    void testRouteId() {
        assertEquals("audit-pipeline", context.getRoute("audit-pipeline").getRouteId());
    }
}
