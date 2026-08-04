package io.kivio.domain.identity.repository;

import io.kivio.common.exception.ResourceNotFoundException;
import io.kivio.domain.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * ユーザーリポジトリを表現します。
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByEmail(String email);

    default User findByIdOrThrow(UUID id) {
        return findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
