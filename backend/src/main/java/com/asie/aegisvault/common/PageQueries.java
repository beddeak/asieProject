package com.asie.aegisvault.common;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.function.Function;

/** JPA의 int 오프셋 범위를 지키고, 삭제·검색 이후 사라진 페이지를 마지막 페이지로 보정합니다. */
public final class PageQueries {
    private PageQueries() { }

    public static <T> Page<T> fetch(int page, int size, Sort sort, Function<Pageable, Page<T>> query) {
        if (size < 1) {
            throw new IllegalArgumentException("페이지 크기는 1 이상이어야 합니다.");
        }
        int maxPage = Integer.MAX_VALUE / size;
        PageRequest request = PageRequest.of(Math.min(Math.max(page, 0), maxPage), size, sort);
        Page<T> result = query.apply(request);
        if (result.getTotalElements() == 0) {
            return Page.empty(request.withPage(0));
        }
        if (result.getNumber() >= result.getTotalPages()) {
            result = query.apply(request.withPage(Math.min(result.getTotalPages() - 1, maxPage)));
        }
        return result.getTotalElements() == 0 ? Page.empty(request.withPage(0)) : result;
    }
}
