package com.portcelana.natiart.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<CustomerOrder, String> {
    @Query(
            "SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END FROM CustomerOrder customerOrder JOIN customerOrder.items item WHERE item.product = :product")
    boolean existsByProduct(@Param("product") Product product);

    @Modifying
    @Query("UPDATE CustomerOrder o SET o.status = :status WHERE o.id = :id")
    int updateStatusById(@Param("id") String id, @Param("status") OrderStatus status);
}
