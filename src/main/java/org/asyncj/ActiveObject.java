package org.asyncj;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Represents active object which decouples method execution from method invocation and resides in their own thread of control.
 * <p>
 *     The active object may use simple task scheduler or priority-based task scheduler. The protected methods simplifies
 *     scheduling of asynchronous tasks.
 * <p>
 *     It is recommended to use the following programming style for active objects:
 *     <ul>
 *         <li>Synchronous version of the method must be private or protected.</li>
 *         <li>For each synchronous method you should write two asynchronous methods: the first method
 *         provides {@link org.asyncj.AsyncResult} as a result, the second returns {@literal void} and
 *         accepts {@link org.asyncj.AsyncCallback} as the last parameter in the signature.</li>
 *         <li>Implementation of these methods consist of wrapping an invocation of synchronous method into
 *         the asynchronous task using protected methods from {@code ActiveObject}.</li>
 *     </ul>
 *     The following example demonstrates active object programming style described above:
 *     <pre><code>
 *      final class ArrayOperations extends ActiveObject {
 *          public ArrayOperations() {
 *              super(TaskExecutor.newSingleThreadExecutor());
 *          }
 *
 *
 *              private &lt;T&gt; T[] reverseArraySync(final T[] array){
 *                  final T[] result = (T[])Array.newInstance(array.getClass().getComponentType(), array.length);
 *                  for(int i = 0; i &lt; array.length; i++)
 *                      result[i] = array[array.length - i - 1];
 *                  return result;
 *              }
 *
 *              public &lt;T&gt; AsyncResult&lt;T[]&gt; reverseArray(final T[] array){
 *                  return super.enqueue(()-&gt; reverseArraySync(array));
 *              }
 *
 *              public &lt;T&gt; void reverseArray(final T[] array, final AsyncCallback&lt;T[]&gt; callback){
 *                  reverseArray(array).onCompleted(callback);
 *              }
 *      }
 *     </code></pre>
 * <p>
 *  If you want to enable priority-based task scheduling then you should do the following things:
 *  <ul>
 *      <li>Instantiate task scheduler which implements {@link org.asyncj.PriorityTaskScheduler} interface. The default
 *      implementation provided by {@link org.asyncj.impl.PriorityTaskExecutor}</li>
 *      <li>Use priority-based enqueue protected methods from {@code ActiveObject} (such as {@link #enqueue(java.util.concurrent.Callable, Enum)}
 *      or {@link #mapReduce(java.util.Iterator, java.util.function.BiFunction, Object, Enum)}) instead of enqueue methods without
 *      {@code priority} parameter (such as {@link #enqueue(java.util.concurrent.Callable)}).</li>
 *      <li>Declare an {@code enum} that represents all possible priorities and implements {@link java.util.function.IntSupplier} interface.</li>
 *  </ul>
 *  The following example demonstrates how to write active object with priority support:
 *  <pre><code>
 *      public class PriorityArrayOperations extends ActiveObject {
 *
 *          private static enum Priority implements IntSupplier{
 *              LOW(0),
 *              NORMAL(1),
 *              HIGH(2);
 *
 *              private final int p;

 *          private Priority(final int priorityNumber){
 *              p = priorityNumber;
 *          }
 *
 *
 *          public int getAsInt() {
 *              return p;
 *          }
 *      }
 *
 *      public PriorityArrayOperations() {
 *          super(PriorityTaskExecutor.createOptimalExecutor(Priority.class, Priority.NORMAL));
 *      }
 *
 *      private &lt;T&gt; T[] reverseArraySync(final T[] array){
 *          final T[] result = (T[]) Array.newInstance(array.getClass().getComponentType(), array.length);
 *          for(int i = 0; i &lt; array.length; i++)
 *              result[i] = array[array.length - i - 1];
 *          return result;
 *      }
 *
 *      public &lt;T&gt; AsyncResult&lt;T[]&gt; reverseArray(final T[] array){
 *          return enqueue(()-&gt; reverseArraySync(array), Priority.HIGH);
 *      }
 *
 *      public &lt;T&gt; void reverseArray(final T[] array, final AsyncCallback&lt;T[]&gt; callback){
 *           reverseArray(array).onCompleted(callback);
 *      }
 *  }
 *  </code></pre>
 * @author Roman Sakno
 * @since 1.0
 * @version 1.0
 * @see org.asyncj.impl.TaskExecutor
 * @see org.asyncj.impl.PriorityTaskExecutor
 */
public abstract class ActiveObject {
    private final TaskScheduler scheduler;

    /**
     * Initializes a new active object with the specified scheduler used for executing asynchronous computation.
     * @param scheduler Task scheduler to be used for executing asynchronous computation execute by this active object. Cannot be {@literal null}.
     * @throws java.lang.NullPointerException {@code scheduler} is {@literal null}.
     */
    protected ActiveObject(final TaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler is null.");
    }

    /**
     * Wraps scalar object into its asynchronous representation.
     * @param value The value to wrap.
     * @param <O> The type of the value to wrap.
     * @return Already completed asynchronous result that represents passed object.
     */
    protected final <O> AsyncResult<O> successful(final O value) {
        return scheduler.successful(value);
    }

    /**
     * Wraps exception into its asynchronous representation.
     * @param err An instance of the exception. Cannot be {@literal null}.
     * @param <O> Type of the asynchronous computation result.
     * @return Already completed asynchronous result that represents passed exception.
     */
    protected final <O> AsyncResult<O> failure(final Exception err) {
        return scheduler.failure(err);
    }

    /**
     * Enqueue a new task for asynchronous execution.
     * <p>
     *     Call {@link #enqueue(java.util.concurrent.Callable, Enum)} instead of this method
     *     if this active object use priority-based task scheduler.
     * </p>
     * @param task The task to schedule. Cannot be {@literal null}.
     * @param <O> Type of the computation result.
     * @return An object that represents state of the asynchronous computation.
     * @see #enqueue(java.util.concurrent.Callable, Enum)
     */
    protected final <O> AsyncResult<O> enqueue(final Callable<? extends O> task) {
        return scheduler.enqueue(task);
    }

    /**
     * Enqueue a new task for asynchronous execution with given priority.
     * <p>
     *     Call {@link #enqueue(java.util.concurrent.Callable)} instead of this method
     *     if this active object don't use priority-based task scheduler.
     * </p>
     * @param task The task to schedule. Cannot be {@literal null}.
     * @param priority The priority of the task. Cannot be {@literal null}.
     * @param <O> Type of the computation result.
     * @param <P> Type of the enum that represents all available priorities.
     * @return An object that represents state of the asynchronous computation.
     * @see #enqueue(java.util.concurrent.Callable)
     */
    protected final <O, P extends Enum<P> & IntSupplier> AsyncResult<O> enqueue(final Callable<O> task, final P priority) {
        return enqueue(AsyncUtils.prioritize(task, priority));
    }

    /**
     * Enqueue a new callback for asynchronous execution.
     * <p>
     *     Call {@link #enqueue(AsyncCallback, Object, Enum)} instead of this method
     *     if this active object use priority-based task scheduler.
     * </p>
     * @param callback The callback to schedule. Cannot be {@literal null}.
     * @param value The value to be passed into the callback at execution time.
     * @param <I> Type of the value to be passed into the callback at execution time.
     * @see #enqueue(AsyncCallback, Object, Enum)
     */
    protected final <I> void enqueue(final AsyncCallback<? super I> callback, final I value) {
        Objects.requireNonNull(callback, "callback is null.");
        enqueue(() -> {
            callback.invoke(value, null);
            return null;
        });
    }

    /**
     * Enqueue a new callback for asynchronous execution with given priority.
     * <p>
     *     Call {@link #enqueue(AsyncCallback, Object)} instead of this method
     *     if this active object don't use priority-based task scheduler.
     * </p>
     * @param callback The callback to schedule. Cannot be {@literal null}.
     * @param value The value to be passed into the callback at execution time.
     * @param priority The priority of the task. Cannot be {@literal null}.
     * @param <I> Type of the value to be passed into the callback at execution time.
     * @param <P> Type of the enum that represents all available priorities.
     * @see #enqueue(AsyncCallback, Object)
     */
    protected final <I, P extends Enum<P> & IntSupplier> void enqueue(final AsyncCallback<? super I> callback, final I value, final P priority) {
        Objects.requireNonNull(callback, "callback is null.");
        enqueue(() -> {
            callback.invoke(value, null);
            return null;
        }, priority);
    }

    /**
     * Enqueue a new callback for asynchronous computation.
     * <p>
     *     Call {@link #enqueue(AsyncCallback, java.lang.Exception, Enum)} instead of this method
     *     if this active object use priority-based task scheduler.
     * </p>
     * @param callback The callback to schedule. Cannot be {@literal null}.
     * @param err An instance of the exception to be passed into callback at execution time.
     * @see #enqueue(AsyncCallback, Exception, Enum)
     */
    protected final void enqueue(final AsyncCallback<?> callback, final Exception err) {
        Objects.requireNonNull(callback, "callback is null.");
        enqueue(() -> {
            callback.invoke(null, err);
            return null;
        });
    }

    /**
     * Enqueue a new callback for asynchronous computation with given priority.
     * <p>
     *     Call {@link #enqueue(AsyncCallback, java.lang.Exception)} instead of this method
     *     if this active object don't use priority-based task scheduler.
     * </p>
     * @param callback The callback to schedule. Cannot be {@literal null}.
     * @param err An instance of the exception to be passed into callback at execution time.
     * @param priority The priority of the task. Cannot be {@literal null}.
     * @param <P> Type of the enum that represents all available priorities.
     * @see #enqueue(AsyncCallback, java.lang.Exception)
     */
    protected final <P extends Enum<P> & IntSupplier> void enqueue(final AsyncCallback<?> callback, final Exception err, final P priority){
        Objects.requireNonNull(callback, "callback is null.");
        enqueue(()->{
            callback.invoke(null, err);
            return null;
        }, priority);
    }

    /**
     * Iterates over collection and performs filtering and summary operation.
     * @param collection The collection to process. Cannot be {@literal null}.
     * @param mr An object that implements map/reduce logic. Cannot be {@literal null}.
     * @param initialValue The initial value passed to the map-reduce algorithm at first iteration.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduced result.
     * @return An object that represents asynchronous result of the map-reduce algorithm.
     */
    protected final <I, O> AsyncResult<O> mapReduce(final Iterator<? extends I> collection,
                                                    final BiFunction<? super I, ? super O, ? extends O> mr,
                                                    final AsyncResult<? extends O> initialValue) {
        return AsyncUtils.mapReduce(scheduler, collection, mr, initialValue);
    }

    /**
     * Iterates over collection and performs filtering and summary operation.
     * <p>
     *     Call {@link #mapReduce(java.util.Iterator, java.util.function.BiFunction, Object, Enum)} instead of this method
     *     if this active object use priority-based task scheduler.
     * </p>
     * @param collection The collection to process. Cannot be {@literal null}.
     * @param mr An object that implements map/reduce logic. Cannot be {@literal null}.
     * @param initialValue The initial value passed to the map-reduce algorithm at first iteration.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduced result.
     * @return An object that represents asynchronous result of the map-reduce algorithm.
     */
    protected final <I, O> AsyncResult<O> mapReduce(final Iterator<? extends I> collection,
                                                    final BiFunction<? super I, ? super O, ? extends O> mr,
                                                    final O initialValue) {
        return AsyncUtils.mapReduce(scheduler, collection, mr, initialValue);
    }

    /**
     * Iterates over collection and performs filtering and summary operation.
     * <p>
     *     Call {@link #mapReduce(java.util.Iterator, java.util.function.BiFunction, Object)} instead of this method
     *     if this active object don't use priority-based task scheduler.
     * </p>
     * @param collection The collection to process. Cannot be {@literal null}.
     * @param mr An object that implements map/reduce logic. Cannot be {@literal null}.
     * @param initialValue The initial value passed to the map-reduce algorithm at first iteration.
     * @param priority The priority of the map-reduce computation.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduced result.
     * @param <P> Type of the enum that represents all available priorities.
     * @return An object that represents asynchronous result of the map-reduce algorithm.
     */
    protected final <I, O, P extends Enum<P> & IntSupplier> AsyncResult<O> mapReduce(final Iterator<? extends I> collection,
                                                    final BiFunction<? super I, ? super O, ? extends O> mr,
                                                    final O initialValue,
                                                    final P priority) {
        return AsyncUtils.mapReduce(scheduler, collection, mr,
                enqueue(() -> initialValue, priority));
    }

    /**
     * Iterates over collection and performs filtering and summary operation.
     * @param collection The collection to process. Cannot be {@literal null}.
     * @param mr An object that implements map/reduce logic. Cannot be {@literal null}.
     * @param initialValue The initial value passed to the map-reduce algorithm at first iteration. Cannot be {@literal null}.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduced result.
     * @return An object that represents asynchronous result of the map-reduce algorithm.
     */
    protected final <I, O> AsyncResult<O> mapReduceAsync(final Iterator<? extends I> collection,
                                                         final BiFunction<? super I, ? super O, AsyncResult<O>> mr,
                                                         final AsyncResult<O> initialValue) {
        return AsyncUtils.mapReduceAsync(scheduler, collection, mr, initialValue);
    }

    /**
     * Iterates over collection and performs filtering and summary operation.
     * <p>
     *     Call {@link #mapReduceAsync(java.util.Iterator, java.util.function.BiFunction, Object, Enum)} instead of this method
     *     if this active object use priority-based task scheduler.
     * </p>
     * @param collection The collection to process. Cannot be {@literal null}.
     * @param mr An object that implements map/reduce logic. Cannot be {@literal null}.
     * @param initialValue The initial value passed to the map-reduce algorithm at first iteration.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduced result.
     * @return An object that represents asynchronous result of the map-reduce algorithm.
     */
    protected final <I, O> AsyncResult<O> mapReduceAsync(final Iterator<? extends I> collection,
                                                         final BiFunction<? super I, ? super O, AsyncResult<O>> mr,
                                                         final O initialValue) {
        return AsyncUtils.mapReduceAsync(scheduler, collection, mr, initialValue);
    }

    /**
     * Iterates over collection and performs filtering and summary operation.
     * <p>
     *     Call {@link #mapReduceAsync(java.util.Iterator, java.util.function.BiFunction, Object)} instead of this method
     *     if this active object don't use priority-based task scheduler.
     * </p>
     * @param collection The collection to process. Cannot be {@literal null}.
     * @param mr An object that implements map/reduce logic. Cannot be {@literal null}.
     * @param initialValue The initial value passed to the map-reduce algorithm at first iteration.
     * @param priority The priority of the map-reduce operation.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduced result.
     * @param <P> Type of the enum that represents all available priorities.
     * @return An object that represents asynchronous result of the map-reduce algorithm.
     */
    protected final <I, O, P extends Enum<P> & IntSupplier> AsyncResult<O> mapReduceAsync(final Iterator<? extends I> collection,
                                                         final BiFunction<? super I, ? super O, AsyncResult<O>> mr,
                                                         final O initialValue,
                                                         final P priority) {
        return AsyncUtils.mapReduceAsync(scheduler, collection, mr,
                enqueue(() -> initialValue, priority));
    }

    /**
     * Reduces the specified collection.
     * @param values An iterator over collection to reduce. Cannot be {@literal null}.
     * @param accumulator Associative, non-interfering and stateless function for combining two values. Cannot be {@literal null}.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduction result.
     * @return The result of the reduction.
     */
    protected final <I, O> AsyncResult<O> reduce(final Iterator<AsyncResult<I>> values,
                                                 final ThrowableFunction<? super Collection<I>, O> accumulator) {
        return AsyncUtils.reduce(scheduler, values, accumulator);
    }

    /**
     * Reduces the specified collection.
     * @param values An iterator over collection to reduce. Cannot be {@literal null}.
     * @param accumulator Associative, non-interfering and stateless function for combining two values. Cannot be {@literal null}.
     * @param priority Priority of the reduction task.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduction result.
     * @param <P> Type of the enum that represents all available priorities.
     * @return The result of the reduction.
     */
    protected final <I, O, P extends Enum<P> & IntSupplier> AsyncResult<O> reduce(final Iterator<AsyncResult<I>> values,
                                                 final ThrowableFunction<? super Collection<I>, O> accumulator,
                                                 final P priority){
        return AsyncUtils.reduce(scheduler,
                values,
                accumulator,
                AsyncUtils.prioritize(AsyncUtils.<I>getInitialVectorProvider(values), priority));
    }

    /**
     * Reduces the specified collection.
     * @param values An iterator over collection to reduce. Cannot be {@literal null}.
     * @param accumulator Associative, non-interfering and stateless function for combining two values. Cannot be {@literal null}.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduction result.
     * @return The result of the reduction.
     */
    protected final <I, O> AsyncResult<O> reduceAsync(final Iterator<AsyncResult<I>> values,
                                                      final Function<? super Collection<I>, AsyncResult<O>> accumulator) {
        return AsyncUtils.reduceAsync(scheduler, values, accumulator);
    }

    /**
     * Reduces the specified collection.
     * @param values An iterator over collection to reduce. Cannot be {@literal null}.
     * @param accumulator Associative, non-interfering and stateless function for combining two values. Cannot be {@literal null}.
     * @param priority Priority of the reduction task.
     * @param <I> Type of the elements in the input collection.
     * @param <O> Type of the reduction result.
     * @param <P> Type of the enum that represents all available priorities.
     * @return The result of the reduction.
     */
    protected final <I, O, P extends Enum<P> & IntSupplier> AsyncResult<O> reduceAsync(final Iterator<AsyncResult<I>> values,
                                                      final Function<? super Collection<I>, AsyncResult<O>> accumulator,
                                                      final P priority) {
        return AsyncUtils.reduceAsync(scheduler, values, accumulator,
                AsyncUtils.prioritize(AsyncUtils.<I>getInitialVectorProvider(values), priority));
    }
}
