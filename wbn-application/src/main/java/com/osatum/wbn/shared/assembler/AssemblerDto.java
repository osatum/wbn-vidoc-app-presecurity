package com.osatum.wbn.shared.assembler;

/**
 * Uniwersalny assembler do mapowania Entity → DTO.
 */
public interface AssemblerDto<E, D, P extends Parameters<E, D>> {
    /**
     * Konwertuje encję na DTO.
     */
    public D toDto(P parameters);
}
