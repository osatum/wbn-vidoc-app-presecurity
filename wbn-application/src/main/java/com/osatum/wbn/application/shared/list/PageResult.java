package com.osatum.wbn.application.shared.list;

import java.util.List;

public record PageResult<T>(
        List<T> data,
        long totalElements,
        int totalPages,
        int size
) {
}
