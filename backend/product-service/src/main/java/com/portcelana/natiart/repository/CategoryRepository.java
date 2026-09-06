package com.portcelana.natiart.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {
    Optional<Category> findCategoryByLabel(String label);

    @Modifying
    @Query("UPDATE Category c SET c.active = CASE WHEN c.active = true THEN false ELSE true END WHERE c.id = :id")
    int toggleActiveById(@Param("id") String id);
}
