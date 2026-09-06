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
    /**
     * Loads a user's cart lines with the associations the listing DTO touches
     * (`product` with its `images`, plus `personalization`) in a single query.
     * Without the fetch joins every line re-fetches its product and collection
     * (N+1 inside one `@Transactional` reader, `open-in-view=false`).
     */
    @Query(
            "SELECT DISTINCT c FROM CartItem c LEFT JOIN FETCH c.product p LEFT JOIN FETCH p.images LEFT JOIN FETCH c.personalization WHERE c.username = :username")
    List<CartItem> findCartItemsByUsername(@Param("username") String username);

    Optional<CartItem> findCartItemByUsernameAndProduct(String username, Product product);

    /**
     * Atomically increments the line quantity without a read-modify-write round
     * trip, so concurrent adds for the same user and product cannot lose
     * increments — but only while the line stays below the caller's cap, so a
     * tight add-loop cannot grow one row without limit. Returns the number of
     * rows affected (0 when no line exists or the line already reached the cap;
     * the caller distinguishes the two with a follow-up lookup).
     */
    @Modifying
    @Query(
            "UPDATE CartItem c SET c.quantity = c.quantity + 1 WHERE c.username = :username AND c.product.id = :productId AND c.quantity < :cap")
    int incrementQuantityIfBelowCap(
            @Param("username") String username, @Param("productId") String productId, @Param("cap") int cap);

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
