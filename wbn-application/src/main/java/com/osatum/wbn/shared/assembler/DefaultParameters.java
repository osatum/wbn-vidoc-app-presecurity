package com.osatum.wbn.shared.assembler;

public class DefaultParameters<E, D> implements Parameters<E, D> {

    public DefaultParameters(E entity, D dto) {
        this.entity = entity;
        this.dto = dto;
    }

    private final E entity;
    private final D dto;

    @Override
    public E getEntity() {
        return entity;
    }

    @Override
    public D getDto() {
        return dto;
    }
}
