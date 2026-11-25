package com.osatum.wbn.application.shared.list;

public record SortOrderDef(
        String field,
        Direction direction
) {
    public enum Direction {
        ASC, DESC
    }
}