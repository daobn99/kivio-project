package io.kivio.domain.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 認証コード（OTP）検証リクエスト（登録ステップ2）を表現します。
 */
public record VerifyOtpRequest(
        /** メールアドレス */
        @NotBlank(message = "メールアドレスは必須です")
        @Email(message = "メールアドレスの形式が正しくありません")
        @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
        String email,

        /** 認証コード（6 桁の数値） */
        @NotBlank(message = "認証コードは必須です")
        @Pattern(regexp = "\\d{6}", message = "認証コードは6桁の数字で入力してください")
        String otp
) {}
