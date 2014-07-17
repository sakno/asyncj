package org.asyncj.impl;

import org.asyncj.AsyncResult;
import org.asyncj.AsyncResultState;

import java.util.Objects;
import java.util.concurrent.*;

/**
 * Represents task
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public final class TaskExecutor extends AbstractTaskScheduler {
    private final ExecutorService executor;
    private final ConcurrentHashMap<AsyncResult<?>, Future<?>> activeTasks;

    public TaskExecutor(final ExecutorService executor){
        this.executor = Objects.requireNonNull(executor, "executor is null.");
        this.activeTasks = new ConcurrentHashMap<>(10);
    }

    /**
     * Creates a new task executor with the given initial
     * parameters and default rejected execution handler.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code threadFactory} is null
     */
    public TaskExecutor(final int corePoolSize,
                        final int maximumPoolSize,
                        final long keepAliveTime,
                        final TimeUnit unit,
                        final BlockingQueue<Runnable> workQueue,
                        final ThreadFactory threadFactory){
        this(new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory));
    }

    public static TaskExecutor newSingleThreadExecutor(final int threadPriority,
                                                       final ThreadGroup group) {
        return new TaskExecutor(0, 1, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> {
            final Thread result = new Thread(group, r);
            result.setDaemon(true);
            result.setPriority(threadPriority);
            result.setContextClassLoader(Thread.currentThread().getContextClassLoader());
            return result;
        });
    }

    public static TaskExecutor newSingleThreadExecutor(){
        return newSingleThreadExecutor(Thread.NORM_PRIORITY, null);
    }

    public boolean isShutdown(){
        return executor.isShutdown();
    }

    public boolean isTerminated(){
        return executor.isTerminated();
    }

    public void shutdown(){
        executor.shutdown();
    }

    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    @Override
    protected <V, T extends AsyncResult<V> & RunnableFuture<V>> AsyncResult<V> enqueueTask(final T task) {
        activeTasks.put(task, executor.submit(() -> {
            try {
                if (task.getAsyncState() == AsyncResultState.CREATED) task.run();
            }
            finally {
                activeTasks.remove(task);
            }
        }));
        return task;
    }

    /**
     * Interrupts thread associated with the specified asynchronous computation.
     * <p>
     * This is infrastructure method and you should not use it directly from your code.
     * </p>
     *
     * @param ar The asynchronous computation to interrupt.
     * @return {@literal true}, if the specified asynchronous computation is interrupted; otherwise, {@literal false}.
     */
    @Override
    public boolean interrupt(final AsyncResult<?> ar) {
        if (ar == null) return false;
        final Future<?> f = activeTasks.remove(ar);
        return f != null && f.cancel(true);
    }

    /**
     * Shutdowns underlying executor service.
     */
    @SuppressWarnings("FinalizeDoesntCallSuperFinalize")
    @Override
    protected void finalize() {
        executor.shutdown();
    }
}
