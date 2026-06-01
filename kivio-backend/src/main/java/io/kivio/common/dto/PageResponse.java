package io.kivio.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
        /** コンテンツ一覧 */
        List<T> content,
        /** ページ番号 */
        int page,
        /** 1ページ当たりの件数 */
        int size,
        /** 総件数 */
        long totalElements,
        /** 総ページ数 */
        int totalPages,
        /** 先頭ページフラグ */
        boolean first,
        /** 末尾ページフラグ */
        boolean last
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
