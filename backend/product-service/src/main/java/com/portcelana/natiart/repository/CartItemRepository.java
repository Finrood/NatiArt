package com.portcelana.natiart.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.CartItem;
import com.portcelana.natiart.model.Product;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, String> {
    List<CartItem> findCartItemsByUsername(String username);

    Optional<CartItem> findCartItemByUsernameAndProduct(String username, Product product);

    /**
     * Atomically increments the line quantity without a read-modify-write round
     * trip, so concurrent adds for the same user and product cannot lose
     * increments. Returns the number of rows affected (0 when no line exists).
     */
    @Modifying
    @Query(
            "UPDATE CartItem c SET c.quantity = c.quantity + 1 WHERE c.username = :username AND c.product.id = :productId")
    int incrementQuantity(@Param("username") String username, @Param("productId") String productId);

    /**
     * Atomically decrements the line quantity, but only while more than one unit
     * remains. Returns the number of rows affected (0 when no line exists or the
     * last unit remains — the caller then deletes the line).
     */
    @Modifying
    @Query(
            "UPDATE CartItem c SET c.quantity = c.quantity - 1 WHERE c.username = :username AND c.product.id = :productId AND c.quantity > 1")
    int decrementQuantityIfGreaterThanOne(@Param("username") String username, @Param("productId") String productId);

    long deleteByUsernameAndProduct(String username, Product product);
}
