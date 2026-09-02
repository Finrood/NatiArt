package com.portcelana.natiart.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.CartItem;
import com.portcelana.natiart.model.Product;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, String> {
    List<CartItem> findCartItemsByUsername(String username);

    Optional<CartItem> findCartItemByUsernameAndProduct(String username, Product product);
}
