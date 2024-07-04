package com.saas.directory.repository;


import com.saas.directory.model.Token;
import com.saas.directory.model.TokenType;
import com.saas.directory.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, String> {
    Optional<Token> findByToken(String token);

    Optional<Token> findByTokenAndTokenType(String token, TokenType tokenType);

    void deleteAllByUser(User user);

    void deleteByToken(String token);
}
