package org.asyncj;

import org.asyncj.impl.TaskExecutor;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Represents additional routines for asynchronous programming.
 * <p>
 *     This class provides a few groups of routines for advanced asynchronous computation:
 *     <ul>
 *         <li>Algorithms - asynchronous implementation of core data processing algorithms, such as MapReduce and Reduce.
 *         The methods with {@code Async} postfix are used when accumulator or MapReduce iteration cannot be implemented
 *         in the synchronous style.</li>
 *         <li>Factory of thread factory - helper methods allows to create an instances of {@link java.util.concurrent.ThreadFactory}
 *         with specified parameters. These methods are useful for task scheduler writers only.</li>
 *         <li>Priority scheduling - helper methods allows to assign priority to the tasks
 *         represented by {@link java.util.concurrent.Callable}.</li>
 *     </ul>
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 * @see org.asyncj.TaskScheduler
 * @see org.asyncj.PriorityTaskScheduler
 */
public final class AsyncUtils {
    private static final class PriorityCallable<V, P extends Enum<P> & IntSupplier> implements IntSupplier, Callable<V> {
        private final Callable<? extends V> task;
        private final P priority;

        public PriorityCallable(final Callable<? extends V> task, final P priority) {
            this.task = Objects.requireNonNull(task, "task is null");
            this.priority = priority;
        }

        @Override
        public V call() throws Exception {
            return task.call();
        }

        @Override
        public int getAsInt() {
            return priority.getAsInt();
        }
    }

    private AsyncUtils(){

    }

    private static TaskScheduler globalScheduler;
    private static boolean useOverriddenScheduler = false;

    /**
     * Gets global task scheduler for executing asynchronous computation not associated with any active object.
     * @return An instance of the global scheduler.
     * @see #setGlobalScheduler(TaskScheduler)
     */
    public static TaskScheduler getGlobalScheduler(){
        if(globalScheduler == null)
            globalScheduler = TaskExecutor.newSingleThreadExecutor();
        return globalScheduler;
    }

    /**
     * Sets the global task scheduler for executing asynchronous computation not associated with any active object.
     * <p>
     *     The global scheduler may be changed once for entire JVM process.
     * </p>
     * @param scheduler The global scheduler. Cannot be {@literal null}.
     * @return {@literal true}, if global scheduler is overridden successfully; otherwise, {@literal false}.
     */
    public static boolean setGlobalScheduler(final TaskScheduler scheduler){
        if(useOverriddenScheduler) return false;
        globalScheduler = Objects.requireNonNull(scheduler, "scheduler is null.");
        return useOverriddenScheduler = true;
    }

    /**
     * Iterates over collection and performs filtering and summary operation.
     * @param scheduler The scheduler used to enqueue map-reduce operation. Cannot be {@literal null}.
     * @param collection The collection to process. Cannot be {@literal null}.
     * @param mr An object that implements map/reduce logic. Cannot be {@literal null}.
     * @param initialValue The initial value passed to the map-reduce algorithm at first iteration.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduced result.
     * @return An object that represents asynchronous result of the map-reduce algorithm.
     */
    public static <I, O> AsyncResult<O> mapReduce(final TaskScheduler scheduler,
                                                  final Iterator<? extends I> collection,
                                                  final BiFunction<? super I, ? super O, ? extends O> mr,
                                                  final AsyncResult<? extends O> initialValue) {
        return initialValue.then(new Function<O, AsyncResult<O>>() {
            @Override
            public AsyncResult<O> apply(O accumulator) {
                if (collection.hasNext()) {
                    accumulator = mr.apply(collection.next(), accumulator);
                    return scheduler.successful(accumulator).then(this);
                } else return scheduler.successful(accumulator);
            }
        });
    }

