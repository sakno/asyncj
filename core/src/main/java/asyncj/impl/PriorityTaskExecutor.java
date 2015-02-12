package asyncj.impl;

import asyncj.AsyncUtils;

import java.util.EnumSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * Represents preemptive task scheduler based on priority queue.
 * <p>
 *     Preemptive task scheduler may use one or more threads which shares
 *     priority queue. The priority queue contains the tasks ordered
 *     based on priority.
 * @author Roman Sakno
 * @version 1.1
 * @since 1.0
 */
public final class PriorityTaskExecutor extends AbstractPriorityTaskScheduler {
    private PriorityTaskExecutor(final int defaultPriority,
                                 final int corePoolSize,
                                 final int maximumPoolSize,
                                 final long keepAliveTime,
                                 final TimeUnit unit,
                                 final int initialQueueCapacity,
                                 final ThreadFactory tfactory) {
        super(defaultPriority,
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                initialQueueCapacity,
                tfactory);
    }

    public PriorityTaskExecutor(final int normalPriority,
                                final int corePoolSize,
                                final int maximumPoolSize,
                                final long keepAliveTime,
                                final TimeUnit unit,
                                final int initialQueueCapacity,
                                final int threadPriority,
                                final ThreadGroup group,
                                final ClassLoader contextClassLoader) {
        this(normalPriority,
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                initialQueueCapacity,
                AsyncUtils.createDaemonThreadFactory(threadPriority, group, contextClassLoader));
    }

    public PriorityTaskExecutor(final int normalPriority,
                                final int corePoolSize,
                                final int maximumPoolSize,
                                final long keepAliveTime,
                                final TimeUnit unit,
                                final int initialQueueCapacity) {
        this(normalPriority, corePoolSize, maximumPoolSize, keepAliveTime, unit, initialQueueCapacity, Thread.NORM_PRIORITY, null, Thread.currentThread().getContextClassLoader());
    }

    public <P extends Enum<P> & IntSupplier> PriorityTaskExecutor(final P normalPriority,
                                final int corePoolSize,
                                final int maximumPoolSize,
                                final long keepAliveTime,
                                final TimeUnit unit,
                                final int initialQueueCapacity,
                                final int threadPriority,
                                final ThreadGroup group,
                                final ClassLoader contextClassLoader) {
        this(normalPriority.getAsInt(),
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                initialQueueCapacity,
                threadPriority,
                group,
                contextClassLoader);
    }

    public <P extends Enum<P> & IntSupplier> PriorityTaskExecutor(final P normalPriority,
                                final int corePoolSize,
                                final int maximumPoolSize,
                                final long keepAliveTime,
                                final TimeUnit unit,
                                final int initialQueueCapacity) {
        this(normalPriority.getAsInt(),
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                initialQueueCapacity);
    }

    /**
     * Creates a new instance of the priority-based task executor that uses optimal parameters for effective
     * priority-based load-balancing between multiple threads.
     * <p>
     *     The instantiated executor has limitation on the maximum count of active threads used to execute
     *     priority-based tasks. If threads are not used they may be stopped by the scheduler. The count of threads
     *     and keep alive time inferred from priority enum semantics.
     * </p>
     * @param normalPriority The priority used to submit tasks with default ({@link #AUTO_PRIORITY}) priority.
     * @param <P> Type of the enum elements.
     * @return A new instance of the priority-based task executor.
     */
    public static <P extends Enum<P> & IntSupplier> PriorityTaskExecutor createOptimalExecutor(final P normalPriority) {
        final int s = EnumSet.allOf(normalPriority.getClass()).size();
        return new PriorityTaskExecutor(normalPriority, 0, s, 30, TimeUnit.SECONDS, s + 1);
    }

    @Override
    protected <T> Task<T> newTaskFor(final Callable<T> callable, final int priority) {
        return new Task<T>(this, priority == AUTO_PRIORITY ? defaultPriority : priority) {
            @Override
            public T call() throws Exception {
                return callable.call();
            }
        };
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
        if(r instanceof Task<?>)
            ((Task<?>)r).setExecutionThread(t);
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
        if (r instanceof Task<?>)
            ((Task<?>) r).setExecutionThread(null);
    }
}