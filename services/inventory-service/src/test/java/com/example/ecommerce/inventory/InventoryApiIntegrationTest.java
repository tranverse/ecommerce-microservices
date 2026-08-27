package com.example.ecommerce.inventory;

import com.example.ecommerce.inventory.domain.ReservationStatus;
import com.example.ecommerce.inventory.dto.CreateReservationRequest;
import com.example.ecommerce.inventory.dto.InventoryResponse;
import com.example.ecommerce.inventory.dto.ReservationLineRequest;
import com.example.ecommerce.inventory.dto.ReservationResponse;
import com.example.ecommerce.inventory.dto.SetStockRequest;
import com.example.ecommerce.inventory.exception.InsufficientInventoryException;
import com.example.ecommerce.inventory.repository.InventoryItemRepository;
import com.example.ecommerce.inventory.repository.InventoryReservationRepository;
import com.example.ecommerce.inventory.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class InventoryApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    private ExecutorService executor;

    @BeforeEach
    void cleanDatabase() {
        reservationRepository.deleteAll();
        inventoryItemRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopExecutor() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void managesTheFullReservationLifecycleOverHttp() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-ID", "inventory-integration-flow");

        ResponseEntity<InventoryResponse> stockResponse = restTemplate.exchange(
                "/api/v1/inventory/items/{productId}", HttpMethod.PUT,
                new HttpEntity<>(new SetStockRequest(5), headers), InventoryResponse.class, productId);
        assertThat(stockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        CreateReservationRequest reservationRequest = new CreateReservationRequest(
                orderId, List.of(new ReservationLineRequest(productId, 2)));
        ResponseEntity<ReservationResponse> reserveResponse = restTemplate.exchange(
                "/api/v1/inventory/reservations", HttpMethod.POST,
                new HttpEntity<>(reservationRequest, headers), ReservationResponse.class);
        assertThat(reserveResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reserveResponse.getHeaders().getFirst("X-Correlation-ID"))
                .isEqualTo("inventory-integration-flow");
        assertThat(reserveResponse.getBody()).isNotNull();
        assertThat(reserveResponse.getBody().status()).isEqualTo(ReservationStatus.RESERVED);

        ResponseEntity<ReservationResponse> retryResponse = restTemplate.exchange(
                "/api/v1/inventory/reservations", HttpMethod.POST,
                new HttpEntity<>(reservationRequest, headers), ReservationResponse.class);
        assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retryResponse.getBody()).extracting(ReservationResponse::id)
                .isEqualTo(reserveResponse.getBody().id());

        ResponseEntity<ReservationResponse> confirmResponse = restTemplate.postForEntity(
                "/api/v1/inventory/reservations/{orderId}/confirm", null,
                ReservationResponse.class, orderId);
        assertThat(confirmResponse.getBody()).isNotNull();
        assertThat(confirmResponse.getBody().status()).isEqualTo(ReservationStatus.CONFIRMED);

        ResponseEntity<InventoryResponse> finalStock = restTemplate.getForEntity(
                "/api/v1/inventory/items/{productId}", InventoryResponse.class, productId);
        assertThat(finalStock.getBody()).isNotNull();
        assertThat(finalStock.getBody().totalQuantity()).isEqualTo(3);
        assertThat(finalStock.getBody().reservedQuantity()).isZero();
        assertThat(finalStock.getBody().availableQuantity()).isEqualTo(3);
    }

    @Test
    void concurrentReservationsCannotOversellOneAvailableUnit() throws Exception {
        UUID productId = UUID.randomUUID();
        inventoryService.setStock(productId, 1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> first = submitReservation(productId, ready, start);
        Future<Boolean> second = submitReservation(productId, ready, start);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        int successes = successfulReservations(first, second);

        assertThat(successes).isEqualTo(1);
        InventoryResponse stock = inventoryService.getStock(productId);
        assertThat(stock.reservedQuantity()).isEqualTo(1);
        assertThat(stock.availableQuantity()).isZero();
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    private Future<Boolean> submitReservation(
            UUID productId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return executor.submit(() -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent test did not start in time");
            }
            inventoryService.reserve(new CreateReservationRequest(
                    UUID.randomUUID(), List.of(new ReservationLineRequest(productId, 1))));
            return true;
        });
    }

    @SafeVarargs
    private int successfulReservations(Future<Boolean>... futures) throws Exception {
        int successes = 0;
        for (Future<Boolean> future : futures) {
            try {
                if (future.get(10, TimeUnit.SECONDS)) {
                    successes++;
                }
            } catch (ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(InsufficientInventoryException.class);
            }
        }
        return successes;
    }
}
