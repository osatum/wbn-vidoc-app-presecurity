package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.shared.list.PageRequest;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.application.shared.list.SearchQuery;
import com.osatum.wbn.application.shared.list.SortOrderDef;
import com.osatum.wbn.domain.model.BaseEntity;
import com.osatum.wbn.domain.model.GeneratedIdEntity;
import com.osatum.wbn.infrastructure.persistence.specifications.EntitySpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractQueryHandler<E extends GeneratedIdEntity, D, F extends PageRequest> {
    protected abstract EntitySpecifications<E, F> getSpecifications();
    protected abstract JpaSpecificationExecutor<E> getRepository();
    protected abstract D toDto(E entity);

    protected Optional<List<Long>> searchIds(F filterForm) {
        return Optional.empty();
    }

    @Transactional
    public PageResult<D> execute(F filterForm) {


        if (filterForm instanceof SearchQuery filter) {
            Optional<List<Long>> idsOptional = searchIds(filterForm);

            if (idsOptional.isPresent()) {
                List<Long> ids = idsOptional.get();

                if (ids.isEmpty()) {
                    return new PageResult<>(List.of(), 0, 0, filterForm.getSize());
                }

                int fromIndex = filterForm.getPage() * filterForm.getSize();

                if (fromIndex >= ids.size()) {
                    return new PageResult<>(List.of(), ids.size(), totalPages(ids.size(), filterForm.getSize()), filterForm.getSize());
                }

                int toIndex = Math.min(fromIndex + filterForm.getSize(), ids.size());
                List<Long> pageIds = new ArrayList<>(ids.subList(fromIndex, toIndex));

                filter.setSearchIds(pageIds);

                List<E> entities = getRepository().findAll(
                        getSpecifications().byFilter(filterForm)
                );

                Map<Long, E> byId = entities.stream()
                        .collect(Collectors.toMap(E::getId, Function.identity()));

                List<D> page = pageIds.stream()
                        .map(byId::get)
                        .filter(Objects::nonNull)
                        .map(this::toDto)
                        .toList();

                enrichEntity(page);

                return new PageResult<>(page, ids.size(), totalPages(ids.size(), filterForm.getSize()), filterForm.getSize());
            }

            filter.setSearchIds(null);
        }
        Sort sort = toSpringSort(filterForm);
        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(filterForm.getPage(), filterForm.getSize(), sort);

        Page<E> page = getRepository().findAll(
                getSpecifications().byFilter(filterForm),
                pageRequest
        );

        List<D> list = page.getContent().stream()
                .map(this::toDto)
                .toList();

        enrichEntity(list);

        return new PageResult<>(list, page.getTotalElements(), page.getTotalPages(), page.getSize());
    }

    protected void enrichEntity(List<D> page) {
    }

    protected Sort toSpringSort(F filterForm) {

        List<SortOrderDef> sorts = getSpecifications().sortByFilter(filterForm);

        if (!sorts.isEmpty()) {
            return Sort.by(
                    sorts.stream()
                            .map(this::toOrder)
                            .toList()
            );
        }

        return Sort.unsorted();
    }

    private Sort.Order toOrder(SortOrderDef sort) {
        return sort.direction() == SortOrderDef.Direction.DESC
                ? Sort.Order.desc(sort.field())
                : Sort.Order.asc(sort.field());
    }

    private int totalPages(int totalElements, int size) {
        if (totalElements <= 0 || size <= 0) {
            return 0;
        }

        return (int) Math.ceil((double) totalElements / size);
    }


}
