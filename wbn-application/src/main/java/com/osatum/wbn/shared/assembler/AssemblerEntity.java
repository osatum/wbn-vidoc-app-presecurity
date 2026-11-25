package com.osatum.wbn.shared.assembler;

public interface AssemblerEntity<E, D, P extends Parameters<E, D>>  {
    public E toEntity(P paramater);
}
