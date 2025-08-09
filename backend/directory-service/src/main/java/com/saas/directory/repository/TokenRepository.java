package com.saas.directory.repository;


import com.saas.directory.model.Token;
import com.saas.directory.model.TokenType;
import com.saas.directory.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, String> {
    Optional<Token> findByJti(String jti);

    Optional<Token> findByJtiAndTokenType(String jti, TokenType tokenType);

    void deleteAllByUser(User user);

    void deleteByJti(String jti);
}
