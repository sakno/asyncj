package org.asyncj.impl;

import org.asyncj.AsyncResult;
import org.asyncj.PriorityTaskScheduler;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Represents an abstract class for constructing prioritized task scheduler.
 * @author Roman Sakno
 * @since 1.0
 * @version 1.0
 */
public abstract class AbstractPriorityTaskScheduler extends AbstractTaskScheduler implements PriorityTaskScheduler {
    /**
     * Represents priority stub indicating that the underlying scheduler should automatically
     * select the most suitable priority for asynchronous task.
     */
    protected static final int AUTO_PRIORITY = -1;

    protected AbstractPriorityTaskScheduler(){
    }

    protected abstract <V, T extends AsyncResult<V> & RunnableFuture<V>> AsyncResult<V> enqueueTask(final T task, final int priority);

    protected <V> PriorityTask<V> createTask(final Callable<? extends V> task, final int priority) {
        return new PriorityTask<V>(this, priority) {
            @Override
            public V call() throws Exception {
                return task.call();
            }
        };
    }

    @Override
    protected final  <V> Task<V> createTask(final Callable<? extends V> task) {
        return createTask(task, task instanceof IntSupplier ? ((IntSupplier) task).getAsInt() : AUTO_PRIORITY);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected final  <V, T extends AsyncResult<V> & RunnableFuture<V>> AsyncResult<V> enqueueTask(final T task) {
        return enqueueTask(task, task instanceof IntSupplier ? ((IntSupplier) task).getAsInt() : AUTO_PRIORITY);
    }

    private <O> AsyncResult<O> enqueue(final PriorityTask<O> task){
        if(isScheduled(task))
            return enqueueTask(task, task.getAsInt());
        else throw new IllegalArgumentException(String.format("Task %s is not scheduled by this scheduler", task));
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <O, T extends IntSupplier & Callable<? extends O>> AsyncResult<O> enqueue(final T task) {
        Objects.requireNonNull(task, "task is null.");
        return task instanceof PriorityTask<?> ? enqueue((PriorityTask<O>)task) : enqueue(createTask(task));
    }

    @Override
    public final <O, T extends AsyncResult<O> & RunnableFuture<O>, F extends IntSupplier & Function<PriorityTaskScheduler, T>> AsyncResult<O> enqueueDirect(final F taskFactory) {
        return enqueueTask(taskFactory.apply(this), taskFactory.getAsInt());
    }
}
