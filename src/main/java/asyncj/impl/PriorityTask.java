package asyncj.impl;

import asyncj.PriorityTaskScheduler;
import asyncj.TaskScheduler;

import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.function.IntSupplier;

/**
 * Represents priority-based implementation of the asynchronous task.
 * @param <V> Type of the asynchronous computation result.
 * @author Roman Sakno
 * @since 1.0
 * @version 1.0
 */
public abstract class PriorityTask<V> extends Task<V> implements Comparable<PriorityTask>, IntSupplier{
    private final int priority;

    /**
     * Initializes a new asynchronous task with given priority.
     * @param scheduler Priority-based scheduler that owns by the newly created task. Cannot be {@literal null}.
     * @param priority The priority of this task.
     */
    protected PriorityTask(final PriorityTaskScheduler scheduler, final int priority) {
        this((TaskScheduler) scheduler, priority);
    }

    private PriorityTask(final TaskScheduler scheduler, final int priority){
        super(scheduler);
        this.priority = priority;
    }

    /**
     * Gets a priority of this task.
     * @return A priority of this task.
     */
    @Override
    public final int getAsInt() {
        return priority;
    }

    @Override
    protected <O> PriorityTask<O> newChildTask(final TaskScheduler scheduler, final Callable<O> task) {
        return new PriorityTask<O>(scheduler, priority) {
            @Override
            public O call() throws Exception {
                return task.call();
            }
        };
    }

    @Override
    public final int compareTo(final PriorityTask o) {
        return Integer.compare(o.getAsInt(), getAsInt());
    }

    @Override
    public String toString() {
        final HashMap<String, Object> fields = new HashMap<>(4);
        fields.put("priority", priority);
        return toString(fields);
    }
}
