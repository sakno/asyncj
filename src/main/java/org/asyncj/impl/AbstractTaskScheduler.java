package org.asyncj.impl;

import org.asyncj.AsyncResult;
import org.asyncj.TaskScheduler;

import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Represents abstract task scheduler.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public abstract class AbstractTaskScheduler extends ThreadPoolExecutor implements TaskScheduler {

    /**
     * Represents state of the asynchronous computation executed by the associated thread.
     * @param <O> Type of the asynchronous computation result.
     */
    protected static interface ThreadAffinityAsyncResult<O> extends AsyncResult<O>{
        /**
         * Gets thread associated with the asynchronous computation.
         * <p>
         * This method is not deterministic and may return {@literal null} if
         * thread that owns by this task is already completed, stopped or destroyed.
         *
         * @return The thread associated with the asynchronous computation.
         */
        Thread getThread();
    }

    protected AbstractTaskScheduler(final int corePoolSize,
                                    final int maximumPoolSize,
                                    final long keepAliveTime,
                                    final TimeUnit unit,
                                    final BlockingQueue<Runnable> workQueue,
                                    final ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    @Override
    public final boolean isScheduled(final AsyncResult<?> ar) {
        return (ar instanceof Task<?> && ((Task<?>) ar).isScheduledBy(this)) ||
                (ar instanceof ProxyTask<?, ?> && ((ProxyTask<?, ?>) ar).isScheduledBy(this));
    }

    /**
     * Returns a {@link org.asyncj.impl.Task} for the given callable task.
     *
     * @param callable the callable task being wrapped
     * @return a {@code RunnableFuture} which, when run, will call the
     * underlying callable and which, as a {@code Future}, will yield
     * the callable's result as its result and provide for
     * cancellation of the underlying task
     */
    @Override
    protected abstract <T> Task<T> newTaskFor(final Callable<T> callable);

    /**
     * Returns a {@link org.asyncj.impl.Task} for the given runnable and default
     * value.
     *
     * @param runnable the runnable task being wrapped
     * @param value    the default value for the returned future
     * @return a {@code RunnableFuture} which, when run, will run the
     * underlying runnable and which, as a {@code Future}, will yield
     * the given value as its result and provide for cancellation of
     * the underlying task
     */
    @Override
    protected final  <T> Task<T> newTaskFor(final Runnable runnable, final T value) {
        return newTaskFor(() -> {
            runnable.run();
            return value;
        });
    }

    /**
     * @param task
     * @throws java.util.concurrent.RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException                            {@inheritDoc}
     */
    @Override
    public final <T> AsyncResult<T> submit(final Callable<T> task) {
        return submitDirect(scheduler -> newTaskFor(task));
    }

    /**
     * @param task
     * @param result
     * @throws java.util.concurrent.RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException                            {@inheritDoc}
     */
    @Override
    public final <T> AsyncResult<T> submit(final Runnable task, final T result) {
        return submitDirect(scheduler -> newTaskFor(task, result));
    }

    /**
     * @param task
     * @throws java.util.concurrent.RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException                            {@inheritDoc}
     */
    @Override
    public final AsyncResult<Void> submit(final Runnable task) {
        return this.<Void>submit(task, null);
    }

    @Override
    public final <O, T extends AsyncResult<O> & RunnableFuture<O>> AsyncResult<O> submitDirect(final Function<TaskScheduler, T> taskFactory) {
        final T task = taskFactory.apply(this);
        execute(task);
        return task;
    }

    private boolean interrupt(final ThreadAffinityAsyncResult<?> ar){
        final Thread owner = ar.getThread();
        if(owner != null){
            owner.interrupt();
            return true;
        }
        else return false;
    }

    /**
     * Interrupts thread associated with the specified asynchronous computation.
     * <p>
     * This method based on that fact that all tasks instantiated by this scheduler implement
     * {@link org.asyncj.impl.AbstractTaskScheduler.ThreadAffinityAsyncResult} interface.
     * </p>
     *
     * @param ar The asynchronous computation to interrupt.
     * @return {@literal true}, if the specified asynchronous computation is interrupted; otherwise, {@literal false}.
     */
    @Override
    public boolean interrupt(final AsyncResult<?> ar) {
        return ar instanceof ThreadAffinityAsyncResult<?> &&
                interrupt((ThreadAffinityAsyncResult<?>) ar);
    }
}
