package org.asyncj;

import org.asyncj.impl.TaskExecutor;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadFactory;
import java.util.function.*;

/**
 * Represents additional routines for asynchronous programming.
 * <p>
 *     This class provides a few groups of routines for advanced asynchronous computation:
 *     <ul>
 *         <li>Asynchronous primitives - a set of method for creating asynchronous primitives, such as scalars.</li>
 *         <li>Algorithms - asynchronous implementation of core data processing algorithms, such as MapReduce and Reduce.
 *         The methods with {@code flat} prefix are used when accumulator or MapReduce iteration cannot be implemented
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
     * This method is not thread-safe.
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
     * This method is not thread-safe.
     * <p>
     * The global scheduler may be changed once for entire JVM process.
     *
     * @param scheduler The global scheduler. Cannot be {@literal null}.
     * @return {@literal true}, if global scheduler is overridden successfully; otherwise, {@literal false}.
     */
    public static boolean setGlobalScheduler(final TaskScheduler scheduler){
        if(useOverriddenScheduler) return false;
        globalScheduler = Objects.requireNonNull(scheduler, "scheduler is null.");
        return useOverriddenScheduler = true;
    }

    /**
     * Shuts down the global scheduler and releases all resources associated with it.
     * This method is not thread-safe.
     * <p>
     * It is recommended to call this method inside of JVM shutdown hooks
     * @see Runtime#addShutdownHook(Thread)
     */
    public static void shutdownGlobalScheduler() {
        useOverriddenScheduler = false;
        if (globalScheduler != null) globalScheduler.shutdown();
        globalScheduler = null;
    }

    /**
     * Wraps the scalar into the asynchronous result.
     * @param scheduler The scheduler used to submit trivial task which returns the specified value. Cannot be {@literal null}.
     * @param value The value returned from the asynchronous computation.
     * @param <V> Type of the scalar.
     * @return A new scheduled task which returns the specified input argument.
     */
    public static <V> AsyncResult<V> successful(final TaskScheduler scheduler, final V value) {
        return Objects.requireNonNull(scheduler , "scheduler is null.").submit(() -> value);
    }

    /**
     * Wraps the specified exception into the asynchronous result.
     * @param scheduler The scheduler used to submit trivial task which throws the specified exception. Cannot be {@literal null}.
     * @param error An error to be thrown asynchronously. Cannot be {@literal null}.
     * @param <V> Type of the asynchronous result which never returns.
     * @return A new task that throws the specified exception asynchronously.
     * @see #failure(TaskScheduler, java.util.function.Supplier)
     */
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public static <V> AsyncResult<V> failure(final TaskScheduler scheduler, final Exception error) {
        Objects.requireNonNull(error, "error is null.");
        return Objects.requireNonNull(scheduler, "scheduler is null.").submit((Callable<V>) () -> {
            throw error;
        });
    }

    /**
     * Wraps the specified exception into the asynchronous result.
     * <p>
     * The exception factory will be called asynchronously, not immediately. Therefore, this
     * method is most preferred way (in compare with {@link #failure(TaskScheduler, Exception)} method) to create
     * exceptional tasks because created exception will have
     * a valid call stack.
     *
     * @param scheduler The scheduler used to submit trivial task which throws the specified exception. Cannot be {@literal null}.
     * @param errorFactory A factory that produces an instance of the exception to be thrown asynchronously.
     * @param <V> Type of the asynchronous result which never returns.
     * @return A new task that throws the specified exception asynchronously.
     */
    public static <V> AsyncResult<V> failure(final TaskScheduler scheduler, final Supplier<Exception> errorFactory) {
        return scheduler.submit((Callable<V>) () -> {
            throw errorFactory.get();
        });
    }

    public static <V> AsyncResult<V> cancellation(final TaskScheduler scheduler) {
        return failure(scheduler, CancellationException::new);
    }

    /**
     * Iterates over collection and performs filtering and summary operation.
     * @param scheduler The scheduler used to submit map-reduce operation. Cannot be {@literal null}.
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
                    return successful(scheduler, accumulator).then(this);
                } else return successful(scheduler, accumulator);
            }
        });
    }

    /**
     * Iterates over collection and performs filtering and summary operation.
     * @param scheduler The scheduler used to submit map-reduce operation. Cannot be {@literal null}.
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
        return mapReduce(scheduler, collection, mr, successful(scheduler, initialValue));
    }

    /**
     * Iterates over collection and performs filtering and summary operation.
     * @param scheduler The scheduler used to submit map-reduce operation. Cannot be {@literal null}.
     * @param collection The collection to process. Cannot be {@literal null}.
     * @param mr An object that implements map/reduce logic. Cannot be {@literal null}.
     * @param initialValue The initial value passed to the map-reduce algorithm at first iteration. Cannot be {@literal null}.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduced result.
     * @return An object that represents asynchronous result of the map-reduce algorithm.
     */
    public static <I, O> AsyncResult<O> flatMapReduce(final TaskScheduler scheduler,
                                                      final Iterator<? extends I> collection,
                                                      final BiFunction<? super I, ? super O, AsyncResult<O>> mr,
                                                      final AsyncResult<O> initialValue) {
        //(O accumulator) -> collection.hasNext() ? mr.apply(collection.next(), accumulator) : scheduler.successful(accumulator)
        return initialValue.then(new Function<O, AsyncResult<O>>() {
            @Override
            public AsyncResult<O> apply(final O accumulator) {
                return collection.hasNext() ? mr.apply(collection.next(), accumulator).then(this) : successful(scheduler, accumulator);
            }
        });
    }

    /**
     * Iterates over collection and performs filtering and summary operation.
     * @param scheduler The scheduler used to submit map-reduce operation. Cannot be {@literal null}.
     * @param collection The collection to process. Cannot be {@literal null}.
     * @param mr An object that implements map/reduce logic. Cannot be {@literal null}.
     * @param initialValue The initial value passed to the map-reduce algorithm at first iteration.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduced result.
     * @return An object that represents asynchronous result of the map-reduce algorithm.
     */
    public static <I, O> AsyncResult<O> flatMapReduce(final TaskScheduler scheduler,
                                                      final Iterator<? extends I> collection,
                                                      final BiFunction<? super I, ? super O, AsyncResult<O>> mr,
                                                      final O initialValue) {
        return flatMapReduce(scheduler, collection, mr, successful(scheduler, initialValue));
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
    public static <I1, I2, O> AsyncResult<O> flatReduce(final AsyncResult<I1> value1, final AsyncResult<I2> value2,
                                                        final BiFunction<? super I1, ? super I2, AsyncResult<O>> accumulator){
        return value1.then((I1 v1) -> value2.then((I2 v2) -> accumulator.apply(v1, v2)));
    }

    static <I, O> AsyncResult<O> reduce(final TaskScheduler scheduler,
                                        final Iterator<AsyncResult<I>> values,
                                        final ThrowableFunction<? super Collection<I>, O> acc,
                                        final Callable<Collection<I>> initialVector){
        return flatMapReduce(scheduler,
                values,
                (AsyncResult<I> result, Collection<I> collection) -> result.then((I elem) -> {
                    collection.add(elem);
                    return collection;
                }),
                scheduler.submit(initialVector)).
                then(acc);
    }

    /**
     * Reduces the specified collection.
     * @param scheduler The scheduler used to submit reduce operation. Cannot be {@literal null}.
     * @param values An iterator over collection to reduce. Cannot be {@literal null}.
     * @param accumulator Associative, non-interfering and stateless function for combining two values. Cannot be {@literal null}.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduction result.
     * @return The result of the reduction.
     */
    public static <I, O> AsyncResult<O> reduce(final TaskScheduler scheduler,
                                               final Iterator<AsyncResult<I>> values,
                                               final ThrowableFunction<? super Collection<I>, O> accumulator) {
        return reduce(scheduler,
                values,
                accumulator,
                Vector::new);
    }

    static <I, O> AsyncResult<O> flatReduce(final TaskScheduler scheduler,
                                            final Iterator<AsyncResult<I>> values,
                                            final Function<? super Collection<I>, AsyncResult<O>> reducer,
                                            final Callable<Collection<I>> initialVector) {
        return flatMapReduce(scheduler,
                values,
                (AsyncResult<I> result, Collection<I> collection) -> values.next().then((I elem) -> {
                    collection.add(elem);
                    return collection;
                }),
                scheduler.submit(initialVector)).
                then(reducer);
    }

    /**
     * Reduces the specified collection.
     * @param scheduler The scheduler used to submit reduce operation. Cannot be {@literal null}.
     * @param values An iterator over collection to reduce. Cannot be {@literal null}.
     * @param accumulator Associative, non-interfering and stateless function for combining two values. Cannot be {@literal null}.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduction result.
     * @return The result of the reduction.
     */
    public static <I, O> AsyncResult<O> flatReduce(final TaskScheduler scheduler,
                                                   final Iterator<AsyncResult<I>> values,
                                                   final Function<? super Collection<I>, AsyncResult<O>> accumulator) {
        return flatReduce(scheduler, values, accumulator,
                Vector::new);
    }

    /**
     * Executes asynchronous version of while-do loop.
     * <p>
     *     The loop iterations are sequential in time but not blocks the task scheduler thread.
     *     This version of while-do loop supports iteration with asynchronous result.
     * </p>
     * @param scheduler The scheduler used to submit loop iterations. Cannot be {@literal null}.
     * @param predicate Loop iteration. If predicate returns {@literal false} then loop will break. Cannot be {@literal null}.
     * @param initialState The initial state of the looping. This object may be used as mutable object that can be rested in predicate.
     * @param <I> Type of the looping state.
     * @return The object that represents asynchronous state of the asynchronous looping.
     */
    public static <I> AsyncResult<I> flatUntil(final TaskScheduler scheduler,
                                               final Function<? super I, AsyncResult<Boolean>> predicate,
                                               final AsyncResult<I> initialState) {
        return initialState.then(new Function<I, AsyncResult<I>>() {
            @Override
            public AsyncResult<I> apply(final I current) {
                return predicate.apply(current).then((Boolean success) -> {
                    final AsyncResult<I> next = successful(scheduler, current);
                    return success ? next.then(this) : next;
                });
            }
        });
    }

    /**
     * Executes asynchronous version of while-do loop.
     * <p>
     *     The loop iterations are sequential in time but not blocks the task scheduler thread.
     *     This version of while-do loop supports iteration with asynchronous result.
     * </p>
     * @param scheduler The scheduler used to submit loop iterations. Cannot be {@literal null}.
     * @param predicate Loop iteration. If predicate returns {@literal false} then loop will break. Cannot be {@literal null}.
     * @param initialState The initial state of the looping. This object may be used as mutable object that can be rested in predicate.
     * @param <I> Type of the looping state.
     * @return The object that represents asynchronous state of the asynchronous looping.
     */
    public static <I> AsyncResult<I> flatUntil(final TaskScheduler scheduler,
                                               final Function<? super I, AsyncResult<Boolean>> predicate,
                                               final I initialState) {
        return flatUntil(scheduler, predicate, successful(scheduler, initialState));
    }

    /**
     * Executes asynchronous version of while-do loop with separated condition check and transformation procedure.
     * <p>
     * The loop iterations are sequential in time but not blocks the task scheduler thread.
     *
     * @param scheduler The scheduler used to submit loop iterations. Cannot be {@literal null}.
     * @param condition Loop condition checker. If checker returns {@literal false} then loop will breaks. Cannot be {@literal null}.
     * @param iteration Loop transformation procedure. Cannot be {@literal null}.
     * @param initialState The initial state of the looping. This object may be used as mutable object that can be rested in predicate.
     * @param <I> Type of the looping state.
     * @return The object that represents asynchronous state of the asynchronous looping.
     */
    public static <I> AsyncResult<I> until(final TaskScheduler scheduler,
                                           final Predicate<? super I> condition,
                                           final Function<? super I, ? extends I> iteration,
                                           final AsyncResult<I> initialState) {
        return initialState.then(new Function<I, AsyncResult<I>>() {
            @Override
            public AsyncResult<I> apply(final I current) {
                return condition.test(current) ?
                        successful(scheduler, iteration.apply(current)).then(this) :
                        successful(scheduler, current);
            }
        });
    }

    /**
     * Executes asynchronous version of while-do loop with separated condition check and transformation procedure.
     * <p>
     *     The loop iterations are sequential in time but not blocks the task scheduler thread.
     * </p>
     * @param scheduler The scheduler used to submit loop iterations. Cannot be {@literal null}.
     * @param condition Loop condition checker. If checker returns {@literal false} then loop will breaks. Cannot be {@literal null}.
     * @param iteration Loop transformation procedure. Cannot be {@literal null}.
     * @param initialState The initial state of the looping. This object may be used as mutable object that can be rested in predicate.
     * @param <I> Type of the looping state.
     * @return The object that represents asynchronous state of the asynchronous looping.
     */
    public static <I> AsyncResult<I> until(final TaskScheduler scheduler,
                                           final Predicate<? super I> condition,
                                           final Function<? super I, ? extends I> iteration,
                                           final I initialState) {
        return until(scheduler, condition, iteration, successful(scheduler, initialState));
    }

    /**
     * Executes asynchronous version of while-do loop.
     * <p>
     *     The loop iterations are sequential in time but not blocks the task scheduler thread.
     * </p>
     * @param scheduler The scheduler used to submit loop iterations. Cannot be {@literal null}.
     * @param predicate Loop iteration. If predicate returns {@literal false} then loop will break. Cannot be {@literal null}.
     * @param initialState The initial state of the looping. This object may be used as mutable object that can be rested in predicate.
     * @param <I> Type of the looping state.
     * @return The object that represents asynchronous state of the asynchronous looping.
     */
    public static <I> AsyncResult<I> until(final TaskScheduler scheduler,
                                           final Predicate<? super I> predicate,
                                           final AsyncResult<I> initialState) {
        return initialState.then(new Function<I, AsyncResult<I>>() {
            @Override
            public AsyncResult<I> apply(final I current) {
                final AsyncResult<I> next = successful(scheduler, current);
                return predicate.test(current) ? next.then(this) : next;
            }
        });
    }

    /**
     * Executes asynchronous version of while-do loop.
     * <p>
     *     The loop iterations are sequential in time but not blocks the task scheduler thread.
     * </p>
     * @param scheduler The scheduler used to submit loop iterations. Cannot be {@literal null}.
     * @param predicate Loop iteration. If predicate returns {@literal false} then loop will break. Cannot be {@literal null}.
     * @param initialState The initial state of the looping. This object may be used as mutable object that can be rested in predicate.
     * @param <I> Type of the looping state.
     * @return The object that represents asynchronous state of the asynchronous looping.
     */
    public static <I> AsyncResult<I> until(final TaskScheduler scheduler,
                            final Predicate<I> predicate,
                            final I initialState){
        return until(scheduler, predicate, successful(scheduler, initialState));
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
    public static <V, P extends Enum<P> & IntSupplier> Callable<V> prioritize(final Callable<V> task, final P priority){
        return new PriorityCallable<>(task, priority);
    }
}
