package com.osatum.wbn.shared.assembler;
/**
 * Dwukierunkowy assembler (Entity ↔ DTO).
 */
public interface BiAssembler<E, F, P extends Parameters<E, F>> extends AssemblerDto<E, F, P> {
    /**
     * Konwertuje DTO na encję.
     */
    public E toEntity(P paramater);
}
