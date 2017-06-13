package ru.lanwen.feign;

import feign.Response;
import feign.codec.Decoder;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.function.Consumer;

/**
 * Validates decoded response if it matches class parameter
 *
 * @author lanwen (Merkushev Kirill)
 */
@RequiredArgsConstructor
public class ValidatedDecoder<T> implements Decoder {

    private final Decoder delegate;
    private final Class<T> aClass;
    private final Consumer<T> action;

    /**
     * Applies action if decoded response has same type as requested
     *
     * @return modified decoded object or the original object if it has another class
     */
    @Override
    public Object decode(Response response, Type type) throws IOException {
        Object decoded = delegate.decode(response, type);

        if (aClass.equals(type)) {
            T obj = aClass.cast(decoded);
            action.accept(obj);
        }

        return decoded;
    }
}
