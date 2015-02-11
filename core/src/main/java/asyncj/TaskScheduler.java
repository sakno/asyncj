package asyncj;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RunnableFuture;
import java.util.function.Function;

/**
 * Represents task scheduler that extends Java {@link java.util.concurrent.ExecutorService} interface.
 * @author Roman Sakno
 * @version 1.1
 * @since 1.0
 */
public interface TaskScheduler extends ExecutorService {
    /**
     * Returns the number of processors available to the Java virtual machine.
     *
     * @return  the maximum number of processors available to the virtual
     *          machine
     **/
    public static int availableProcessors(){
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * Submits a new task for asynchronous execution.
     * @param task The task to be executed asynchronously. Cannot be {@literal null}.
     * @param <T> Type of the asynchronous computation result.
     * @return An object that represents the state of the asynchronous computation.
     */
    @SuppressWarnings("NullableProblems")
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
    @SuppressWarnings("NullableProblems")
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
    @SuppressWarnings("NullableProblems")
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

    /**
     * Gets priority of this scheduler.
     * <p>
     *     The scheduler priority used for resolving default scheduler for the application
     *     using {@link java.util.ServiceLoader}.
     * </p>
     * @return The priority of this scheduler.
     * @see AsyncUtils#getGlobalScheduler()
     */
    default int getPriority(){
        return 0;
    }
}
