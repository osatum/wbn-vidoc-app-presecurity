package com.osatum.wbn.infrastructure.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PreAuthRequiredException extends ResponseStatusException {
    public PreAuthRequiredException() {
        super(HttpStatus.UNAUTHORIZED, "PRE_AUTH token is missing or invalid");
    }
}
