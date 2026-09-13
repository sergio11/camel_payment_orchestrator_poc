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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
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

    private SimpleHealthCheck checkWithMockAdmin(AdminClient admin) throws Exception {
        SimpleHealthCheck c = new SimpleHealthCheck();
        c.bootstrapServers = "localhost:9092";
        Field f = SimpleHealthCheck.class.getDeclaredField("adminClient");
        f.setAccessible(true);
        f.set(c, admin);
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
    void kafkaReachable_up() throws Exception {
        AdminClient admin = mock(AdminClient.class);
        ListTopicsResult listings = mock(ListTopicsResult.class);
        KafkaFuture future = mock(KafkaFuture.class);
        when(admin.listTopics()).thenReturn(listings);
        when(listings.listings()).thenReturn(future);
        when(future.get(anyLong(), any())).thenReturn(List.of());

        HealthCheckResponse r = checkWithMockAdmin(admin).call();

        assertTrue(r.getStatus() == HealthCheckResponse.Status.UP);
    }

    @Test
    @DisplayName("unreachable Kafka returns DOWN with message")
    void kafkaDown_down() throws Exception {
        AdminClient admin = mock(AdminClient.class);
        ListTopicsResult listings = mock(ListTopicsResult.class);
        KafkaFuture future = mock(KafkaFuture.class);
        when(admin.listTopics()).thenReturn(listings);
        when(listings.listings()).thenReturn(future);
        when(future.get(anyLong(), any()))
            .thenThrow(new ExecutionException("connection refused", new RuntimeException()));

        HealthCheckResponse r = checkWithMockAdmin(admin).call();

        assertTrue(r.getStatus() == HealthCheckResponse.Status.DOWN);
    }

    @Test
    @DisplayName("Kafka failure without message uses toString fallback")
    void kafkaFailureNullMessage_fallback() throws Exception {
        AdminClient admin = mock(AdminClient.class);
        ListTopicsResult listings = mock(ListTopicsResult.class);
        KafkaFuture future = mock(KafkaFuture.class);
        when(admin.listTopics()).thenReturn(listings);
        when(listings.listings()).thenReturn(future);
        when(future.get(anyLong(), any()))
            .thenThrow(new ExecutionException(null, new RuntimeException("root")));

        HealthCheckResponse r = checkWithMockAdmin(admin).call();

        assertTrue(r.getStatus() == HealthCheckResponse.Status.DOWN);
    }

    @Test
    @DisplayName("AdminClient creation failure leaves null client, returns UP")
    void adminCreateFails_up() throws Exception {
        SimpleHealthCheck c = new SimpleHealthCheck();
        c.bootstrapServers = "localhost:1";
        Field f = SimpleHealthCheck.class.getDeclaredField("adminClient");
        f.setAccessible(true);
        f.set(c, null);

        HealthCheckResponse r = c.call();

        assertTrue(r.getStatus() == HealthCheckResponse.Status.UP);
    }

    @Test
    @DisplayName("init with null servers does not create AdminClient")
    void init_nullServers_noAdminClient() {
        SimpleHealthCheck c = check(null);
        c.init();
        HealthCheckResponse r = c.call();
        assertTrue(r.getStatus() == HealthCheckResponse.Status.UP);
    }

    @Test
    @DisplayName("init with blank servers does not create AdminClient")
    void init_blankServers_noAdminClient() {
        SimpleHealthCheck c = check("  ");
        c.init();
        HealthCheckResponse r = c.call();
        assertTrue(r.getStatus() == HealthCheckResponse.Status.UP);
    }

    @Test
    @DisplayName("init with valid servers creates AdminClient via static factory")
    void init_validServers_createsAdminClient() {
        try (MockedStatic<AdminClient> mocked = mockStatic(AdminClient.class)) {
            AdminClient mockClient = mock(AdminClient.class);
            mocked.when(() -> AdminClient.create(any(Map.class))).thenReturn(mockClient);

            SimpleHealthCheck c = check("localhost:9092");
            c.init();

            mocked.verify(() -> AdminClient.create(any(Map.class)));
        }
    }

    @Test
    @DisplayName("destroy with null AdminClient does not throw")
    void destroy_nullAdmin_noException() {
        SimpleHealthCheck c = check("localhost:9092");
        c.destroy();
    }

    @Test
    @DisplayName("destroy calls AdminClient close successfully")
    void destroy_callsClose() throws Exception {
        AdminClient admin = mock(AdminClient.class);
        SimpleHealthCheck c = checkWithMockAdmin(admin);
        c.destroy();
        verify(admin).close();
    }

    @Test
    @DisplayName("destroy when AdminClient close throws does not propagate")
    void destroy_closeThrows_doesNotThrow() throws Exception {
        AdminClient admin = mock(AdminClient.class);
        doThrow(new RuntimeException("close error")).when(admin).close();
        SimpleHealthCheck c = checkWithMockAdmin(admin);
        c.destroy();
        verify(admin).close();
    }
}
