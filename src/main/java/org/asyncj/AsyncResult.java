package org.asyncj;

import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Represents state of the asynchronous computation.
 * @param <V> Type of the asynchronous computation result.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public interface AsyncResult<V> extends Future<V> {

    /**
     * Attaches a new asynchronous computation to this state.
     * <p>
     *     Attached task will be executed after completion of this computation.
     * </p>
     * @param action The action implementing attached asynchronous computation if this computation
     *               is completed successfully. Cannot be {@literal null}.
     * @param errorHandler The action implementing attached asynchronous computation if this computation
     *               is failed. May be {@literal null}.
     * @param <O> Type of the attached asynchronous computation result.
     * @return The object that represents state of the attached asynchronous computation.
     */
    <O> AsyncResult<O> then(final Function<? super V, AsyncResult<O>> action,
                            final Function<Exception, AsyncResult<O>> errorHandler);


    /**
     * Attaches a new asynchronous computation to this state.
     * @param action The action implementing attached asynchronous computation if this computation
     *               is completed successfully. Cannot be {@literal null}.
     * @param errorHandler The action implementing attached asynchronous computation if this computation
     *               is failed. May be {@literal null}.
     * @param <O> Type of the attached asynchronous computation result.
     * @return The object that represents state of the attached asynchronous computation.
     */
    <O> AsyncResult<O> then(final ThrowableFunction<? super V, ? extends O> action,
                                                   final ThrowableFunction<Exception, ? extends O> errorHandler);

    /**
     * Attaches the completion callback.
     * @param callback The completion callback. Cannot be {@literal null}.
     */
    void onCompleted(final AsyncCallback<? super V> callback);

    /**
     * Attaches a new asynchronous computation to this state.
     * <p>
     *     This method represents short version of {@link #then(java.util.function.Function, java.util.function.Function)}
     *     method. If asynchronous computation represented by this object will fail then this method returns
     *     the error without calling an error handler (because it is not presented).
     * </p>
     * @param action The action implementing attached asynchronous computation if this computation
     *               is completed successfully. Cannot be {@literal null}.
     * @param <O> Type of the attached asynchronous computation result.
     * @return The object that represents state of the attached asynchronous computation.
     */
    default <O> AsyncResult<O> then(final Function<? super V, AsyncResult<O>> action){
        return then(action, null);
    }

    /**
     * Schedules a new synchronous computation depends on completion of this computation.
     * @param action The continuation action in synchronous style. Cannot be {@literal null}.
     * @param <O> Type of the result in the continuation chain.
     * @return THe object that represents state of the chained computation.
     */
    default <O> AsyncResult<O> then(final ThrowableFunction<? super V, ? extends O> action){
        return then(action, null);
    }

    /**
     * Gets state of the asynchronous computation.
     * @return The state of the asynchronous computation.
     */
    AsyncResultState getAsyncState();

}
