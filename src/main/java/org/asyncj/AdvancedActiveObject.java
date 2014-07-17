package org.asyncj;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public abstract class AdvancedActiveObject<Q extends Enum<Q>> {
    private final TaskSchedulerGroup<Q> schedulers;

    protected AdvancedActiveObject(final TaskSchedulerGroup<Q> schedulers){
        this.schedulers = Objects.requireNonNull(schedulers, "schedulers is null.");
    }

    protected final <O> AsyncResult<O> successful(final Q queue, final O value) {
        return schedulers.successful(queue, value);
    }

    protected final <O> AsyncResult<O> failure(final Q queue, final Exception err) {
        return schedulers.failure(queue, err);
    }

    protected final <O> AsyncResult<O> enqueue(final Q queue, final Callable<O> task) {
        return schedulers.enqueue(queue, task);
    }

    protected final <I> void enqueue(final Q queue,
                                     final AsyncCallback<? super I> callback,
                                     final I value) {
        Objects.requireNonNull(callback, "callback is null.");
        schedulers.enqueue(queue, () -> {
            callback.invoke(value, null);
            return null;
        });
    }

    protected final <I> void enqueue(final Q queue,
                                     final AsyncCallback<? super I> callback,
                                     final Exception err) {
        Objects.requireNonNull(callback, "callback is null.");
        schedulers.enqueue(queue, () -> {
            callback.invoke(null, err);
            return null;
        });
    }

    protected final <I, O> AsyncResult<O> mapReduce(final Q queue,
                                                    final Iterator<? extends I> collection,
                                                    final BiFunction<? super I, ? super O, ? extends O> mapper,
                                                    final AsyncResult<? extends O> initialValue) {
        return schedulers.processScheduler(queue, scheduler->AsyncUtils.mapReduce(scheduler, collection, mapper, initialValue));
    }

    protected final <I, O> AsyncResult<O> mapReduce(final Q queue,
                                                    final Iterator<? extends I> collection,
                                                    final BiFunction<? super I, ? super O, ? extends O> mapper,
                                                    final O initialValue) {
        return schedulers.processScheduler(queue, scheduler->AsyncUtils.mapReduce(scheduler, collection, mapper, initialValue));
    }

    protected final <I, O> AsyncResult<O> mapReduceAsync(final Q queue,
                                                         final Iterator<? extends I> collection,
                                                         final BiFunction<? super I, ? super O, AsyncResult<O>> mapper,
                                                         final AsyncResult<O> initialValue) {
        return schedulers.processScheduler(queue, scheduler->AsyncUtils.mapReduceAsync(scheduler, collection, mapper, initialValue));
    }

    protected final <I, O> AsyncResult<O> mapReduceAsync(final Q queue,
                                                         final Iterator<? extends I> collection,
                                                         final BiFunction<? super I, ? super O, AsyncResult<O>> mapper,
                                                         final O initialValue) {
        return schedulers.processScheduler(queue, scheduler->AsyncUtils.mapReduceAsync(scheduler, collection, mapper, initialValue));
    }

    protected final <I, O> AsyncResult<O> reduce(final Q queue,
                                                 final Iterator<AsyncResult<I>> values,
                                                 final ThrowableFunction<? super Collection<I>, O> reducer) {
        return schedulers.processScheduler(queue, scheduler->AsyncUtils.reduce(scheduler, values, reducer));
    }

    protected final <I, O> AsyncResult<O> reduceAsync(final Q queue,
                                                      final Iterator<AsyncResult<I>> values,
                                                      final Function<? super Collection<I>, AsyncResult<O>> reducer) {
        return schedulers.processScheduler(queue, scheduler->AsyncUtils.reduceAsync(scheduler, values, reducer));
    }
}
