package com.osatum.wbn.application.auth.mapper;

import com.osatum.wbn.application.auth.dto.LoginSessionViewDto;
import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.auth.LoginSessionEndReason;
import com.osatum.wbn.domain.model.auth.LoginSessionStatus;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface LoginSessionMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "institutionId", source = "institution.id")
    @Mapping(target = "status", expression = "java(resolveStatus(session, now))")
    @Mapping(target = "endReason", expression = "java(resolveEndReason(session, now))")
    LoginSessionViewDto toDto(LoginSession session, @Context Instant now);

    default LoginSessionStatus resolveStatus(LoginSession session, Instant now) {
        if (isExpiredWithoutEnd(session, now)) {
            return LoginSessionStatus.EXPIRED;
        }

        return session.getStatus();
    }

    default LoginSessionEndReason resolveEndReason(LoginSession session, Instant now) {
        if (isExpiredWithoutEnd(session, now)) {
            return LoginSessionEndReason.EXPIRED;
        }

        return session.getEndReason();
    }

    private static boolean isExpiredWithoutEnd(LoginSession session, Instant now) {
        return session.getEndedAt() == null
                && session.getExpiresAt() != null
                && !session.getExpiresAt().isAfter(now);
    }
}
