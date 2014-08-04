package asyncj;

import java.util.Objects;
import java.util.function.Function;

/**
 * Represents a container that wraps plain Java object into active object.
 * <p>
 *     By default, the public interface of this container doesn't provide priority-based
 *     asynchronous operations, because the client of the active object cannot specify the priority
 *     of the operation directly.
 * </p>
 * @author Roman Sakno
 * @since 1.0
 * @version 1.1
 */
public abstract class AbstractAsyncResourceAccess<R> extends ActiveObject implements AsyncResourceAccess<R> {
    /**
     * Initializes a new asynchronous container.
     * @param scheduler Task scheduler used to execute methods of this active object asynchronously. Cannot be {@literal null}.
     */
    protected AbstractAsyncResourceAccess(final TaskScheduler scheduler){
        super(scheduler);
    }

    /**
     * Gets encapsulated resource.
     * @return The encapsulated resource.
     */
    protected abstract R getResource();

    private <V> AsyncResult<V> get(final ThrowableFunction<R, V> reader, final R resource){
        Objects.requireNonNull(reader, "reader is null.");
        return submit(() -> reader.apply(resource));
    }

    /**
     * Processes the encapsulated resource asynchronously.
     * @param reader The resource processor. Cannot be {@literal null}.
     * @param <V> Type of the processing result.
     * @return The object that represents state of the asynchronous resource processing.
     */
    @Override
    public final  <V> AsyncResult<V> get(final ThrowableFunction<R, V> reader) {
        return get(reader, getResource());
    }

    /**
     * Processes the encapsulated resource asynchronously.
     * @param callback The callback that accepts the encapsulated resource. Cannot be {@literal null}.
     */
    @Override
    public final void get(final AsyncCallback<R> callback) {
        submit(callback, getResource());
    }

    /**
     * Processes the encapsulated resource asynchronously.
     * @param reader The resource processor. Cannot be {@literal null}.
     * @param <V> Type of the processing result.
     * @return The object that represents asynchronous state of the processing.
     */
    @Override
    public final  <V> AsyncResult<V> get(final Function<R, AsyncResult<V>> reader) {
        return successful(getResource()).then(reader);
    }
}
