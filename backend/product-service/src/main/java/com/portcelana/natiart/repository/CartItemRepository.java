package com.portcelana.natiart.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.CartItem;
import com.portcelana.natiart.model.Product;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, String> {
    boolean existsByProduct(Product product);

    /**
     * Loads a user's cart lines with the associations the listing DTO touches
     * (`product` with its `images`, plus `personalization`) in a single query.
     * Without the fetch joins every line re-fetches its product and collection
     * (N+1 inside one `@Transactional` reader, `open-in-view=false`).
     */
    @Query(
            "SELECT DISTINCT c FROM CartItem c LEFT JOIN FETCH c.product p LEFT JOIN FETCH p.images LEFT JOIN FETCH c.personalization WHERE c.username = :username")
    List<CartItem> findCartItemsByUsername(@Param("username") String username);

    /** Loads a cart line and its DTO associations in one query. */
    @Query(
            "SELECT DISTINCT c FROM CartItem c LEFT JOIN FETCH c.product p LEFT JOIN FETCH p.images LEFT JOIN FETCH p.category LEFT JOIN FETCH p.packaging LEFT JOIN FETCH c.personalization WHERE c.username = :username AND p.id = :productId")
    Optional<CartItem> findCartItemByUsernameAndProductWithDetails(
            @Param("username") String username, @Param("productId") String productId);

    Optional<CartItem> findCartItemByUsernameAndProduct(String username, Product product);

    /** Locks the line before the last-unit delete decision. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CartItem c WHERE c.username = :username AND c.product.id = :productId")
    Optional<CartItem> findCartItemByUsernameAndProductForUpdate(
            @Param("username") String username, @Param("productId") String productId);

    /**
     * Empties a user's cart in one statement. The rows are never read on this
     * path, so loading them first only to delete them one by one is pure
     * overhead on the checkout flow.
     */
    void deleteByUsername(String username);

    /**
     * Removes one cart line by user and product. Declared void on purpose:
     * Spring Data runs a void derived delete as load-then-remove, so the
     * {@link CartItem#getPersonalization()} cascade (ALL, orphanRemoval) fires
     * and the line's Personalization row goes with it. The {@code long}-return
     * form of the same query fails result unwrapping with a ClassCastException
     * at runtime (pinned by CartItemCascadeSemanticsTest).
     */
    void deleteByUsernameAndProduct(String username, Product product);
}
