package com.portcelana.natiart.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/** Shared collection envelope; page is zero based and total counts the filtered result. */
public record PagedResponseDto<T>(List<T> items, long total, int page, int size, boolean hasNext) {
    /** Creates metadata and items from the same database page. */
    public static <T> PagedResponseDto<T> from(Page<T> page) {
        return new PagedResponseDto<>(
                page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize(), page.hasNext());
    }
}
