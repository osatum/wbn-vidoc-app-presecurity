package com.osatum.wbn.application.report.survey.assembler;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class SurveyReportRowSupport {

    private static final String EMPTY_VALUE = "-";

    private SurveyReportRowSupport() {
    }

    static <T> void addIfHasSectionData(List<T> rows, T row) {
        if (row == null) {
            return;
        }
        fillEmptyStringFields(row);
        if (hasSectionData(row)) {
            rows.add(row);
        }
    }

    static <T> List<T> safeList(Collection<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    static String valueOrEmpty(String value) {
        return value == null || value.isBlank() ? getEmpty() : value;
    }

    static String valueOrNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    static String getEmpty() {
        return EMPTY_VALUE;
    }

    private static boolean hasSectionData(Object row) {
        try {
            for (Field field : row.getClass().getDeclaredFields()) {
                if (field.isSynthetic()
                        || Modifier.isStatic(field.getModifiers())
                        || "journalId".equals(field.getName())) {
                    continue;
                }
                field.setAccessible(true);
                if (hasValue(field.get(row))) {
                    return true;
                }
            }
            return false;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot inspect survey report row", exception);
        }
    }

    private static void fillEmptyStringFields(Object row) {
        try {
            for (Field field : row.getClass().getDeclaredFields()) {
                if (field.isSynthetic()
                        || Modifier.isStatic(field.getModifiers())
                        || !String.class.equals(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(row);
                if (value == null || value.toString().isBlank()) {
                    field.set(row, getEmpty());
                }
            }
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot fill empty survey report row fields", exception);
        }
    }

    private static boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof CharSequence text) {
            return !text.toString().isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        return true;
    }
}
