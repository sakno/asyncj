package org.asyncj.impl;

import org.asyncj.AsyncResult;
import org.asyncj.AsyncResultState;
import org.asyncj.AsyncUtils;

import java.util.Comparator;
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

    private static abstract class PriorityRunnable implements Runnable, Comparable<IntSupplier>, IntSupplier {
        private final int priority;

        protected PriorityRunnable(final int priority) {
            this.priority = Objects.requireNonNull(priority, "priority is null.");
        }

        @Override
        public final int getAsInt() {
            return priority;
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public final int compareTo(final IntSupplier task) {
            //reverse order because the taks with highest priority should be executed as soon as possible
            return Integer.compare(Objects.requireNonNull(task, "task is null.").getAsInt(), priority);
        }
    }

    private final ThreadPoolExecutor executor;
    private final ConcurrentHashMap<AsyncResult<?>, Future<?>> activeTasks;
    private final int normalPriority;

    private PriorityTaskExecutor(final int normalPriority,
                                 final int corePoolSize,
                                 final int maximumPoolSize,
                                 final long keepAliveTime,
                                 final TimeUnit unit,
                                 final int initialQueueCapacity,
                                 final ThreadFactory tfactory) {
        executor = new ThreadPoolExecutor(corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                new PriorityBlockingQueue<>(initialQueueCapacity),
                tfactory);
        activeTasks = new ConcurrentHashMap<>(initialQueueCapacity);
        this.normalPriority = normalPriority;
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
     * @param priorityEnum The type of the enum that represents all possible priorities.
     * @param normalPriority The priority used to enqueue tasks with default ({@link #AUTO_PRIORITY}) priority.
     * @param <P> Type of the enum elements.
     * @return A new instance of the priority-based task executor.
     */
    public static <P extends Enum<P> & IntSupplier> PriorityTaskExecutor createOptimalExecutor(final Class<P> priorityEnum,
                                                                                                        final P normalPriority){
        final int s = EnumSet.allOf(priorityEnum).size();
        return new PriorityTaskExecutor(normalPriority, 0, s, 30, TimeUnit.SECONDS, s + 1);
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
    protected <V, T extends AsyncResult<V> & RunnableFuture<V>> AsyncResult<V> enqueueTask(final T task, final int priority) {
        if(priority < 0) return enqueueTask(task, normalPriority);
        else executor.submit(new PriorityRunnable(priority) {
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
