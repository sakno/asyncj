package org.asyncj;

import java.util.concurrent.Callable;
import java.util.function.IntSupplier;

/**
 * Represents scheduler that supports prioritized task execution.
 * @author Roman Sakno
 * @since 1.0
 * @version 1.0
 */
public interface PriorityTaskScheduler extends TaskScheduler {

    /**
     * Schedules a new task.
     * @param task The computation to execute asynchronously.
     * @param <O> Type of the asynchronous computation result.
     * @return An object that represents state of the asynchronous computation.
     */
    <O, T extends IntSupplier & Callable<O>> AsyncResult<O> submit(final T task);
}
