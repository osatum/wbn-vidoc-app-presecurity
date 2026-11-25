package com.osatum.wbn.application.security;

import java.util.Optional;

public interface CurrentActorProvider {

    Actor currentActor();

    Optional<Actor> findCurrentActor();
}
