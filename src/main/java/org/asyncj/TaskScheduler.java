package org.asyncj;

import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;
import java.util.function.Function;

/**
 * Represents task scheduler.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public interface TaskScheduler {

    /**
     * Interrupts thread associated with the specified asynchronous computation.
     * <p>
     *     This is infrastructure method and you should not use it directly from your code.
     * </p>
     * @param ar The asynchronous computation to interrupt.
     * @return {@literal true}, if the specified asynchronous computation is interrupted; otherwise, {@literal false}.
     */
    boolean interrupt(final AsyncResult<?> ar);

    /**
     *
     * @param task
     * @param <O>
     * @return
     */
    <O> AsyncResult<O> enqueue(final Callable<? extends O> task);

    <O, T extends AsyncResult<O> & RunnableFuture<O>> AsyncResult<O> enqueue(final Function<TaskScheduler, T> taskFactory);

    /**
     * Wraps the specified value into completed asynchronous result.
     * @param value
     * @param <O>
     * @return
     */
    default <O> AsyncResult<O> successful(final O value) {
        return enqueue(() -> value);
    }

    /**
     * Wraps the specified exception into completed asynchronous result.
     * @param err
     * @param <O>
     * @return
     */
    default <O> AsyncResult<O> failure(final Exception err){
        return enqueue(()->{
            throw err;
        });
    }

    /**
     * Determines whether the specified asynchronous computation scheduled by this object.
     * @param ar The asynchronous computation to check.
     * @return {@literal true}, if the specified asynchronous computation is scheduled by this object; otherwise, {@literal false}.
     */
    boolean isScheduled(final AsyncResult<?> ar);
}
