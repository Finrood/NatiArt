package com.portcelana.natiart.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Product;

@Repository
public interface OrderRepository extends JpaRepository<CustomerOrder, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CustomerOrder o WHERE o.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") String id);

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<CustomerOrder> findByOwnerExternalIdAndIdempotencyKey(String ownerExternalId, String idempotencyKey);

    @Query(
            "SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END FROM CustomerOrder customerOrder JOIN customerOrder.items item WHERE item.product = :product")
    boolean existsByProduct(@Param("product") Product product);
}
