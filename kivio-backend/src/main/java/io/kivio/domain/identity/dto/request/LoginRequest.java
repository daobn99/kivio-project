package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

/**
 * メールアドレス・パスワードによるログインリクエストを表現します。
 */
@Getter
@Builder
public class LoginRequest {

    /** メールアドレス */
    @NotBlank(message = "メールアドレスは必須です")
    @Email(message = "メールアドレスの形式が正しくありません")
    @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
    private String email;

    /** パスワード */
    @NotBlank(message = "パスワードは必須です")
    private String password;
}
