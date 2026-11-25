package com.osatum.wbn.infrastructure.security;

import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.repository.user.UserRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.InstitutionMembershipSpringDataRepository;
import com.osatum.wbn.infrastructure.security.principal.InstitutionPrincipal;
import com.osatum.wbn.infrastructure.security.service.ClaimsConstant;
import com.osatum.wbn.infrastructure.security.service.JwtPrincipalService;
import com.osatum.wbn.infrastructure.security.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";


    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final InstitutionRepository institutionRepository;
    private final InstitutionMembershipSpringDataRepository membershipRepository;
    private final JwtPrincipalService jwtPrincipalService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String auth = req.getHeader(org.springframework.http.HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }
        String token = auth.substring(7);

        try {
            var claims = jwtService.parse(token);
            String type = claims.get(ClaimsConstant.CLAIMS_TYPE, String.class);

            String rolesCsv = claims.get(ClaimsConstant.CLAIMS_ROLES, String.class);
            String institutionRole = claims.get(ClaimsConstant.CLAIMS_INSTITUTION_ROLE, String.class);
            String institutionType = claims.get(ClaimsConstant.CLAIMS_INSTITUTION_TYPE, String.class);
            Long institutionId = claims.get(ClaimsConstant.CLAIMS_INSTITUTION_ID, Long.class);
            Long loginSessionId = claims.get(ClaimsConstant.CLAIMS_LOGIN_SESSION_ID, Long.class);


            var authorities = Arrays.stream((rolesCsv == null ? "" : rolesCsv).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(SimpleGrantedAuthority::new)
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

            if (ClaimsConstant.CLAIMS_TYPE_USER.equals(type)) {
                Long userId = Long.valueOf(claims.getSubject());
                String email = claims.get(ClaimsConstant.CLAIMS_EMAIL, String.class);

                var user = userRepository.findById(userId);

                if (email == null || user.isEmpty() || !canAuthenticate(user.get())) {
                    chain.doFilter(req, res);
                    return;
                }

                var institution = institutionRepository.findById(institutionId);
                if (institution.isEmpty() || !canAuthenticate(institution.get())) {
                    chain.doFilter(req, res);
                    return;
                }

                var membership = membershipRepository.findByUserIdAndInstitutionIdAndRole(
                        userId,
                        institutionId,
                        InstitutionRole.valueOf(institutionRole)
                );
                if (membership.isEmpty() || !canAuthenticate(membership.get())) {
                    chain.doFilter(req, res);
                    return;
                }

                if (institutionRole != null && !institutionRole.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(institutionRole));
                }


                var principal = jwtPrincipalService.buildUserPrincipal(userId, membership.get().getId(), loginSessionId);
                //new UserPrincipal(userId, email, institutionId, InstitutionType.valueOf(institutionType), InstitutionRole.valueOf(institutionRole));

                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(new InstitutionAuthDetails(institutionId, institutionRole));

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } else if (ClaimsConstant.CLAIMS_TYPE_INSTITUTION.equals(type)) {
                var institution = institutionRepository.findById(institutionId);
                if (institution.isEmpty() || !canAuthenticate(institution.get())) {
                    chain.doFilter(req, res);
                    return;
                }
                String institutionName = claims.get(ClaimsConstant.CLAIMS_INSTITUTION_NAME, String.class);
                var principal = new InstitutionPrincipal(institutionId, institutionName, InstitutionType.valueOf(institutionType), InstitutionRole.valueOf(institutionRole), institutionName, loginSessionId);
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException | IllegalArgumentException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(req, res);
    }


    private boolean canAuthenticate(AppUser user) {
        return EntityState.ACTIVE.equals(user.getState());
    }

    private boolean canAuthenticate(Institution institution) {
        return EntityState.ACTIVE.equals(institution.getState());
    }

    private boolean canAuthenticate(InstitutionMembership membership) {
        return EntityState.ACTIVE.equals(membership.getState())
                && membership.getRole() != InstitutionRole.INSTITUTION_PENDING;
    }

}
