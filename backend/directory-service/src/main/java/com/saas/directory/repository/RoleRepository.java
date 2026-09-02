package com.saas.directory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;

@Repository
public interface RoleRepository extends JpaRepository<Role, String> {
    Optional<Role> findRoleByLabel(RoleName roleName);
}
