package io.kivio.domain.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * トークンハッシュ計算ユーティリティを表現します。
 *
 * <p>DB にはトークンの SHA-256 ハッシュ値のみ保存し、平文は保持しません。
 */
final class TokenHashUtils {

    private TokenHashUtils() {}

    /**
     * 文字列を SHA-256 でハッシュ化し、16進数文字列で返します。
     */
    static String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
