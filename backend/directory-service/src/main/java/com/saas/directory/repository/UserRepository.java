package com.saas.directory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.saas.directory.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findUserByUsernameIgnoreCase(String username);

    boolean existsUserByUsernameIgnoreCase(String username);
}
