package com.osatum.wbn.infrastructure.persistence.specifications;

import com.osatum.wbn.application.shared.list.SortOrderDef;
import com.osatum.wbn.application.user.query.UserListQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserSpecificationsTest {
    private final UserSpecifications specifications = new UserSpecifications();

    @Test
    void returnsNameAscendingSortByDefault() {
        UserListQuery query = new UserListQuery();

        assertThat(specifications.sortByFilter(query)).containsExactly(
                new SortOrderDef("lastName", SortOrderDef.Direction.ASC),
                new SortOrderDef("firstName", SortOrderDef.Direction.ASC),
                new SortOrderDef("id", SortOrderDef.Direction.ASC)
        );
    }

    @Test
    void returnsNameDescendingSort() {
        UserListQuery query = new UserListQuery();
        query.setSortBy(UserListQuery.SortBy.SORT_BY_NAME_DESC);

        assertThat(specifications.sortByFilter(query)).containsExactly(
                new SortOrderDef("lastName", SortOrderDef.Direction.DESC),
                new SortOrderDef("firstName", SortOrderDef.Direction.DESC),
                new SortOrderDef("id", SortOrderDef.Direction.ASC)
        );
    }

    @Test
    void returnsEmailAscendingSort() {
        UserListQuery query = new UserListQuery();
        query.setSortBy(UserListQuery.SortBy.SORT_BY_EMAIL_ASC);

        assertThat(specifications.sortByFilter(query)).containsExactly(
                new SortOrderDef("email", SortOrderDef.Direction.ASC),
                new SortOrderDef("id", SortOrderDef.Direction.ASC)
        );
    }

    @Test
    void returnsEmailDescendingSort() {
        UserListQuery query = new UserListQuery();
        query.setSortBy(UserListQuery.SortBy.SORT_BY_EMAIL_DESC);

        assertThat(specifications.sortByFilter(query)).containsExactly(
                new SortOrderDef("email", SortOrderDef.Direction.DESC),
                new SortOrderDef("id", SortOrderDef.Direction.ASC)
        );
    }
}
