package io.kivio.domain.identity.service;

import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.dto.response.UserResponse;
import io.kivio.domain.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * ユーザー情報のアプリケーションサービスを表現します。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * 指定 ID のユーザープロフィールを取得します。
     *
     * @throws io.kivio.common.exception.ResourceNotFoundException ユーザーが存在しない場合
     */
    @Transactional(readOnly = true)
    public UserResponse getById(UUID userId) {
        User user = userRepository.findByIdOrThrow(userId);
        return UserResponse.from(user);
    }
}
