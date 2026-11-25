package com.osatum.wbn.application.shared.list;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageRequest {
    private int first;
    private int size;
    private int page;

}
