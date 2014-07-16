package org.asyncj;

import java.util.function.Function;

/**
 * Represents container for synchronous resource that can be represented as
 * asynchronous object.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public interface AsyncResourceAccess<R> {

    /**
     * Processes underlying resource asynchronously.
     * @param reader The resource processor.
     * @param <V> Type of the resource processing result.
     * @return
     */
    <V> AsyncResult<V> get(final ThrowableFunction<R, V> reader);

    void get(final AsyncCallback<R> callback);

    /**
     *
     * @param reader
     * @param <V>
     * @return
     */
    <V> AsyncResult<V> get(final Function<R, AsyncResult<V>> reader);
}
