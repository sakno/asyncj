package org.asyncj;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.function.Function;

/**
 * Represents task scheduler.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public interface TaskScheduler extends ExecutorService {

    /**
     * Interrupts thread associated with the specified asynchronous computation.
     * <p>
     *     This is infrastructure method and you should not use it directly from your code.
     * </p>
     * @param ar The asynchronous computation to interrupt.
     * @return {@literal true}, if the specified asynchronous computation is interrupted; otherwise, {@literal false}.
     */
    boolean interrupt(final AsyncResult<?> ar);

    @Override
    <T> AsyncResult<T> submit(final Callable<T> task);

    @Override
    default <T> AsyncResult<T> submit(final Runnable task, final T result) {
        return submit(() -> {
            task.run();
            return result;
        });
    }

    @Override
    default AsyncResult<Void> submit(final Runnable task) {
        return submit(task, null);
    }

    <O, T extends AsyncResult<O> & RunnableFuture<O>> AsyncResult<O> submitDirect(final Function<TaskScheduler, T> taskFactory);

    boolean isScheduled(final AsyncResult<?> ar);
}
