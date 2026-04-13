package com.youthfit.user.infrastructure.persistence;

import com.youthfit.user.domain.model.AuthProvider;
import com.youthfit.user.domain.model.User;
import com.youthfit.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email);
    }

    @Override
    public Optional<User> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId) {
        return jpaRepository.findByAuthProviderAndProviderId(authProvider, providerId);
    }

    @Override
    public User save(User user) {
        return jpaRepository.save(user);
    }
}
