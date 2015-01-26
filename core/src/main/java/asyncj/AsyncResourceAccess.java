package asyncj;

import java.util.function.Function;

/**
 * Represents container for synchronous resource that can be represented as
 * asynchronous object.
 * @author Roman Sakno
 * @version 1.1
 * @since 1.0
 */
public interface AsyncResourceAccess<R> {

    /**
     * Processes underlying resource asynchronously.
     * @param reader The resource processor. Cannot be {@literal null}.
     * @param <V> Type of the resource processing result.
     * @return The object that represents state of the asynchronous processing.
     */
    <V> AsyncResult<V> get(final ThrowableFunction<R, V> reader);

    /**
     * Processes underlying resource asynchronously.
     * @param callback The callback that accepts the resource to process. Cannot be {@literal null}.
     */
    void get(final AsyncCallback<R> callback);

    /**
     * Processes underlying resource asynchronously.
     * @param reader The resource processor. Cannot be {@literal null}.
     * @param <V> Type of the processing result.
     * @return The object that represents state of the asynchronous processing.
     */
    <V> AsyncResult<V> get(final Function<R, AsyncResult<V>> reader);
}
