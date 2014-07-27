package org.asyncj.impl;

import org.asyncj.AsyncResult;
import org.asyncj.PriorityTaskScheduler;

import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * Represents an abstract class for constructing prioritized task scheduler.
 * @author Roman Sakno
 * @since 1.0
 * @version 1.0
 */
public abstract class AbstractPriorityTaskScheduler extends AbstractTaskScheduler implements PriorityTaskScheduler {
    /**
     * Represents priority stub indicating that the underlying scheduler should automatically
     * select the most suitable priority for asynchronous task.
     */
    protected static final int AUTO_PRIORITY = -1;

    private static final class PriorityComparator implements Comparator<Runnable> {
        private final int defaultPriority;

        PriorityComparator(final int defaultPriority) {
            this.defaultPriority = defaultPriority;
        }

        private static int getPriority(final Object task, final int defaultPriority) {
            if (task instanceof IntSupplier)
                return ((IntSupplier) task).getAsInt();
            else if (task instanceof ProxyTask<?, ?>)
                return getPriority((((ProxyTask<?, ?>) task)).parent, defaultPriority);
            else return defaultPriority;
        }

        @Override
        public int compare(final Runnable task1, final Runnable task2) {
            return Integer.compare(getPriority(task2, defaultPriority), getPriority(task1, defaultPriority));
        }
    }

    protected final int defaultPriority;

    protected AbstractPriorityTaskScheduler(final int defaultPriority,
                                            final int corePoolSize,
                                            final int maximumPoolSize,
                                            final long keepAliveTime,
                                            final TimeUnit unit,
                                            final int initialQueueCapacity,
                                            final ThreadFactory threadFactory) {
        super(corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                new PriorityBlockingQueue<>(initialQueueCapacity, new PriorityComparator(defaultPriority)),
                threadFactory);
        this.defaultPriority = defaultPriority;
    }

    /**
     * Schedules a new task.
     *
     * @param task The computation to execute asynchronously.
     * @return An object that represents state of the asynchronous computation.
     */
    @Override
    public final <O, T extends IntSupplier & Callable<O>> AsyncResult<O> submit(final T task) {
        final PriorityTask<O> t = newTaskFor(task, task.getAsInt());
        execute(t);
        return t;
    }

    protected abstract <T> PriorityTask<T> newTaskFor(final Callable<T> callable, final int priority);

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
    protected final <T> PriorityTask<T> newTaskFor(final Callable<T> callable) {
        return newTaskFor(callable, callable instanceof IntSupplier ? ((IntSupplier) callable).getAsInt() : AUTO_PRIORITY);
    }
}
