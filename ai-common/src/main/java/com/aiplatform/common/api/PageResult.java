package com.aiplatform.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 统一分页结构。模块内将 MyBatis-Plus 的 IPage 转换为该结构，避免 API 层泄漏 ORM 对象。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResult<T>(
        List<T> records,
        long total,
        long page,
        long size,
        long pages
) {

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        long safeSize = Math.max(size, 1);
        long pages = total == 0 ? 0 : (total + safeSize - 1) / safeSize;
        return new PageResult<>(records, total, page, size, pages);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(List.of(), 0, page, size, 0);
    }
}
