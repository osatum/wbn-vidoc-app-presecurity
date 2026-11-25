package com.osatum.wbn.web.shared.request;

import com.osatum.wbn.application.shared.request.ClientRequestMetadata;
import com.osatum.wbn.application.shared.request.ClientRequestMetadataInput;
import com.osatum.wbn.application.shared.request.ClientRequestMetadataResolutionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ClientRequestMetadataResolver {

    private final ClientRequestMetadataResolutionService resolutionService;

    @Autowired
    public ClientRequestMetadataResolver(ClientRequestMetadataResolutionService resolutionService) {
        this.resolutionService = resolutionService;
    }

    public ClientRequestMetadataResolver(String trustedProxies) {
        this(new ClientRequestMetadataResolutionService(trustedProxies));
    }

    public ClientRequestMetadata resolve(HttpServletRequest request) {
        return resolutionService.resolve(new ClientRequestMetadataInput(
                request.getRemoteAddr(),
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getHeader("User-Agent")
        ));
    }
}
