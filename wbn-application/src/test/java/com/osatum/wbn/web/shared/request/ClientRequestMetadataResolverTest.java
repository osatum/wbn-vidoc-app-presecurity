package com.osatum.wbn.web.shared.request;

import com.osatum.wbn.application.shared.request.ClientRequestMetadataResolutionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClientRequestMetadataResolverTest {

    @Test
    void isInstantiatedBySpringUsingResolutionServiceConstructor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test-properties", Map.of("app.web.trusted-proxies", ""))
            );
            context.register(ClientRequestMetadataResolutionService.class, ClientRequestMetadataResolver.class);
            context.refresh();

            assertThat(context.getBean(ClientRequestMetadataResolver.class)).isNotNull();
        }
    }

    @Test
    void ignoresForwardedHeadersFromUntrustedSender() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20");

        var metadata = new ClientRequestMetadataResolver("10.0.0.10").resolve(request);

        assertThat(metadata.ipAddress()).isEqualTo("203.0.113.10");
    }

    @Test
    void selectsNearestUntrustedAddressBeforeTrustedProxyChain() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.11");
        request.addHeader("X-Forwarded-For", "1.1.1.1, 198.51.100.20, 10.0.0.10");

        var metadata = new ClientRequestMetadataResolver("10.0.0.10,10.0.0.11").resolve(request);

        assertThat(metadata.ipAddress()).isEqualTo("198.51.100.20");
    }

    @Test
    void sanitizesAndLimitsUserAgent() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "browser\r\n" + "x".repeat(1100));

        var metadata = new ClientRequestMetadataResolver("").resolve(request);

        assertThat(metadata.userAgent()).doesNotContain("\r", "\n").hasSize(1024);
    }

    @Test
    void resolvesIpv6ClientAddressFromTrustedProxy() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8:ffff::10");
        request.addHeader("X-Forwarded-For", "2001:db8:1234::20, 2001:db8:ffff::10");

        var metadata = new ClientRequestMetadataResolver("2001:db8:ffff::10").resolve(request);

        assertThat(metadata.ipAddress()).isEqualTo("2001:db8:1234:0:0:0:0:20");
    }

    @Test
    void rejectsMalformedForwardedAddressFromTrustedProxy() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8:ffff::10");
        request.addHeader("X-Forwarded-For", "[2001:db8:1234::20]:443");

        var metadata = new ClientRequestMetadataResolver("2001:db8:ffff::10").resolve(request);

        assertThat(metadata.ipAddress()).isEmpty();
    }

    @Test
    void supportsIpv6CidrForTrustedProxyConfiguration() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8:ffff:1::10");
        request.addHeader("X-Forwarded-For", "2001:db8:1234::20, 2001:db8:ffff:1::10");

        var metadata = new ClientRequestMetadataResolver("2001:db8:ffff::/48").resolve(request);

        assertThat(metadata.ipAddress()).isEqualTo("2001:db8:1234:0:0:0:0:20");
    }
}
