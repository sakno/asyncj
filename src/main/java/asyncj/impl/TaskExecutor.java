package asyncj.impl;

import asyncj.TaskScheduler;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Represents simple task scheduler that represents bridge
 * between {@link java.util.concurrent.ExecutorService} and {@link asyncj.TaskScheduler}.
 * @author Roman Sakno
 * @version 1.1
 * @since 1.0
 */
public final class TaskExecutor extends AbstractTaskScheduler {
    private static abstract class ThreadAffinityTask<V> extends Task<V> implements ThreadAffinityAsyncResult<V>{
        private Reference<Thread> affinity = null;

        private ThreadAffinityTask(final TaskScheduler scheduler){
            super(scheduler);
        }

        private static <V> ThreadAffinityTask<V> create(final TaskScheduler scheduler, final Callable<V> task){
            return new ThreadAffinityTask<V>(scheduler) {
                @Override
                public V call() throws Exception {
                    return task.call();
                }
            };
        }

        /**
         * Creates a new instance of the child task.
         *
         * @param scheduler The scheduler that owns by the newly created task. Cannot be {@literal null}.
         * @param task      The implementation of the child task. Cannot be {@literal null}.
         * @return A new instance of the child task.
         */
        @Override
        protected final  <O> Task<O> newChildTask(final TaskScheduler scheduler, final Callable<O> task) {
            return create(scheduler, task);
        }

        /**
         * Gets thread associated with the asynchronous computation.
         * <p>
         * This method is not deterministic and may return {@literal null} if
         * thread that owns by this task is already completed, stopped or destroyed.
         *
         * @return The thread associated with the asynchronous computation.
         */
        @Override
        public final Thread getThread() {
            return affinity != null ? affinity.get() : null;
        }

        private void setThread(final Thread affinity){
            this.affinity = new WeakReference<>(affinity);
        }

        private void clearThread(){
            if(affinity != null) affinity.clear();
            affinity = null;
        }
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
                        final ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<>(), threadFactory);
    }

    /**
     * Creates a new cached task scheduler that may use no more than one thread
     * for executing tasks.
     * @param threadPriority Thread priority used by scheduler.
     * @param group Thread group used by scheduler.
     * @return A new instance of the task scheduler.
     */
    public static TaskExecutor newSingleThreadExecutor(final int threadPriority,
                                                       final ThreadGroup group) {
        return new TaskExecutor(0, 1, 30, TimeUnit.SECONDS, r -> {
            final Thread result = new Thread(group, r);
            result.setDaemon(true);
            result.setPriority(threadPriority);
            result.setContextClassLoader(Thread.currentThread().getContextClassLoader());
            return result;
        });
    }

    /**
     * Creates a new cached thread scheduler that may use no more that on thread for
     * executing tasks.
     * @return A new instance of thread executor.
     */
    public static TaskExecutor newSingleThreadExecutor(){
        return newSingleThreadExecutor(Thread.NORM_PRIORITY, null);
    }

    /**
     * Returns a {@link Task} for the given callable task.
     *
     * @param callable the callable task being wrapped
     * @return a {@code RunnableFuture} which, when run, will call the
     * underlying callable and which, as a {@code Future}, will yield
     * the callable's result as its result and provide for
     * cancellation of the underlying task
     */
    @Override
    protected <T> ThreadAffinityTask<T> newTaskFor(final Callable<T> callable) {
        return ThreadAffinityTask.create(this, Objects.requireNonNull(callable, "callable is null."));
    }

    /**
     * Method invoked prior to executing the given Runnable in the
     * given thread.  This method is invoked by thread {@code t} that
     * will execute task {@code r}, and may be used to re-initialize
     * ThreadLocals, or to perform logging.
     * <p>
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.beforeExecute} at the end of
     * this method.
     *
     * @param t the thread that will run task {@code r}
     * @param r the task that will be executed
     */
    @Override
    protected void beforeExecute(final Thread t, final Runnable r) {
        if(r instanceof ThreadAffinityTask<?>)
            ((ThreadAffinityTask<?>)r).setThread(t);
    }

    /**
     * Method invoked upon completion of execution of the given Runnable.
     * This method is invoked by the thread that executed the task. If
     * non-null, the Throwable is the uncaught {@code RuntimeException}
     * or {@code Error} that caused execution to terminate abruptly.
     *
     * @param r the runnable that has completed
     * @param t the exception that caused termination, or null if
     */
    @Override
    protected void afterExecute(final Runnable r, final Throwable t) {
        if (r instanceof ThreadAffinityTask<?>)
            ((ThreadAffinityTask<?>) r).clearThread();
    }
}
