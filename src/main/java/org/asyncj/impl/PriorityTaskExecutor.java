package org.asyncj.impl;

import org.asyncj.AsyncResult;
import org.asyncj.AsyncResultState;
import org.asyncj.AsyncUtils;

import java.util.Objects;
import java.util.concurrent.*;

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
public final class PriorityTaskExecutor<P extends Comparable<P>> extends AbstractPriorityTaskScheduler<P> {

    private static abstract class PriorityRunnable<P extends Comparable<P>> implements Runnable, Comparable<PriorityRunnable<P>>, PriorityItem<P> {
        private final P priority;

        protected PriorityRunnable(final P priority) {
            this.priority = Objects.requireNonNull(priority, "priority is null.");
        }

        /**
         * Gets priority associated with this item.
         *
         * @return The priority associated with this item.
         */
        @Override
        public final P getPriority() {
            return priority;
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public final int compareTo(final PriorityRunnable<P> task) {
            return priority.compareTo(Objects.requireNonNull(task, "task is null.").priority);
        }
    }

    private final ThreadPoolExecutor executor;
    private final ConcurrentHashMap<AsyncResult<?>, Future<?>> activeTasks;

    private PriorityTaskExecutor(final P normalPriority,
                                 final int corePoolSize,
                                 final int maximumPoolSize,
                                 final long keepAliveTime,
                                 final TimeUnit unit,
                                 final int initialQueueCapacity,
                                 final ThreadFactory tfactory) {
        super(normalPriority);
        executor = new ThreadPoolExecutor(corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                new PriorityBlockingQueue<>(initialQueueCapacity),
                tfactory);
        activeTasks = new ConcurrentHashMap<>(initialQueueCapacity);
    }

    public PriorityTaskExecutor(final P normalPriority,
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

    public PriorityTaskExecutor(final P normalPriority,
                                final int corePoolSize,
                                final int maximumPoolSize,
                                final long keepAliveTime,
                                final TimeUnit unit,
                                final int initialQueueCapacity) {
        this(normalPriority, corePoolSize, maximumPoolSize, keepAliveTime, unit, initialQueueCapacity, Thread.NORM_PRIORITY, null, Thread.currentThread().getContextClassLoader());
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
    protected <V, T extends AsyncResult<V> & RunnableFuture<V>> AsyncResult<V> enqueueTask(final T task, final P priority) {
        executor.submit(new PriorityRunnable<P>(priority) {
            @Override
            public void run() {
                try {
                    if (task.getAsyncState() == AsyncResultState.CREATED)
                        task.run();
                } finally {
                    activeTasks.remove(task);
                }
            }
        });
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
     * Releases underlying executor service.
     */
    @SuppressWarnings("FinalizeDoesntCallSuperFinalize")
    @Override
    protected void finalize() {
        executor.shutdown();
    }
}
