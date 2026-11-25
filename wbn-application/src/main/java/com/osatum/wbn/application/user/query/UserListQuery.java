package com.osatum.wbn.application.user.query;

import com.osatum.wbn.application.shared.list.PageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserListQuery  extends PageRequest {

    public enum SortBy {
        SORT_BY_EMAIL_ASC,
        SORT_BY_EMAIL_DESC,
        SORT_BY_NAME_ASC,
        SORT_BY_NAME_DESC
    }

    private String email;
    private String name;
    private Long institutionId;
    private SortBy sortBy = SortBy.SORT_BY_NAME_ASC;
}
