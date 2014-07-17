package org.asyncj;

import org.asyncj.impl.TaskExecutor;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Represents additional routines for asynchronous programming.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public final class AsyncUtils {
    private static final class PriorityCallable<V, P extends Comparable<P>> implements PriorityTaskScheduler.PriorityItem<P>, Callable<V>{
        private final Callable<? extends V> task;
        private final P priority;

        public PriorityCallable(final Callable<? extends V> task, final P priority){
            this.task = Objects.requireNonNull(task, "task is null");
            this.priority = priority;
        }

        @Override
        public V call() throws Exception {
            return task.call();
        }

        @Override
        public P getPriority() {
            return priority;
        }
    }

    private AsyncUtils(){

    }

    private static TaskScheduler globalScheduler;
    private static boolean useOverriddenScheduler = false;

    public static TaskScheduler getGlobalScheduler(){
        if(globalScheduler == null)
            globalScheduler = TaskExecutor.newSingleThreadExecutor();
        return globalScheduler;
    }

    public static boolean setGlobalScheduler(final TaskScheduler scheduler){
        if(useOverriddenScheduler) return false;
        globalScheduler = Objects.requireNonNull(scheduler, "scheduler is null.");
        return useOverriddenScheduler = true;
    }

    public static TaskScheduler createScheduler(final ExecutorService executor){
        return new TaskExecutor(executor);
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
        return value1.then((I1 v1) -> value2.then((I2 v2) -> reducer.apply(v1, v2)));
    }

    static <I, O> AsyncResult<O> reduce(final TaskScheduler scheduler,
                                        final Iterator<AsyncResult<I>> values,
                                        final ThrowableFunction<? super Collection<I>, O> reducer,
                                        final Callable<? extends Collection<I>> initialVector){
        return mapReduceAsync(scheduler,
                values,
                (AsyncResult<I> result, Collection<I> collection) -> values.next().then((I elem) -> { collection.add(elem); return collection; }),
                scheduler.enqueue(initialVector)).
                then(reducer);
    }

    static <V> Callable<? extends Collection<V>> getInitialVectorProvider(final Iterator<?> values) {
        return () -> new Vector<>(values instanceof Collection ? ((Collection) values).size() : 10);
    }

    public static <I, O> AsyncResult<O> reduce(final TaskScheduler scheduler,
                                               final Iterator<AsyncResult<I>> values,
                                               final ThrowableFunction<? super Collection<I>, O> reducer){
        return reduce(scheduler,
                values,
                reducer,
                AsyncUtils.<I>getInitialVectorProvider(values));
    }

    static <I, O> AsyncResult<O> reduceAsync(final TaskScheduler scheduler,
                                                    final Iterator<AsyncResult<I>> values,
                                                    final Function<? super Collection<I>, AsyncResult<O>> reducer,
                                                    final Callable<? extends Collection<I>> initialVector) {
        return mapReduceAsync(scheduler,
                values,
                (AsyncResult<I> result, Collection<I> collection) -> values.next().then((I elem) -> {
                    collection.add(elem);
                    return collection;
                }),
                scheduler.enqueue(initialVector)).
                then(reducer);
    }

    public static <I, O> AsyncResult<O> reduceAsync(final TaskScheduler scheduler,
                                               final Iterator<AsyncResult<I>> values,
                                               final Function<? super Collection<I>, AsyncResult<O>> reducer) {
        return reduceAsync(scheduler, values, reducer,
                AsyncUtils.<I>getInitialVectorProvider(values));
    }

    public static ThreadFactory createDaemonThreadFactory(final int threadPriority,
                                                          final ThreadGroup group,
                                                          final ClassLoader contextClassLoader){
        return r->{
            final Thread t = new Thread(group, r);
            t.setDaemon(true);
            t.setPriority(threadPriority);
            t.setContextClassLoader(contextClassLoader);
            return t;
        };
    }

    public static ThreadFactory createDaemonThreadFactory(final int threadPriority,
                                                          final ThreadGroup group){
        return createDaemonThreadFactory(threadPriority, group, Thread.currentThread().getContextClassLoader());
    }

    public static <V, P extends Comparable<P>> Callable<? extends V> prioritize(final Callable<? extends V> task, final P priority){
        return new PriorityCallable<>(task, priority);
    }
}
