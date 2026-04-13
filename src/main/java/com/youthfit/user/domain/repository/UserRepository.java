package com.youthfit.user.domain.repository;

import com.youthfit.user.domain.model.AuthProvider;
import com.youthfit.user.domain.model.User;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);

    User save(User user);
}
