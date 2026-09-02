package com.saas.directory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.saas.directory.model.Profile;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, String> {}
