package org.asyncj;

import java.util.Objects;
import java.util.function.Function;

/**
 * Represents a container that represents plain Java object as active object.
 */
public abstract class AbstractAsyncResourceAccess<R> extends ActiveObject implements AsyncResourceAccess<R> {
    protected AbstractAsyncResourceAccess(final TaskScheduler scheduler){
        super(scheduler);
    }

    protected abstract R getResource();

    private <V> AsyncResult<V> get(final ThrowableFuntion<R, V> reader, final R resource){
        Objects.requireNonNull(reader, "reader is null.");
        return enqueue(()->reader.apply(resource));
    }

    @Override
    public final  <V> AsyncResult<V> get(final ThrowableFuntion<R, V> reader) {
        return get(reader, getResource());
    }

    @Override
    public final void get(final AsyncCallback<R> callback) {
        enqueue(callback, getResource());
    }

    @Override
    public final  <V> AsyncResult<V> get(final Function<R, AsyncResult<V>> reader) {
        return successful(getResource()).then(reader);
    }
}
