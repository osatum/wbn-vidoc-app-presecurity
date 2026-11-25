package com.osatum.wbn.application.user.mapper;

import com.osatum.wbn.application.user.dto.AccountActivationLogListDto;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.registration.AccountActivationLog;
import com.osatum.wbn.domain.model.registration.ActivationToken;
import com.osatum.wbn.domain.model.user.AppUser;
import org.springframework.stereotype.Component;

@Component
public class AccountActivationLogMapper {
    public AccountActivationLogListDto toListDto(AccountActivationLog log) {
        AccountActivationLogListDto dto = new AccountActivationLogListDto();
        dto.setId(log.getId());
        dto.setAttemptedAt(log.getAttemptedAt());
        dto.setStatus(log.getStatus());
        dto.setIpAddress(log.getIpAddress());
        dto.setUserAgent(log.getUserAgent());
        dto.setBrowser(resolveBrowser(log.getUserAgent()));
        dto.setOperatingSystem(resolveOperatingSystem(log.getUserAgent()));
        dto.setErrorMessage(log.getErrorMessage());
        dto.setTermsAndPrivacyPolicyAccepted(log.getTermsAndPrivacyPolicyAccepted());
        dto.setPersonalDataProcessingAcknowledged(log.getPersonalDataProcessingAcknowledged());

        AppUser user = log.getUser();
        if (user != null) {
            dto.setUserId(user.getId());
            dto.setUserEmail(user.getEmail());
            dto.setUserFirstName(user.getFirstName());
            dto.setUserLastName(user.getLastName());
        }

        Institution institution = log.getInstitution();
        if (institution != null) {
            dto.setInstitutionId(institution.getId());
            dto.setInstitutionName(institution.getName());
        }

        InstitutionMembership membership = log.getMembership();
        if (membership != null) {
            dto.setMembershipId(membership.getId());
        }

        ActivationToken token = log.getActivationToken();
        if (token != null) {
            dto.setActivationTokenId(token.getId());
        }

        return dto;
    }

    private String resolveBrowser(String userAgent) {
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
