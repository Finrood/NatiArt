package com.saas.directory.repository;

import com.saas.directory.model.ExternalUser;
import com.saas.directory.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExternalUserRepository extends JpaRepository<ExternalUser, String> {
    Optional<ExternalUser> findByUser(User user);
}