    /**
     * Iterates over collection and performs filtering and summary operation.
     * @param scheduler The scheduler used to enqueue map-reduce operation. Cannot be {@literal null}.
     * @param collection The collection to process. Cannot be {@literal null}.
     * @param mr An object that implements map/reduce logic. Cannot be {@literal null}.
     * @param initialValue The initial value passed to the map-reduce algorithm at first iteration.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduced result.
     * @return An object that represents asynchronous result of the map-reduce algorithm.
     */
    public static <I, O> AsyncResult<O> mapReduce(final TaskScheduler scheduler,
                                                  final Iterator<? extends I> collection,
                                                  final BiFunction<? super I, ? super O, ? extends O> mr,
                                                  final O initialValue) {
        return mapReduce(scheduler, collection, mr, scheduler.successful(initialValue));
    }

    /**
     * Iterates over collection and performs filtering and summary operation.
     * @param scheduler The scheduler used to enqueue map-reduce operation. Cannot be {@literal null}.
     * @param collection The collection to process. Cannot be {@literal null}.
     * @param mr An object that implements map/reduce logic. Cannot be {@literal null}.
     * @param initialValue The initial value passed to the map-reduce algorithm at first iteration. Cannot be {@literal null}.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduced result.
     * @return An object that represents asynchronous result of the map-reduce algorithm.
     */
    public static <I, O> AsyncResult<O> mapReduceAsync(final TaskScheduler scheduler,
                                                  final Iterator<? extends I> collection,
                                                  final BiFunction<? super I, ? super O, AsyncResult<O>> mr,
                                                  final AsyncResult<O> initialValue) {
        return initialValue.then((O accumulator) -> collection.hasNext() ? mr.apply(collection.next(), accumulator) : scheduler.successful(accumulator));
    }

    /**
     * Iterates over collection and performs filtering and summary operation.
     * @param scheduler The scheduler used to enqueue map-reduce operation. Cannot be {@literal null}.
     * @param collection The collection to process. Cannot be {@literal null}.
     * @param mr An object that implements map/reduce logic. Cannot be {@literal null}.
     * @param initialValue The initial value passed to the map-reduce algorithm at first iteration.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduced result.
     * @return An object that represents asynchronous result of the map-reduce algorithm.
     */
    public static <I, O> AsyncResult<O> mapReduceAsync(final TaskScheduler scheduler,
                                                  final Iterator<? extends I> collection,
                                                  final BiFunction<? super I, ? super O, AsyncResult<O>> mr,
                                                  final O initialValue) {
        return mapReduceAsync(scheduler, collection, mr, scheduler.successful(initialValue));
    }

    /**
     * Reduces two asynchronous values.
     * @param value1 The first asynchronous result to reduce. Cannot be {@literal null}.
     * @param value2 The second asynchronous result to reduce. Cannot be {@literal null}.
     * @param accumulator Associative, non-interfering and stateless function for combining two values. Cannot be {@literal null}.
     * @param <I1> The type of the first asynchronous result.
     * @param <I2> The type of the second asynchronous result.
     * @param <O> The type of the
     * @return The result of the reduction
     */
    public static <I1, I2, O> AsyncResult<O> reduce(final AsyncResult<I1> value1,
                                                    final AsyncResult<I2> value2,
                                                    final BiFunction<? super I1, ? super I2, ? extends O> accumulator){
        return value1.then((I1 v1) -> value2.<O>then((I2 v2) -> accumulator.apply(v1, v2)));
    }

    /**
     * Reduces two asynchronous values.
     * @param value1 The first asynchronous result to reduce. Cannot be {@literal null}.
     * @param value2 The second asynchronous result to reduce. Cannot be {@literal null}.
     * @param accumulator Associative, non-interfering and stateless function for combining two values. Cannot be {@literal null}.
     * @param <I1> The type of the first asynchronous result.
     * @param <I2> The type of the second asynchronous result.
     * @param <O> The type of the reduction result.
     * @return The result of the reduction.
     */
    public static <I1, I2, O> AsyncResult<O> reduceAsync(final AsyncResult<I1> value1, final AsyncResult<I2> value2,
                                                         final BiFunction<? super I1, ? super I2, AsyncResult<O>> accumulator){
        return value1.then((I1 v1) -> value2.then((I2 v2) -> accumulator.apply(v1, v2)));
    }

