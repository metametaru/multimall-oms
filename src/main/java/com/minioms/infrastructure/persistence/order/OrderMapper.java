package com.minioms.infrastructure.persistence.order;

import com.minioms.domain.order.MallOrderKey;
import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderItem;

import java.util.List;

/** ドメインモデルとJPAエンティティの相互変換 */
final class OrderMapper {

    private OrderMapper() {
    }

    static OrderJpaEntity toEntity(Order order) {
        List<OrderItemJpaEntity> items = order.items().stream()
                .map(item -> new OrderItemJpaEntity(
                        item.id(), item.productCode(), item.productName(), item.unitPrice(), item.quantity()))
                .toList();

        return new OrderJpaEntity(
                order.id(),
                order.mallOrderKey().mallId(),
                order.mallOrderKey().mallOrderNumber(),
                order.status(),
                order.customerName(),
                order.totalAmount(),
                order.orderedAt(),
                items,
                order.id() == null ? null : order.version());
    }

    static Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(item -> new OrderItem(
                        item.getId(), item.getProductCode(), item.getProductName(),
                        item.getUnitPrice(), item.getQuantity()))
                .toList();

        return new Order(
                entity.getId(),
                new MallOrderKey(entity.getMallId(), entity.getMallOrderNumber()),
                entity.getStatus(),
                entity.getCustomerName(),
                entity.getTotalAmount(),
                entity.getOrderedAt(),
                items,
                entity.getVersion());
    }
}
