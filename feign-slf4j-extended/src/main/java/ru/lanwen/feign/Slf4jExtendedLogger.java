package ru.lanwen.feign;

import feign.Logger;
import feign.Request;
import feign.Response;
import feign.Util;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static feign.Util.UTF_8;
import static feign.Util.checkNotNull;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;

/**
 * @author lanwen (Merkushev Kirill)
 */

public class Slf4jExtendedLogger extends Logger {
    private static final String REQ_ID_KEY = "req-id";
    private static final int HTTP_NO_CONTENT_204 = 204;
    private static final int HTTP_RESET_CONTENT_205 = 205;
    private final ThreadLocal<String> requestId = new ThreadLocal<>();
    private final ThreadLocal<String> retryed = new ThreadLocal<>();
    private final org.slf4j.Logger log;

    public Slf4jExtendedLogger(Class<?> clazz) {
        this(LoggerFactory.getLogger(clazz));
    }

    public Slf4jExtendedLogger(String name) {
        this(LoggerFactory.getLogger(name));
    }

    Slf4jExtendedLogger(org.slf4j.Logger logger) {
        this.log = logger;
    }

    @Override
    protected void logRequest(String configKey, Level logLevel, Request request) {
        if (!log.isDebugEnabled()) {
            return;
        }

        if (retryed.get() != null) {
            retryed.remove();
        } else {
            requestId.remove();
        }

        List<Map.Entry<String, Object>> fields = new ArrayList<>(asList(
                field(REQ_ID_KEY, reqId()),
                field("call", configKey),
                field("method", request.method()),
                field("uri", request.url())
        ));

        if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {
            fields.add(field("headers", request.headers()));
        }

        if (request.body() != null) {
            fields.add(field("length", request.body().length));
            if (logLevel.ordinal() >= Level.FULL.ordinal()) {
                String bodyText = request.charset() != null ? new String(request.body(), request.charset()) : null;
                fields.add(field("body", bodyText != null ? bodyText.replaceAll("\t", "\\\\t") : "binary_data"));
            }
        }

        log(fields);
    }

    @Override
    protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime)
            throws IOException {

        if (!log.isDebugEnabled()) {
            return response;
        }

        List<Map.Entry<String, Object>> fields = new ArrayList<>(asList(
                field(REQ_ID_KEY, reqId()),
                field("status", response.status()),
                field("reason", response.reason()),
                field("elapsed-ms", elapsedTime)
        ));

        if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {
            fields.add(field("headers", response.headers()));
        }

        int bodyLength;
        if (response.body() != null
                // HTTP 204 No Content "...response MUST NOT include a message-body"
                && !(response.status() == HTTP_NO_CONTENT_204
                // HTTP 205 Reset Content "...response MUST NOT include an entity"
                || response.status() == HTTP_RESET_CONTENT_205)) {

            byte[] bodyData = Util.toByteArray(response.body().asInputStream());
            bodyLength = bodyData.length;
            fields.add(field("length", bodyLength));
            if (logLevel.ordinal() >= Level.FULL.ordinal() && bodyLength > 0) {
                fields.add(
                        field("body", decodeOrDefault(bodyData, UTF_8, "binary_data")
                                .replaceAll("\t", "\\\\t"))
                );
            }
            log(fields);
            return response.toBuilder().body(bodyData).build();
        }

        log(fields);
        return response;
    }

    @Override
    protected void logRetry(String configKey, Level logLevel) {
        if (!log.isDebugEnabled()) {
            return;
        }

        retryed.set(requestId.get());
        log(asList(
                field("state", "retry"),
                field(REQ_ID_KEY, requestId.get())
        ));
    }

    @Override
    protected IOException logIOException(String configKey, Level logLevel, IOException ioe, long elapsedTime) {
        if (!log.isDebugEnabled()) {
            return ioe;
        }

        List<Map.Entry<String, Object>> fields = new ArrayList<>(asList(
                field("state", "error"),
                field(REQ_ID_KEY, reqId()),
                field("class", ioe.getClass().getSimpleName()),
                field("message", ioe.getMessage()),
                field("elapsed-ms", elapsedTime)
        ));

        if (logLevel.ordinal() >= Level.FULL.ordinal()) {
            StringWriter sw = new StringWriter();
            ioe.printStackTrace(new PrintWriter(sw));
            fields.add(field("trace", sw.toString().replaceAll("\t", " ")));
        }

        log(fields);
        return ioe;
    }


    @Override
    protected void log(String configKey, String format, Object... args) {
        log.debug(format, args);
    }

    private String reqId() {
        if (requestId.get() == null) {
            requestId.set(UUID.randomUUID().toString());
        }
        return requestId.get();
    }

    private void log(Collection<Map.Entry<String, Object>> tskv) {
        String line = tskv.stream()
                .map(entry -> String.format("%s=[%s]", entry.getKey(), entry.getValue()))
                .collect(joining("\t"));
        log("", "http\t{}", line);
    }

    private static Map.Entry<String, Object> field(String key, Object value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    static String decodeOrDefault(byte[] data, Charset charset, String defaultValue) {
        if (data == null) {
            return defaultValue;
        }
        checkNotNull(charset, "charset");
        try {
            return charset.newDecoder().decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException ex) { //NOSONAR
            return defaultValue;
        }
    }

}
