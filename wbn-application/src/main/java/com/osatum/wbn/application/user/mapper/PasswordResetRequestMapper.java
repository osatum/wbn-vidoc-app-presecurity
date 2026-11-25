package com.osatum.wbn.application.user.mapper;

import com.osatum.wbn.application.user.dto.PasswordResetRequestListDto;
import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequest;
import com.osatum.wbn.domain.model.user.AppUser;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetRequestMapper {
    public PasswordResetRequestListDto toListDto(PasswordResetRequest request) {
        PasswordResetRequestListDto dto = new PasswordResetRequestListDto();
        dto.setId(request.getId());
        dto.setEmail(request.getEmail());
        dto.setStatus(request.getStatus());
        dto.setRequestedAt(request.getRequestedAt());
        dto.setExpiresAt(request.getExpiresAt());
        dto.setClickedAt(request.getClickedAt());
        dto.setCompletedAt(request.getCompletedAt());
        dto.setInvalidatedAt(request.getInvalidatedAt());
        dto.setIpAddress(request.getIpAddress());
        dto.setUserAgent(request.getUserAgent());
        dto.setBrowser(request.getBrowser() == null ? resolveBrowser(request.getUserAgent()) : request.getBrowser());
        dto.setOperatingSystem(resolveOperatingSystem(request.getUserAgent()));
        dto.setEmailMessageLogId(request.getEmailMessageLogId());
        dto.setErrorMessage(request.getErrorMessage());

        AppUser user = request.getUser();
        if (user != null) {
            dto.setUserId(user.getId());
            dto.setUserFirstName(user.getFirstName());
            dto.setUserLastName(user.getLastName());
        }

        return dto;
    }

    public String resolveBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }

        String normalized = userAgent.toLowerCase();
        if (normalized.contains("edg/")) {
            return "Microsoft Edge";
        }
        if (normalized.contains("opr/") || normalized.contains("opera")) {
            return "Opera";
        }
        if (normalized.contains("firefox/")) {
            return "Firefox";
        }
        if (normalized.contains("chrome/") || normalized.contains("crios/")) {
            return "Chrome";
        }
        if (normalized.contains("safari/")) {
            return "Safari";
        }

        return "Other";
    }

    private String resolveOperatingSystem(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }

        String normalized = userAgent.toLowerCase();
        if (normalized.contains("windows")) {
            return "Windows";
        }
        if (normalized.contains("android")) {
            return "Android";
        }
        if (normalized.contains("iphone") || normalized.contains("ipad") || normalized.contains("ios")) {
            return "iOS";
        }
        if (normalized.contains("mac os") || normalized.contains("macintosh")) {
            return "macOS";
        }
        if (normalized.contains("linux")) {
            return "Linux";
        }

        return "Other";
    }
}
