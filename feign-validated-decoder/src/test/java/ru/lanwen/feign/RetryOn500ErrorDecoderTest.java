package ru.lanwen.feign;

import com.github.tomakehurst.wiremock.WireMockServer;
import feign.Feign;
import feign.FeignException;
import feign.RequestLine;
import feign.RetryableException;
import feign.Retryer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockResolver.Wiremock;
import ru.lanwen.wiremock.ext.WiremockUriResolver;
import ru.lanwen.wiremock.ext.WiremockUriResolver.WiremockUri;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author lanwen (Merkushev Kirill)
 */
@ExtendWith({
        WiremockResolver.class,
        WiremockUriResolver.class
})
class RetryOn500ErrorDecoderTest {

    private static final int RETRY_COUNT = 3;

    @Test
    void shouldRetryOn500(@Wiremock WireMockServer server,
                          @WiremockUri String uri) throws Exception {
        server.stubFor(post(urlPathEqualTo("/path"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(
                RetryableException.class,
                () -> api(uri).get()
        );
        server.verify(RETRY_COUNT, postRequestedFor(urlPathEqualTo("/path")));
    }

    @Test
    void shouldNotRetryOn400(@Wiremock WireMockServer server,
                             @WiremockUri String uri) throws Exception {
        server.stubFor(post(urlPathEqualTo("/path"))
                .willReturn(aResponse().withStatus(400)));

        assertThrows(
                FeignException.class,
                () -> api(uri).get()
        );
        server.verify(1, postRequestedFor(urlPathEqualTo("/path")));
    }

    private static Dummy api(@WiremockUri String uri) {
        return Feign.builder()
                .retryer(new Retryer.Default(100, 500, RETRY_COUNT))
                .errorDecoder(new RetryOn500ErrorDecoder())
                .target(Dummy.class, uri);
    }

    interface Dummy {
        @RequestLine("POST /path")
        void get();
    }
}
