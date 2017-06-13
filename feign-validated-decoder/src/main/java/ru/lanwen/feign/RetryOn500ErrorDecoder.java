package ru.lanwen.feign;

import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;

import java.time.Instant;
import java.util.Date;

import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * Throws RetryableException if status >= 500
 *
 * @author lanwen (Merkushev Kirill)
 */
public class RetryOn500ErrorDecoder extends ErrorDecoder.Default {

    @Override
    public Exception decode(String methodKey, Response response) {
        Exception exception = super.decode(methodKey, response);
        if (response.status() >= 500) {
            return new RetryableException(
                    exception.getMessage(),
                    exception,
                    Date.from(Instant.now().plus(1, SECONDS))
            );
        }
        return exception;
    }
}