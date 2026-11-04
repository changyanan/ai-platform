package com.aiplatform.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 通用分页参数，最大页大小 100，防止一次查询拖垮演示环境。
 */
@Data
public class PageQuery {

    @Min(value = 1, message = "页码不能小于 1")
    private long page = 1;

    @Min(value = 1, message = "每页数量不能小于 1")
    @Max(value = 100, message = "每页数量不能超过 100")
    private long size = 10;

    public long currentPage() {
        return Math.max(page, 1);
    }

    public long pageSize() {
        return Math.min(Math.max(size, 1), 100);
    }

    public long offset() {
        return (currentPage() - 1) * pageSize();
    }
}
