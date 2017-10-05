package ru.lanwen.feign;

import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;

/**
 * Throws RetryableException if status greater or equal to 500
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
                    null
            );
        }
        return exception;
    }
}
