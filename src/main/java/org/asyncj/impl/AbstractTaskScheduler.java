package org.asyncj.impl;

import org.asyncj.*;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Represents abstract task scheduler.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public abstract class AbstractTaskScheduler implements TaskScheduler {

    protected AbstractTaskScheduler(){

    }

    /**
     * Wraps the specified value into completed asynchronous result.
     *
     * @param value
     * @return
     */
    @Override
    public final  <O> AsyncResult<O> successful(final O value) {
        return enqueueTask(this.<O>createTask(() -> value));
    }

    private <O> AsyncResult<O> cancelled(final String reason) {
        return enqueueTask(this.<O>createTask(() -> {
            throw new CancellationException(reason);
        }));
    }

    /**
     * Wraps the specified exception into completed asynchronous result.
     *
     * @param err
     * @return
     */
    @Override
    public final  <O> AsyncResult<O> failure(final Exception err) {
        return enqueueTask(this.<O>createTask(()->{
            throw err;
        }));
    }

    protected <V> Task<V> createTask(final Callable<? extends V> task){
        return new Task<V>(this) {
            @Override
            public V call() throws Exception {
                return task.call();
            }
        };
    }

    protected abstract <V, T extends AsyncResult<V> & RunnableFuture<V>> AsyncResult<V> enqueueTask(final T task);

    @Override
    public final  <O, T extends AsyncResult<O> & RunnableFuture<O>> AsyncResult<O> enqueueDirect(final Function<TaskScheduler, T> taskFactory) {
        return enqueueTask(taskFactory.apply(this));
    }

    private <O> AsyncResult<O> enqueue(final Task<O> task){
        if(isScheduled(task))
            return enqueueTask(task);
        else throw new IllegalArgumentException(String.format("Task %s is not scheduled by this scheduler", task));
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <O> AsyncResult<O> enqueue(final Callable<? extends O> task) {
        Objects.requireNonNull(task, "task is null.");
        return task instanceof Task<?> ? enqueue((Task<O>)task) : enqueue(createTask(task));
    }

    /**
     * Determines whether the specified asynchronous computation scheduled by this object.
     *
     * @param ar The asynchronous computation to check.
     * @return {@literal true}, if the specified asynchronous computation is scheduled by this object; otherwise, {@literal false}.
     */
    @Override
    public final boolean isScheduled(final AsyncResult<?> ar) {
        if(ar instanceof Task<?>)
            return ((Task<?>)ar).isScheduledBy(this);
        else return ar instanceof ProxyTask<?, ?> && ((ProxyTask<?, ?>) ar).isScheduledBy(this);
    }
}
