package com.osatum.wbn.application.shared.request;

public record ClientRequestMetadataInput(
        String remoteAddress,
        String forwardedFor,
        String realIp,
        String userAgent
) {
}
