package org.asyncj;

import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;
import java.util.function.Function;

/**
 * Represents scheduler that supports prioritized task execution.
 * @param <P> Type of the task
 */
public interface PriorityTaskScheduler<P extends Comparable<P>> extends TaskScheduler {
    /**
     * Represents prioritized item that can be scheduled by this scheduler.
     * @param <P> Type of the priority indicator.
     * @author Roman Sakno
     * @since 1.0
     * @version 1.0
     */
    public static interface PriorityItem<P extends Comparable<P>>{
        /**
         * Gets priority associated with this item.
         * @return The priority associated with this item.
         */
        P getPriority();
    }

    /**
     * Schedules a new task.
     * @param task The computation to execute asynchronously.
     * @param <O> Type of the asynchronous computation result.
     * @return An object that represents state of the asynchronous computation.
     */
    <O, T extends PriorityItem<P> & Callable<? extends O>> AsyncResult<O> enqueue(final T task);

    <O, T extends AsyncResult<O> & RunnableFuture<O>, F extends PriorityItem<P> & Function<PriorityTaskScheduler<P>, T>> AsyncResult<O> enqueueDirect(final F taskFactory);
}
