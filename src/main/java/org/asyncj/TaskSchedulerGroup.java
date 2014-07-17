package org.asyncj;

import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;
import java.util.function.Function;

/**
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public interface TaskSchedulerGroup<Q extends Enum<Q>> {
    <T> T processScheduler(final Q queue, final Function<TaskScheduler, T> processor);

    /**
     * Interrupts thread associated with the specified asynchronous computation.
     * <p>
     *     This is infrastructure method and you should not use it directly from your code.
     * </p>
     * @param queue Type of the scheduling queue.
     * @param ar The asynchronous computation to interrupt.
     * @return {@literal true}, if the specified asynchronous computation is interrupted; otherwise, {@literal false}.
     */
    boolean interrupt(final Q queue, final AsyncResult<?> ar);

    /**
     * @param queue Type of the scheduling queue.
     * @param task
     * @param <O>
     * @return
     */
    <O> AsyncResult<O> enqueue(final Q queue, final Callable<? extends O> task);

    <O, T extends AsyncResult<O> & RunnableFuture<O>> AsyncResult<O> enqueue(final Q queue, final Function<TaskScheduler, T> taskFactory);

    /**
     * Wraps the specified value into completed asynchronous result.
     * @param value
     * @param <O>
     * @return
     */
    default <O> AsyncResult<O> successful(final Q queue, final O value) {
        return enqueue(queue, () -> value);
    }

    /**
     * Wraps the specified exception into completed asynchronous result.
     * @param err
     * @param <O>
     * @return
     */
    default <O> AsyncResult<O> failure(final Q queue, final Exception err){
        return enqueue(queue, ()->{
            throw err;
        });
    }

    /**
     * Determines whether the specified asynchronous computation scheduled by this object.
     * @param ar The asynchronous computation to check.
     * @return {@literal true}, if the specified asynchronous computation is scheduled by this object; otherwise, {@literal false}.
     */
    boolean isScheduled(final Q queue, final AsyncResult<?> ar);
}
