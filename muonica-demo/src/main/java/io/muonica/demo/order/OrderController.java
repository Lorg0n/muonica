package io.muonica.demo.order;

import io.muonica.core.annotation.api.MuonicaGroup;
import io.muonica.core.annotation.api.MuonicaOperation;
import io.muonica.core.annotation.api.MuonicaResponse;
import io.muonica.core.annotation.documentation.MuonicaDocumentation;
import io.muonica.core.annotation.security.MuonicaSecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@MuonicaGroup(name = "Orders", description = "Inspect, create and manage demo orders.")
@MuonicaDocumentation(file = "classpath:/muonica/orders/index.md")
class OrderController {
    @GetMapping
    @MuonicaOperation(summary = "List orders", description = "Returns a filterable page of orders for the current account.")
    @MuonicaDocumentation(file = "classpath:/muonica/orders/list-orders.md")
    @MuonicaSecurityRequirement("bearerAuth")
    List<OrderResponse> listOrders(
            @RequestParam(name = "status", required = false) OrderStatus status,
            @RequestParam(name = "customerId", required = false) UUID customerId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "pageSize", required = false) Integer pageSize,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId) {
        return List.of(sampleOrder());
    }

    @GetMapping("/{id}")
    @MuonicaOperation(summary = "Get an order", description = "Returns the complete order, including line items and payment state.")
    @MuonicaDocumentation(file = "classpath:/muonica/orders/get-order.md")
    @MuonicaResponse(status = 404, description = "Order was not found", body = OrderErrorResponse.class)
    @MuonicaSecurityRequirement("bearerAuth")
    OrderResponse getOrder(@PathVariable UUID id) {
        return sampleOrder();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @MuonicaOperation(summary = "Create an order", description = "Creates an order after validating every line item and payment method.")
    @MuonicaDocumentation(file = "classpath:/muonica/orders/create-order.md")
    @MuonicaResponse(status = 409, description = "The order could not be created because inventory changed", body = OrderErrorResponse.class)
    @MuonicaSecurityRequirement("bearerAuth")
    OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return sampleOrder();
    }

    @PatchMapping("/{id}/status")
    @MuonicaOperation(summary = "Change order status", description = "Moves an order through the fulfilment lifecycle.")
    @MuonicaDocumentation(file = "classpath:/muonica/orders/update-status.md")
    @MuonicaResponse(status = 409, description = "The requested status transition is not allowed", body = OrderErrorResponse.class)
    @MuonicaSecurityRequirement("bearerAuth")
    OrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return sampleOrder();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @MuonicaOperation(summary = "Cancel an order", description = "Cancels an order that has not entered fulfilment.")
    @MuonicaDocumentation(file = "classpath:/muonica/orders/cancel-order.md")
    @MuonicaResponse(status = 404, description = "Order was not found", body = OrderErrorResponse.class)
    @MuonicaResponse(status = 409, description = "The order is already being fulfilled", body = OrderErrorResponse.class)
    @MuonicaSecurityRequirement("bearerAuth")
    void cancelOrder(@PathVariable UUID id) { }

    private OrderResponse sampleOrder() {
        return new OrderResponse(UUID.fromString("6d8f2c22-7d5d-4f3a-8b3b-08cf8e4a6c11"),
                UUID.fromString("0f4b9d4a-3cf5-4ea2-9dc1-4f6c7e0c4a21"), OrderStatus.PAID,
                new BigDecimal("129.00"), List.of(new OrderLine("muonica-notebook", 2, new BigDecimal("64.50"))),
                Instant.parse("2026-01-15T09:00:00Z"));
    }

    record OrderResponse(UUID id, UUID customerId, OrderStatus status, BigDecimal total,
            List<OrderLine> lines, Instant createdAt) { }

    record CreateOrderRequest(@NotNull UUID customerId, @NotEmpty List<@Valid OrderLine> lines,
            @NotNull PaymentMethod paymentMethod) { }

    record UpdateOrderStatusRequest(@NotNull OrderStatus status) { }

    record OrderLine(@NotBlank String sku, @Min(1) int quantity, @NotNull BigDecimal unitPrice) { }

    record OrderErrorResponse(String code, String message, String requestId) { }

    enum OrderStatus { PENDING, PAID, FULFILLING, SHIPPED, CANCELLED }

    enum PaymentMethod { CARD, BANK_TRANSFER, INVOICE }
}
