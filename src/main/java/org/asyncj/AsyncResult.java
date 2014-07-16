package org.asyncj;

import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Represents state of the asynchronous computation.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public interface AsyncResult<V> extends Future<V> {

    <O> AsyncResult<O> then(final Function<? super V, AsyncResult<O>> action,
                            final Function<Exception, AsyncResult<O>> errorHandler);


    <O> AsyncResult<O> then(final ThrowableFunction<? super V, ? extends O> action,
                                                   final ThrowableFunction<Exception, ? extends O> errorHandler);

    void onCompleted(final AsyncCallback<? super V> callback);

    default <O> AsyncResult<O> then(final Function<? super V, AsyncResult<O>> action){
        return then(action, null);
    }

    /**
     * Schedules a new synchronous computation depends on completion of this computation.
     * <p>
     *     If this computation fails then synchronous action will accepts {@literal null} as input argument.
     * </p>
     * @param action
     * @param <O>
     * @return
     */
    default <O> AsyncResult<O> then(final ThrowableFunction<? super V, ? extends O> action){
        return then(action, null);
    }

    AsyncResultState getAsyncState();

}
