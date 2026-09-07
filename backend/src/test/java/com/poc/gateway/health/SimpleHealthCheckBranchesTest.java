package com.poc.gateway.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.common.KafkaFuture;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class SimpleHealthCheckBranchesTest {

    private SimpleHealthCheck check(String servers) {
        SimpleHealthCheck c = new SimpleHealthCheck();
        c.bootstrapServers = servers;
        return c;
    }

    @Test
    @DisplayName("blank servers returns UP without Kafka check")
    void blankServers_up() {
        HealthCheckResponse r = check("  ").call();
        assertTrue(r.getStatus() == HealthCheckResponse.Status.UP);
        HealthCheckResponse r2 = check("").call();
        assertTrue(r2.getStatus() == HealthCheckResponse.Status.UP);
    }

    @Test
    @DisplayName("reachable Kafka returns UP with data")
    void kafkaReachable_up() throws Exception  {
        AdminClient admin = mock(AdminClient.class);
        ListTopicsResult listings = mock(ListTopicsResult.class);
        KafkaFuture future = mock(KafkaFuture.class);
        try (MockedStatic<AdminClient> mocked = mockStatic(AdminClient.class)) {
            mocked.when(() -> AdminClient.create(anyMap())).thenReturn(admin);
            when(admin.listTopics()).thenReturn(listings);
            when(listings.listings()).thenReturn(future);
            when(future.get(anyLong(), any())).thenReturn(List.of());

            HealthCheckResponse r = check("localhost:9092").call();

            assertTrue(r.getStatus() == HealthCheckResponse.Status.UP);
            verify(admin).close();
        }
    }

    @Test
    @DisplayName("unreachable Kafka returns DOWN with message")
    void kafkaDown_down() throws Exception  {
        AdminClient admin = mock(AdminClient.class);
        ListTopicsResult listings = mock(ListTopicsResult.class);
        KafkaFuture future = mock(KafkaFuture.class);
        try (MockedStatic<AdminClient> mocked = mockStatic(AdminClient.class)) {
            mocked.when(() -> AdminClient.create(anyMap())).thenReturn(admin);
            when(admin.listTopics()).thenReturn(listings);
            when(listings.listings()).thenReturn(future);
            when(future.get(anyLong(), any()))
                .thenThrow(new ExecutionException("connection refused", new RuntimeException()));

            HealthCheckResponse r = check("localhost:1").call();

            assertTrue(r.getStatus() == HealthCheckResponse.Status.DOWN);
            verify(admin).close();
        }
    }

    @Test
    @DisplayName("Kafka failure without message uses toString fallback")
    void kafkaFailureNullMessage_fallback() throws Exception  {
        AdminClient admin = mock(AdminClient.class);
        ListTopicsResult listings = mock(ListTopicsResult.class);
        KafkaFuture future = mock(KafkaFuture.class);
        try (MockedStatic<AdminClient> mocked = mockStatic(AdminClient.class)) {
            mocked.when(() -> AdminClient.create(anyMap())).thenReturn(admin);
            when(admin.listTopics()).thenReturn(listings);
            when(listings.listings()).thenReturn(future);
            when(future.get(anyLong(), any()))
                .thenThrow(new ExecutionException(null, new RuntimeException("root")));

            HealthCheckResponse r = check("localhost:1").call();

            assertTrue(r.getStatus() == HealthCheckResponse.Status.DOWN);
        }
    }

    @Test
    @DisplayName("AdminClient creation failure returns DOWN")
    void adminCreateFails_down() {
        try (MockedStatic<AdminClient> mocked = mockStatic(AdminClient.class)) {
            mocked.when(() -> AdminClient.create(anyMap())).thenThrow(new RuntimeException("no client"));

            HealthCheckResponse r = check("localhost:1").call();

            assertTrue(r.getStatus() == HealthCheckResponse.Status.DOWN);
        }
    }
}


