package com.osatum.wbn.application.journal.view;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageView<T> {
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private List<T> content;
    private int number;
    private int numberOfElements;
    private boolean empty;

}