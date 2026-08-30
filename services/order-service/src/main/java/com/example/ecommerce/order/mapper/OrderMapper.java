package com.example.ecommerce.order.mapper;

import com.example.ecommerce.order.domain.CustomerOrder;
import com.example.ecommerce.order.domain.OrderItem;
import com.example.ecommerce.order.dto.OrderItemResponse;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.order.dto.OrderSummaryResponse;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse toResponse(CustomerOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getFailureReason(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getItems().stream().map(this::toItemResponse).toList(),
                order.getVersion(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public OrderSummaryResponse toSummary(CustomerOrder order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getStatus(),
                order.getFailureReason(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getVersion(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getLineNumber(),
                item.getProductId(),
                item.getProductSku(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal()
        );
    }
}
