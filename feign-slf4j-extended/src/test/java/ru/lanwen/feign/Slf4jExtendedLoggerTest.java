package ru.lanwen.feign;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import feign.Feign;
import feign.FeignException;
import feign.Headers;
import feign.Logger.Level;
import feign.Param;
import feign.Request;
import feign.RequestLine;
import feign.RetryableException;
import feign.Retryer;
import feign.jackson.JacksonEncoder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockResolver.Wiremock;
import ru.lanwen.wiremock.ext.WiremockUriResolver;
import ru.lanwen.wiremock.ext.WiremockUriResolver.WiremockUri;

import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.substringBetween;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author lanwen (Merkushev Kirill)
 */
@ExtendWith({
        WiremockResolver.class,
        WiremockUriResolver.class
})
@Slf4j
public class Slf4jExtendedLoggerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("test.logger");

    private TestAppender appender;

    @BeforeEach
    void setUp() throws InterruptedException {
        appender = new TestAppender();
        appender.start();
        ch.qos.logback.classic.Logger.class.cast(LOGGER).setLevel(ch.qos.logback.classic.Level.DEBUG);
        ch.qos.logback.classic.Logger.class.cast(LOGGER).addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        ch.qos.logback.classic.Logger.class.cast(LOGGER).detachAppender(appender);
        appender.stop();
    }

    @Test
    void shouldLogReqAndResp(@Wiremock WireMockServer server, @WiremockUri String uri) throws Exception {

        server.stubFor(post(urlPathMatching("/path"))
                .willReturn(aResponse().withBody("{}")));

        Feign.builder()
                .encoder(new JacksonEncoder())
                .logger(new Slf4jExtendedLogger(LOGGER))
                .logLevel(Level.FULL)
                .target(Dummy.class, uri)
                .get("some");

        List<String> events = appender.lines();
        assertThat(events, hasSize(2));
        String first = events.get(0);
        String reqId = reqIdFrom(first);

        assertThat(first, allOf(
                containsString("call=[Dummy#get(String)]"),
                containsString("method=[POST]"),
                containsString("uri=[http://localhost:"),
                containsString("headers=[{Content-type=[application/json], Content-Length=[21]}]"),
                containsString("length=[21]"),
                containsString("body=[{")
        ));
        assertThat(events.get(1), allOf(
                containsString("status=[200]"),
                containsString("reason=[OK]"),
                containsString("elapsed-ms="),
                containsString("headers=[{"),
                containsString("length=[2]"),
                containsString("body=[{}]"),
                containsString(reqId)
        ));

    }


    @Test
    void shouldLogRetry(@Wiremock WireMockServer server, @WiremockUri String uri) throws Exception {
        server.stubFor(post(urlPathMatching("/path"))
                .willReturn(aResponse().withFixedDelay(300)));

        assertThrows(
                RetryableException.class,
                () -> Feign.builder()
                        .encoder(new JacksonEncoder())
                        .logger(new Slf4jExtendedLogger(LOGGER))
                        .logLevel(Level.FULL)
                        .retryer(new Retryer.Default(100, 200, 2))
                        .options(new Request.Options(100, 200))
                        .target(Dummy.class, uri)
                        .get("some")
        );
        List<String> events = appender.lines();
        assertThat(events, hasSize(5));
        assertThat(events, hasItems(
                containsString("state=[retry]"),
                containsString("state=[error]")
        ));
    }

    @Test
    void shouldLogExceptions(@Wiremock WireMockServer server, @WiremockUri String uri) throws Exception {

        server.stubFor(post(urlPathMatching("/path"))
                .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

        assertThrows(
                FeignException.class,
                () -> Feign.builder()
                        .encoder(new JacksonEncoder())
                        .logger(new Slf4jExtendedLogger(LOGGER))
                        .logLevel(Level.FULL)
                        .target(Dummy.class, uri)
                        .get("some")
        );
        List<String> events = appender.lines();
        assertThat(events, hasSize(2));
        assertThat(events, hasItems(
                containsString("trace="),
                containsString("state=[error]"),
                containsString("message=[Premature EOF]"),
                containsString("elapsed-ms=")
        ));
    }


    @Test
    void shouldLogNothingOnDisabledDebug(@Wiremock WireMockServer server, @WiremockUri String uri) throws Exception {
        Logger logger = mock(Logger.class);

        when(logger.isDebugEnabled()).thenReturn(false);

        server.stubFor(post(urlPathMatching("/path"))
                .willReturn(aResponse().withBody("{}")));

        Feign.builder()
                .encoder(new JacksonEncoder())
                .logger(new Slf4jExtendedLogger(logger))
                .logLevel(Level.FULL)
                .target(Dummy.class, uri)
                .get("");

        verify(logger, times(2)).isDebugEnabled();
        verifyNoMoreInteractions(logger);
    }


    @Test
    void shouldNotLogBodyIfDisabled(@Wiremock WireMockServer server, @WiremockUri String uri) throws Exception {

        server.stubFor(post(urlPathMatching("/path"))
                .willReturn(aResponse().withBody("{}")));

        Feign.builder()
                .encoder(new JacksonEncoder())
                .logger(new Slf4jExtendedLogger(LOGGER))
                .logLevel(Level.HEADERS)
                .target(Dummy.class, uri)
                .get("some");

        List<String> events = appender.lines();
        assumeThat(events, hasSize(2));
        assertThat(events, not(hasItems(
                containsString("body=")
        )));
    }


    @Test
    void shouldNotChangeReqIdAfterRetry(@Wiremock WireMockServer server, @WiremockUri String uri) {
        server.stubFor(post(urlPathMatching("/path")).inScenario("1")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(504))
                .willSetStateTo("Normal"));

        server.stubFor(post(urlPathMatching("/path")).inScenario("1")
                .whenScenarioStateIs("Normal")
                .willReturn(aResponse().withStatus(200)));

        Dummy api = Feign.builder()
                .encoder(new JacksonEncoder())
                .logger(new Slf4jExtendedLogger(LOGGER))
                .logLevel(Level.FULL)
                .errorDecoder(new RetryOn500ErrorDecoder())
                .retryer(new Retryer.Default(100, 200, 2))
                .target(Dummy.class, uri);

        api.get("some");
        api.get("some");

        List<String> events = appender.lines();
        assertThat(events, hasSize(7));
        String retryLine = events.get(2);
        assertThat("retry-line", retryLine, containsString("state=[retry]"));
        String reqId = reqIdFrom(retryLine);
        assertThat("req-id (first)", reqId, not("null"));
        assertThat("first-req", reqIdFrom(events.get(0)), is(reqId));

        assertThat("next-req",
                reqIdFrom(events.get(5)),
                allOf(not(reqId), not("null"))
        );

    }


    private String reqIdFrom(String line) {
        return substringBetween(line, "req-id=[", "]");
    }

    interface Dummy {
        @RequestLine("POST /path")
        @Headers({
                "Content-type: application/json"
        })
        void get(@Param("body") String body);
    }

    @Getter
    public static class TestAppender extends AppenderBase<ILoggingEvent> {
        private List<ILoggingEvent> events = new ArrayList<>();

        TestAppender() {
            setName("TEST-APPENDER");
            setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        }

        public List<String> lines() {
            return events.stream().map(ILoggingEvent::getFormattedMessage).collect(toList());
        }

        @Override
        protected void append(ILoggingEvent e) {
            events.add(e);
        }

    }
}
