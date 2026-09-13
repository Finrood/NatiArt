package com.portcelana.natiart.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import jakarta.persistence.EntityManager;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.CustomerOrderItem;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.OrderStatus;

@DataJpaTest(properties = "spring.sql.init.mode=never")
class OrderRepositoryFetchTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void idempotencyReplayInitializesItemsForDetachedDtoMapping() {
        final Category category = categoryRepository.save(new Category("plates"));
        final Product product = productRepository.save(
                new Product("Handmade plate", new BigDecimal("25.00")).setCategory(category));
        final CustomerOrder order = new CustomerOrder()
                .setFirstname("Jane")
                .setLastname("Customer")
                .setEmail("jane@example.com")
                .setOrderDate(java.time.Instant.now())
                .setDeliveryAmount(BigDecimal.ZERO)
                .setTotalAmount(new BigDecimal("25.00"))
                .setStatus(OrderStatus.PENDING)
                .setOwnerExternalId("cus_jane")
                .setIdempotencyKey("checkout-1")
                .setRequestFingerprint("fingerprint");
        order.getItems().add(new CustomerOrderItem()
                .setCustomerOrder(order)
                .setProduct(product)
                .setQuantity(1)
                .setPrice(new BigDecimal("25.00")));
        orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        final CustomerOrder replay = orderRepository
                .findByOwnerExternalIdAndIdempotencyKey("cus_jane", "checkout-1")
                .orElseThrow();

        assertTrue(Hibernate.isInitialized(replay.getItems()));
        assertEquals(1, OrderDto.from(replay).getItems().size());
    }
}
