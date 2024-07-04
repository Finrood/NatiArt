package com.saas.directory.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User {
	private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

	@Id
	private String id;

	@Version
	private long version;

	@Column(nullable = false, unique = true)
	private String username;

	@Column(nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private boolean emailConfirmed;

	@OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
	private Profile profile;

	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
	private final Set<Token> tokens = new HashSet<>();

	@ManyToOne(optional = false, fetch = FetchType.EAGER)
	@JoinColumn(name = "role_id", referencedColumnName = "id", nullable = false)
	private Role role;

	@CreatedDate
	@Column(updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	private Instant updatedAt;

	@Column(nullable = false)
	private boolean active;

	private Instant deactivatedAt;

	protected User() {
		// FOR JPA
	}

	public User(String username, String password) {
		this.id = UUID.randomUUID().toString();
		this.username = username.toLowerCase();
		this.passwordHash = encoder.encode(password);
		this.emailConfirmed = false;
		this.active = true;
	}


	public String getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public User setUsername(String username) {
		this.username = username;
		return this;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public User setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
		return this;
	}

	public boolean isEmailConfirmed() {
		return emailConfirmed;
	}

	public User setEmailConfirmed(boolean emailConfirmed) {
		this.emailConfirmed = emailConfirmed;
		return this;
	}

	public Profile getProfile() {
		return profile;
	}

	public User setProfile(Profile profile) {
		this.profile = profile;
		return this;
	}

	public Role getRole() {
		return role;
	}

	public User setRole(Role role) {
		this.role = role;
		return this;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public User setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
		return this;
	}

	public boolean isActive() {
		return active;
	}

	public User setActive(boolean active) {
		this.active = active;
		return this;
	}

	public Instant getDeactivatedAt() {
		return deactivatedAt;
	}

	public User setDeactivatedAt(Instant deactivatedAt) {
		this.deactivatedAt = deactivatedAt;
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		User user = (User) o;
		return Objects.equals(id, user.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
