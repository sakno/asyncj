package org.asyncj.impl;

import org.asyncj.AsyncResult;
import org.asyncj.AsyncUtils;

import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.IntSupplier;

/**
 * Represents preemptive task scheduler based on priority queue.
 * <p>
 *     Preemptive task scheduler may use one or more threads which shares
 *     priority queue. The priority queue contains the tasks ordered
 *     based on priority.
 * </p>
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public final class PriorityTaskExecutor extends AbstractPriorityTaskScheduler {

    private static final class PriorityFutureTask<V> extends FutureTask<V> implements Comparable<PriorityFutureTask> {
        private final int priority;

        public PriorityFutureTask(final Runnable task, final V value, final int priority) {
            super(task, value);
            this.priority = priority;
        }

        @Override
        public int compareTo(final PriorityFutureTask o) {
            return Integer.compare(o.priority, priority);
        }
    }

    private static final class PriorityThreadPoolExecutor extends ThreadPoolExecutor{
        public final int normalPriority;

        public PriorityThreadPoolExecutor(final int normalPriority,
                                          final int corePoolSize,
                                          final int maximumPoolSize,
                                          final long keepAliveTime,
                                          final TimeUnit unit,
                                          final int initialQueueCapacity,
                                          final ThreadFactory tfactory){
            super(corePoolSize,
                    maximumPoolSize,
                    keepAliveTime,
                    unit,
                    new PriorityBlockingQueue<>(initialQueueCapacity),
                    tfactory);
            this.normalPriority = normalPriority;
        }

        private <T> PriorityFutureTask<T> newTaskFor(final Runnable runnable, final T value, final int priority) {
            return new PriorityFutureTask<>(runnable, value, priority);
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(final Runnable runnable, final T value) {
            return newTaskFor(runnable, value, normalPriority);
        }

        public Future submit(final Runnable task, final int priority) {
            final RunnableFuture<Void> ftask = newTaskFor(task, null, priority);
            execute(ftask);
            return ftask;
        }
    }

    private final PriorityThreadPoolExecutor executor;
    private final ConcurrentHashMap<AsyncResult<?>, Future> activeTasks;

    private PriorityTaskExecutor(final int normalPriority,
                                 final int corePoolSize,
                                 final int maximumPoolSize,
                                 final long keepAliveTime,
                                 final TimeUnit unit,
                                 final int initialQueueCapacity,
                                 final ThreadFactory tfactory) {
        executor = new PriorityThreadPoolExecutor(normalPriority,
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                initialQueueCapacity,
                tfactory);
        activeTasks = new ConcurrentHashMap<>(initialQueueCapacity);
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
     * @param normalPriority The priority used to enqueue tasks with default ({@link #AUTO_PRIORITY}) priority.
     * @param <P> Type of the enum elements.
     * @return A new instance of the priority-based task executor.
     */
    public static <P extends Enum<P> & IntSupplier> PriorityTaskExecutor createOptimalExecutor(final P normalPriority) {
        final int s = EnumSet.allOf(normalPriority.getClass()).size();
        return new PriorityTaskExecutor(normalPriority, 0, s, 30, TimeUnit.SECONDS, s + 1);
    }

    /**
     * Gets count of currently running tasks.
     * @return Count of currently running tasks.
     */
    public int getActiveTasks(){
        return activeTasks.size();
    }

    /**
     * Returns {@literal true} if this executor has been shut down.
     *
     * @return {@literal true} if this executor has been shut down
     */
    public boolean isShutdown(){
        return executor.isShutdown();
    }

    /**
     * Returns {@literal true} if all tasks have completed following shut down.
     * Note that {@code isTerminated} is never {@literal true} unless
     * either {@code shutdown} or {@code shutdownNow} was called first.
     *
     * @return {@literal true} if all tasks have completed following shut down
     */
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
    protected <V, T extends AsyncResult<V> & RunnableFuture<V>> AsyncResult<V> enqueueTask(final T task, final int priority) {
        if (priority < 0) return enqueueTask(task, executor.normalPriority);
        else activeTasks.put(task, executor.submit(() -> {
            try {
                task.run();
            } finally {
                activeTasks.remove(task);
            }
        }, priority));
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
        final Future<?> f = activeTasks.remove(ar);
        return f != null && f.cancel(true);
    }

    /**
     * Returns a string representation of this executor.
     * @return A string representation of this executor.
     */
    @Override
    public String toString() {
        return executor.toString();
    }

    /**
     * Releases underlying executor service.
     */
    @SuppressWarnings("FinalizeDoesntCallSuperFinalize")
    @Override
    protected void finalize() {
        executor.shutdown();
    }
}
