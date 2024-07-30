package com.portcelana.natiart.repository;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Order;
import com.portcelana.natiart.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String>  {
}
