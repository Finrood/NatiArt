package com.saas.directory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.saas.directory.model.ExternalUser;
import com.saas.directory.model.User;

@Repository
public interface ExternalUserRepository extends JpaRepository<ExternalUser, String> {
    Optional<ExternalUser> findByUser(User user);
}
