package com.osatum.wbn.application.shared.exception;

public class ActivationTokenAlreadyUsedException extends IllegalArgumentException {

    public ActivationTokenAlreadyUsedException() {
        super("Activation token was already used.");
    }
}
