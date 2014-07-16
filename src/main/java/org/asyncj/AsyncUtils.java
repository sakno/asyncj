package org.asyncj;

import org.asyncj.impl.SingleThreadScheduler;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Represents additional routines for asynchronous programming.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public final class AsyncUtils {

    private AsyncUtils(){

    }

    private static TaskScheduler globalScheduler = new SingleThreadScheduler();
    private static boolean useOverriddenScheduler = false;

    public static TaskScheduler getGlobalScheduler(){
        return globalScheduler;
    }

    public static boolean setGlobalScheduler(final TaskScheduler scheduler){
        Objects.requireNonNull(scheduler, "scheduler is null.");
        if(useOverriddenScheduler) return false;
        globalScheduler = scheduler;
        return useOverriddenScheduler = true;
    }

    public static <I, O> AsyncResult<O> mapReduce(final TaskScheduler scheduler,
                                                  final Iterator<? extends I> collection,
                                                  final BiFunction<? super I, ? super O, ? extends O> mapper,
                                                  final AsyncResult<? extends O> initialValue) {
        return initialValue.then(new Function<O, AsyncResult<O>>() {
            @Override
            public AsyncResult<O> apply(O accumulator) {
                if (collection.hasNext()) {
                    accumulator = mapper.apply(collection.next(), accumulator);
                    return scheduler.successful(accumulator).then(this);
                } else return scheduler.successful(accumulator);
            }
        });
    }

    public static <I, O> AsyncResult<O> mapReduce(final TaskScheduler scheduler,
                                                  final Iterator<? extends I> collection,
                                                  final BiFunction<? super I, ? super O, ? extends O> mapper,
                                                  final O initialValue) {
        return mapReduce(scheduler, collection, mapper, scheduler.successful(initialValue));
    }

    public static <I, O> AsyncResult<O> mapReduceAsync(final TaskScheduler scheduler,
                                                  final Iterator<? extends I> collection,
                                                  final BiFunction<? super I, ? super O, AsyncResult<O>> mapper,
                                                  final AsyncResult<O> initialValue) {
        return initialValue.then((O accumulator) -> collection.hasNext() ? mapper.apply(collection.next(), accumulator) : scheduler.successful(accumulator));
    }

    public static <I, O> AsyncResult<O> mapReduceAsync(final TaskScheduler scheduler,
                                                  final Iterator<? extends I> collection,
                                                  final BiFunction<? super I, ? super O, AsyncResult<O>> mapper,
                                                  final O initialValue) {
        return mapReduceAsync(scheduler, collection, mapper, scheduler.successful(initialValue));
    }

    public static <I1, I2, O> AsyncResult<O> reduce(final AsyncResult<I1> value1,
                                                    final AsyncResult<I2> value2,
                                                    final BiFunction<? super I1, ? super I2, ? extends O> reducer){
        return value1.then((I1 v1) -> value2.<O>then((I2 v2) -> reducer.apply(v1, v2)));
    }

    public static <I1, I2, O> AsyncResult<O> reduceAsync(final AsyncResult<I1> value1, final AsyncResult<I2> value2,
                                                         final BiFunction<? super I1, ? super I2, AsyncResult<O>> reducer){
        return value1.then((I1 v1)-> value2.then((I2 v2)-> reducer.apply(v1, v2)));
    }

    public static <I, O> AsyncResult<O> reduce(final TaskScheduler scheduler,
                                               final Iterator<AsyncResult<I>> values,
                                               final ThrowableFunction<? super Collection<I>, O> reducer){
        return mapReduceAsync(scheduler,
                values,
                (AsyncResult<I> result, Collection<I> collection) -> values.next().then((I elem) -> { collection.add(elem); return collection; }),
                new Vector<>(values instanceof Collection ? ((Collection) values).size() : 10)).
                then(reducer);
    }

    public static <I, O> AsyncResult<O> reduceAsync(final TaskScheduler scheduler,
                                               final Iterator<AsyncResult<I>> values,
                                               final Function<? super Collection<I>, AsyncResult<O>> reducer) {
        return mapReduceAsync(scheduler,
                values,
                (AsyncResult<I> result, Collection<I> collection) -> values.next().then((I elem) -> {
                    collection.add(elem);
                    return collection;
                }),
                new Vector<>(values instanceof Collection ? ((Collection) values).size() : 10)).
                then(reducer);
    }
}