    static <I, O> AsyncResult<O> reduce(final TaskScheduler scheduler,
                                        final Iterator<AsyncResult<I>> values,
                                        final ThrowableFunction<? super Collection<I>, O> acc,
                                        final Callable<? extends Collection<I>> initialVector){
        return mapReduceAsync(scheduler,
                values,
                (AsyncResult<I> result, Collection<I> collection) -> values.next().then((I elem) -> { collection.add(elem); return collection; }),
                scheduler.enqueue(initialVector)).
                then(acc);
    }

    static <V> Callable<? extends Collection<V>> getInitialVectorProvider(final Iterator<?> values) {
        return () -> new Vector<>(values instanceof Collection ? ((Collection) values).size() : 10);
    }

    /**
     * Reduces the specified collection.
     * @param scheduler The scheduler used to enqueue reduce operation. Cannot be {@literal null}.
     * @param values An iterator over collection to reduce. Cannot be {@literal null}.
     * @param accumulator Associative, non-interfering and stateless function for combining two values. Cannot be {@literal null}.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduction result.
     * @return The result of the reduction.
     */
    public static <I, O> AsyncResult<O> reduce(final TaskScheduler scheduler,
                                               final Iterator<AsyncResult<I>> values,
                                               final ThrowableFunction<? super Collection<I>, O> accumulator){
        return reduce(scheduler,
                values,
                accumulator,
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

    /**
     * Reduces the specified collection.
     * @param scheduler The scheduler used to enqueue reduce operation. Cannot be {@literal null}.
     * @param values An iterator over collection to reduce. Cannot be {@literal null}.
     * @param accumulator Associative, non-interfering and stateless function for combining two values. Cannot be {@literal null}.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduction result.
     * @return The result of the reduction.
     */
    public static <I, O> AsyncResult<O> reduceAsync(final TaskScheduler scheduler,
                                               final Iterator<AsyncResult<I>> values,
                                               final Function<? super Collection<I>, AsyncResult<O>> accumulator) {
        return reduceAsync(scheduler, values, accumulator,
                AsyncUtils.<I>getInitialVectorProvider(values));
    }

    /**
     * Creates a new instance of the thread factory with given thread instantiation parameters.
     * <p>
     *     The returned factory always creates daemon threads. For more information, see {@link Thread#setDaemon(boolean)}.
     * </p>
     * @param threadPriority The priority of all threads which created by returned factory.
     * @param group Thread group for all threads which created by returned factory. May be {@literal null}.
     * @param contextClassLoader The context class loader used by all threads which created by returned factory. May be {@literal null}.
     * @return A new instance of the thread factory.
     */
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

    /**
     * Creates a new instance of the thread factory with given thread instantiation parameters.
     *  <p>
     *     The returned factory always creates daemon threads. For more information, see {@link Thread#setDaemon(boolean)}.
     * </p>
     * @param threadPriority The priority of all threads which created by returned factory.
     * @param group Thread group for all threads which created by returned factory. May be {@literal null}.
     * @return A new instance of the thread factory.
     */
    public static ThreadFactory createDaemonThreadFactory(final int threadPriority,
                                                          final ThreadGroup group){
        return createDaemonThreadFactory(threadPriority, group, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Assigns the priority to the specified task implementation.
     * @param task The task to wrap. Cannot be {@literal null}.
     * @param priority The priority to be assigned to the task.
     * @param <V> The type of the computation result.
     * @param <P> The type of the enum representing priority.
     * @return An instance of the task with attached priority.
     */
    public static <V, P extends Enum<P> & IntSupplier> Callable<? extends V> prioritize(final Callable<? extends V> task, final P priority){
        return new PriorityCallable<>(task, priority);
    }
}
