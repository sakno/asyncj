package org.asyncj.impl;

import org.asyncj.PriorityTaskScheduler;
import org.asyncj.TaskScheduler;

import java.util.concurrent.Callable;

/**
 * Created by rvsakno on 17.07.2014.
 */
public abstract class PriorityTask<V, P extends Comparable<P>> extends Task<V> implements PriorityTaskScheduler.PriorityItem<P> {
    private final P priority;

    protected PriorityTask(final PriorityTaskScheduler<P> scheduler, final P priority) {
        this((TaskScheduler) scheduler, priority);
    }

    private PriorityTask(final TaskScheduler scheduler, final P priority){
        super(scheduler);
        this.priority = priority;
    }

    /**
     * Gets priority associated with this task.
     * @return The priority associated with this task.
     */
    public final P getPriority(){
        return priority;
    }

    @Override
    <O> PriorityTask<O, P> newChildTask(final TaskScheduler scheduler, final Callable<? extends O> task) {
        return new PriorityTask<O, P>(scheduler, priority) {
            @Override
            public O call() throws Exception {
                return task.call();
            }
        };
    }
}
