package ru.lanwen.feign;

import feign.Response;
import feign.codec.Decoder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author lanwen (Merkushev Kirill)
 */
class ValidatedDecoderTest {

    private static final String DECODED_BODY = "decoded body";
    private static final String ACTION_MESSAGE = "we are in action";

    private static final Decoder DELEGATE_DECODER = (response, type) -> DECODED_BODY;

    @Test
    void shouldCallActionIfItMatches() throws IOException {
        Throwable throwable = assertThrows(
                RuntimeException.class,
                () -> new ValidatedDecoder<>(
                        DELEGATE_DECODER,
                        String.class,
                        result -> {
                            throw new RuntimeException(ACTION_MESSAGE);
                        }
                ).decode(
                        Response.builder()
                                .headers(new HashMap<>())
                                .body("string body", StandardCharsets.UTF_8)
                                .status(200).build(),
                        String.class
                )
        );

        assertThat(throwable.getMessage(), is(ACTION_MESSAGE));
    }

    @Test
    void shouldNotCallActionIfItNotMatches() throws IOException {
        Object decoded = new ValidatedDecoder<>(
                DELEGATE_DECODER,
                Integer.class,
                result -> {
                    throw new RuntimeException(ACTION_MESSAGE);
                })
                .decode(
                        Response.builder()
                                .headers(new HashMap<>())
                                .body("string body", StandardCharsets.UTF_8)
                                .status(200).build(),
                        String.class
                );

        assertThat(decoded, is(DECODED_BODY));
    }
}
