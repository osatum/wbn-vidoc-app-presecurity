package com.osatum.wbn.infrastructure.security;

/**
 * Dodatkowe informacje przypięte do Authentication.
 */
public record InstitutionAuthDetails(Long institutionId, String institutionRole) {
}
