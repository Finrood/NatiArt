package com.saas.directory.repository;

import com.saas.directory.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
	Optional<User> findUserByUsernameIgnoreCase(String username);

	boolean existsUserByUsernameIgnoreCase(String username);
}
