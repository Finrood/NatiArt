package com.saas.directory.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.saas.directory.model.Token;
import com.saas.directory.model.TokenType;
import com.saas.directory.model.User;

@Repository
public interface TokenRepository extends JpaRepository<Token, String> {
    Optional<Token> findByJti(String jti);

    Optional<Token> findByJtiAndTokenType(String jti, TokenType tokenType);

    void deleteAllByUser(User user);

    void deleteByJti(String jti);

    @Modifying
    @Query("DELETE FROM Token t WHERE t.expiry < :now")
    int deleteAllExpiredBefore(@Param("now") Instant now);
}
