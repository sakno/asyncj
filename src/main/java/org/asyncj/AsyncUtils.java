package org.asyncj;

import org.asyncj.impl.SingleThreadScheduler;

import java.util.Iterator;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Represents additional routines for asynchronous programming.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public final class AsyncUtils {
    private static interface Iteration<I, O>{
        void mapReduce(final I input, final O previous, final AsyncCallback<O> next);
    }
    private static final class Box<T>{
        public T value;

        public Box(final T initialValue){
            value = initialValue;
        }
    }

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
        return true;
    }

    private static <I, O> AsyncResult<O> mapReduce(final TaskScheduler scheduler, final Iterator<I> collection, final BiFunction<I, O, O> mapper, final Box<O> accumulator){
        if(!collection.hasNext()) return scheduler.successful(accumulator.value);
        return scheduler.successful(accumulator.value).then(new Function<O, AsyncResult<O>>() {
            @Override
            public AsyncResult<O> apply(O o) {
                if(collection.hasNext()) {
                    accumulator.value = mapper.apply(collection.next(), accumulator.value);
                    return scheduler.successful(accumulator.value).then(this);
                }
                else return scheduler.successful(accumulator.value);
            }
        });
    }

    public static <I, O> AsyncResult<O> mapReduce(final TaskScheduler scheduler, final Iterator<I> collection, final BiFunction<I, O, O> mapper, final O initialValue){
        return mapReduce(scheduler, collection, mapper, new Box<O>(initialValue));
    }
}
