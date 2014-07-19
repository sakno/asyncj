package org.asyncj;

import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Represents scheduler that supports prioritized task execution.
 */
public interface PriorityTaskScheduler extends TaskScheduler {

    /**
     * Schedules a new task.
     * @param task The computation to execute asynchronously.
     * @param <O> Type of the asynchronous computation result.
     * @return An object that represents state of the asynchronous computation.
     */
    <O, T extends IntSupplier & Callable<? extends O>> AsyncResult<O> enqueue(final T task);

    <O, T extends AsyncResult<O> & RunnableFuture<O>, F extends IntSupplier & Function<PriorityTaskScheduler, T>> AsyncResult<O> enqueueDirect(final F taskFactory);
}
