package com.osatum.wbn.application.shared.list;

import java.util.List;

public interface SearchQuery {
    public List<Long> getSearchIds();

    public void setSearchIds(List<Long> searchIds);
}
