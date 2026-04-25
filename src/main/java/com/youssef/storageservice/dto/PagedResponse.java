package com.youssef.storageservice.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic paginated response wrapper for list endpoints.
 *
 * Usage:
 *   Page<FileInfoResponse> page = fileService.listFiles(user, pageable);
 *   return ResponseEntity.ok(PagedResponse.of(page));
 *
 * JSON shape:
 * {
 *   "content": [...],
 *   "page": 0,
 *   "size": 20,
 *   "totalElements": 5,
 *   "totalPages": 1,
 *   "last": true
 * }
 */
@Getter
@Builder
public class PagedResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;
    private boolean first;

    /**
     * Factory method — builds a PagedResponse from a Spring Data Page object.
     */
    public static <T> PagedResponse<T> of(Page<T> springPage) {
        return PagedResponse.<T>builder()
                .content(springPage.getContent())
                .page(springPage.getNumber())
                .size(springPage.getSize())
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .last(springPage.isLast())
                .first(springPage.isFirst())
                .build();
    }
}
