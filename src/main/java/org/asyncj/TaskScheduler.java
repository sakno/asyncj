package org.asyncj;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.function.Function;

/**
 * Represents task scheduler that extends Java {@link java.util.concurrent.ExecutorService} interface.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public interface TaskScheduler extends ExecutorService {

    /**
     * Interrupts thread associated with the specified asynchronous computation.
     * <p>
     *     This is infrastructure method and you should not use it directly from your code.
     * <p>
     *
     * @param ar The asynchronous computation to interrupt.
     * @return {@literal true}, if the specified asynchronous computation is interrupted; otherwise, {@literal false}.
     */
    boolean interrupt(final AsyncResult<?> ar);

    /**
     * Submits a new task for asynchronous execution.
     * @param task The task to be executed asynchronously. Cannot be {@literal null}.
     * @param <T> Type of the asynchronous computation result.
     * @return An object that represents the state of the asynchronous computation.
     */
    @Override
    <T> AsyncResult<T> submit(final Callable<T> task);

    /**
     * Submits a new task for asynchronous execution and associates the result value with it.
     * <p>
     *     You may not implement this method directly in the class because the default implementation
     *     provides bridge to {@link #submit(java.util.concurrent.Callable)} method.
     *
     * @param task The task to be executed asynchronously. Cannot be {@literal null}.
     * @param result The result returned from the asynchronous computation.  May be {@literal null}.
     * @param <T> Type of the asynchronous computation result.
     * @return An object that represents the state of the asynchronous computation.
     */
    @Override
    default <T> AsyncResult<T> submit(final Runnable task, final T result) {
        return submit(() -> {
            task.run();
            return result;
        });
    }

    /**
     * Submits a new task for asynchronous execution.
     * @param task The task to be executed asynchronously. Cannot be {@literal null}.
     * @return An object that represents the state of the asynchronous computation.
     */
    @Override
    default AsyncResult<Void> submit(final Runnable task) {
        return submit(task, null);
    }

    /**
     * Submits the promise implementation directly.
     * <p>
     *     This method has the following behaviour:
     *     <ul>
     *         <li>Factory is executed synchronously with method caller thread</li>
     *         <li>The return promise may not be of the same instance as factory result.</li>
     *     </ul>
     *
     * @param taskFactory The factory that creates a new instance of the promise to be submitted into the internal
     *                    execution queue. Cannot be {@literal null}.
     * @param <O> Type of the asynchronous computation result.
     * @param <T> Type of the promise implementation.
     * @return An object that represents the state of the asynchronous computation.
     */
    <O, T extends AsyncResult<O> & RunnableFuture<O>> AsyncResult<O> submitDirect(final Function<TaskScheduler, T> taskFactory);

    /**
     * Determines whether the specified promise is instantiated and submitted by this scheduler.
     * @param ar The asynchronous computation to check.
     * @return {@literal true}, if the specified promise is instantiated and submitted by this scheduler;
     * otherwise, {@literal false}.
     */
    boolean isScheduled(final AsyncResult<?> ar);
}
