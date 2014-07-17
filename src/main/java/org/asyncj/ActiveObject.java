package org.asyncj;

import org.asyncj.impl.PriorityTaskExecutor;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Represents active object which decouples method execution from method invocation and resides in their own thread of control.
 * <p>
 *     This class supports simple task executors and priority task executors both. If you don't use priority task scheduler
 *     then don't specify type of the {@code P} generic parameter in the derived class.
 * </p>
 * @param <P> Type of the priority identifier.
 * @author Roman Sakno
 * @since 1.0
 * @version 1.0
 */
public abstract class ActiveObject<P extends Comparable<P>> {
    private final TaskScheduler scheduler;

    protected ActiveObject(final TaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler is null.");
    }

    protected ActiveObject(final PriorityTaskExecutor<P> scheduler){
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler is null.");
    }

    protected final <O> AsyncResult<O> successful(final O value) {
        return scheduler.successful(value);
    }

    protected final <O> AsyncResult<O> failure(final Exception err) {
        return scheduler.failure(err);
    }

    protected final <O> AsyncResult<O> enqueue(final Callable<O> task) {
        final ActiveObject obj;
        return scheduler.enqueue(task);
    }

    protected final <O> AsyncResult<O> enqueue(final Callable<O> task, final P priority) {
        return AsyncUtils.enqueueWithPriority(scheduler, task, priority);
    }

    protected final <I> void enqueue(final AsyncCallback<? super I> callback, final I value) {
        Objects.requireNonNull(callback, "callback is null.");
        enqueue(() -> {
            callback.invoke(value, null);
            return null;
        });
    }

    protected final <I> void enqueue(final AsyncCallback<? super I> callback, final I value, final P priority) {
        Objects.requireNonNull(callback, "callback is null.");
        enqueue(() -> {
            callback.invoke(value, null);
            return null;
        }, priority);
    }

    protected final <I> void enqueue(final AsyncCallback<? super I> callback, final Exception err) {
        Objects.requireNonNull(callback, "callback is null.");
        enqueue(() -> {
            callback.invoke(null, err);
            return null;
        });
    }

    protected final <I> void enqueue(final AsyncCallback<? super I> callback, final Exception err, final P priority){
        Objects.requireNonNull(callback, "callback is null.");
        enqueue(()->{
            callback.invoke(null, err);
            return null;
        }, priority);
    }

    protected final <I, O> AsyncResult<O> mapReduce(final Iterator<? extends I> collection,
                                                    final BiFunction<? super I, ? super O, ? extends O> mapper,
                                                    final AsyncResult<? extends O> initialValue) {
        return AsyncUtils.mapReduce(scheduler, collection, mapper, initialValue);
    }

    protected final <I, O> AsyncResult<O> mapReduce(final Iterator<? extends I> collection,
                                                    final BiFunction<? super I, ? super O, ? extends O> mapper,
                                                    final O initialValue) {
        return AsyncUtils.mapReduce(scheduler, collection, mapper, initialValue);
    }

    protected final <I, O> AsyncResult<O> mapReduceAsync(final Iterator<? extends I> collection,
                                                         final BiFunction<? super I, ? super O, AsyncResult<O>> mapper,
                                                         final AsyncResult<O> initialValue) {
        return AsyncUtils.mapReduceAsync(scheduler, collection, mapper, initialValue);
    }

    protected final <I, O> AsyncResult<O> mapReduceAsync(final Iterator<? extends I> collection,
                                                         final BiFunction<? super I, ? super O, AsyncResult<O>> mapper,
                                                         final O initialValue) {
        return AsyncUtils.mapReduceAsync(scheduler, collection, mapper, initialValue);
    }

    protected final <I, O> AsyncResult<O> reduce(final Iterator<AsyncResult<I>> values,
                                                 final ThrowableFunction<? super Collection<I>, O> reducer) {
        return AsyncUtils.reduce(scheduler, values, reducer);
    }

    protected final <I, O> AsyncResult<O> reduceAsync(final Iterator<AsyncResult<I>> values,
                                                      final Function<? super Collection<I>, AsyncResult<O>> reducer) {
        return AsyncUtils.reduceAsync(scheduler, values, reducer);
    }
}
