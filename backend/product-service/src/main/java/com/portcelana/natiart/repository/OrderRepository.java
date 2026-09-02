package com.portcelana.natiart.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.CustomerOrder;

@Repository
public interface OrderRepository extends JpaRepository<CustomerOrder, String> {}
