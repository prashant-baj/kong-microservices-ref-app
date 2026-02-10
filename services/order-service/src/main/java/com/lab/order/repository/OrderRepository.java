package com.lab.order.repository;

import com.lab.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

     @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.lineItems")
    List<Order> findAllWithLineItems();

}
