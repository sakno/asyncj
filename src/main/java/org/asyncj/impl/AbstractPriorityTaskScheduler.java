package org.asyncj.impl;

import org.asyncj.AsyncResult;
import org.asyncj.PriorityTaskScheduler;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;
import java.util.function.Function;

/**
 * Represents an abstract class for constructing prioritized task scheduler.
 * @author Roman Sakno
 * @since 1.0
 * @version 1.0
 */
public abstract class AbstractPriorityTaskScheduler<P extends Comparable<P>> extends AbstractTaskScheduler implements PriorityTaskScheduler<P> {
    private final P normalPriority;

    protected AbstractPriorityTaskScheduler(final P normalPriority){
        this.normalPriority = Objects.requireNonNull(normalPriority, "normalPriority is null.");
    }

    protected abstract <V, T extends AsyncResult<V> & RunnableFuture<V>> AsyncResult<V> enqueueTask(final T task, final P priority);

    protected <V> PriorityTask<V, P> createTask(final Callable<? extends V> task, final P priority) {
        return new PriorityTask<V, P>(this, priority) {
            @Override
            public V call() throws Exception {
                return task.call();
            }
        };
    }

    @Override
    protected final  <V> Task<V> createTask(final Callable<? extends V> task) {
        return createTask(task, normalPriority);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected final  <V, T extends AsyncResult<V> & RunnableFuture<V>> AsyncResult<V> enqueueTask(final T task) {
        return enqueueTask(task, task instanceof PriorityItem<?> ? ((PriorityItem<P>) task).getPriority() : normalPriority);
    }

    private <O> AsyncResult<O> enqueue(final PriorityTask<O, P> task){
        if(isScheduled(task))
            return enqueueTask(task, task.getPriority());
        else throw new IllegalArgumentException(String.format("Task %s is not scheduled by this scheduler", task));
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <O, T extends PriorityItem<P> & Callable<? extends O>> AsyncResult<O> enqueue(final T task) {
        Objects.requireNonNull(task, "task is null.");
        return task instanceof PriorityTask<?, ?> ? enqueue((PriorityTask<O, P>)task) : enqueue(createTask(task));
    }

    @Override
    public final <O, T extends AsyncResult<O> & RunnableFuture<O>, F extends PriorityItem<P> & Function<PriorityTaskScheduler<P>, T>> AsyncResult<O> enqueueDirect(final F taskFactory) {
        return enqueueTask(taskFactory.apply(this), taskFactory.getPriority());
    }
}
