package asyncj.impl;

import asyncj.AsyncResult;
import asyncj.TaskScheduler;
import asyncj.ThrowableFunction;

import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;
import java.util.function.Function;

/**
 * Represents asynchronously executing task.
 * <p>
 *     Concurrent linked queue, from which this class derives, used for storing delayed children asynchronous task.
 *     Direct inheritance is used for optimization reasons of task instantiation and memory arrangement. Therefore,
 *     the clients of this class should not add or remove elements from the queue.
 * </p>
 * @param <V> Type of the asynchronous computation result.
 * @author Roman Sakno
 * @version 1.1
 * @since 1.0
 */
public abstract class Task<V> extends AbstractTask<V> implements InternalAsyncResult<V>, RunnableFuture<V>, Callable<V> {
    private Thread executionThread;
    private final Thread creationThread;
    private final int priority;

    /**
     * Initializes a new task prepared for execution in the specified scheduler.
     * @param scheduler The scheduler that owns by the newly created task. Cannot be {@literal null}.
     */
    protected Task(final TaskScheduler scheduler){
        this(scheduler, 0);
    }

    protected Task(final TaskScheduler scheduler, final int priority){
        super(scheduler);
        creationThread = Thread.currentThread();
        executionThread = null;
        this.priority = priority;
    }

    final void setExecutionThread(final Thread value){
        this.executionThread = value;
    }

    /**
     * Gets priority of this task.
     *
     * @return The priority of this task.
     */
    @Override
    public final int getAsInt() {
        return priority;
    }

    /**
     * Attempts to cancel execution of this task.  This attempt will
     * fail if the task has already completed, has already been cancelled,
     * or could not be cancelled for some other reason. If successful,
     * and this task has not started when {@code cancel} is called,
     * this task should never run.  If the task has already started,
     * then the {@code mayInterruptIfRunning} parameter determines
     * whether the thread executing this task should be interrupted in
     * an attempt to stop the task.
     * <p>
     * <p>After this method returns, subsequent calls to {@link #isDone} will
     * always return {@code true}.  Subsequent calls to {@link #isCancelled}
     * will always return {@code true} if this method returned {@code true}.
     *
     * @param mayInterruptIfRunning {@code true} if the thread executing this
     *                              task should be interrupted; otherwise, in-progress tasks are allowed
     *                              to complete
     * @return {@code false} if the task could not be cancelled,
     * typically because it has already completed normally;
     * {@code true} otherwise
     */
    @Override
    public final boolean cancel(final boolean mayInterruptIfRunning) {
        int currentState;
        do {
            switch (currentState = getState()) {
                case COMPLETED_STATE:
                    return false;
                case CANCELLED_STATE:
                    return true;
            }
        }
        while (cannotChangeState() || !compareAndSetState(currentState, CANCELLED_STATE));
        try {
            final Thread executionThread = this.executionThread;
            if(mayInterruptIfRunning && executionThread != null && executionThread != creationThread){
                executionThread.interrupt();
                return true;
            }
            else return executionThread == null;
        } finally {
            releaseShared(0);
            done(CANCELLED_STATE);
        }
    }

    /**
     * Executes this task synchronously.
     * <p>
     *     This method invokes {@link #call()} synchronously, therefore, it should be executed in the thread
     *     provided by task scheduler.
     * </p>
     */
    @Override
    public final void run() {
        if (compareAndSetState(CREATED_STATE, EXECUTED_STATE)) {
            int finalState = getState();
            try {
                finalState = complete(call(), null);
            } catch (final Exception e) {
                finalState = complete(null, e);
            } finally {
                done(finalState);
            }
        }
    }

    /**
     * Invokes the task.
     * @return The task invocation result.
     * @throws java.lang.Exception Error occurred during task execution.
     */
    @Override
    public abstract V call() throws Exception;

    /**
     * {@inheritDoc}
     * @param action
     * @param errorHandler
     * @param <O>
     * @return
     */
    @Override
    public final  <O> Promise<V, O> then(final ThrowableFunction<? super V, ? extends O> action,
                                                        final ThrowableFunction<Exception, ? extends O> errorHandler) {
        return Promise.create(this, action, errorHandler);
    }

    /**
     * {@inheritDoc}
     * @param action
     * @param <O>
     * @return
     */
    @Override
    public final  <O> Promise<V, O> then(final ThrowableFunction<? super V, ? extends O> action) {
        return Promise.create(this, action);
    }

    /**
     * {@inheritDoc}
     * @param action
     * @param errorHandler
     * @param <O>
     * @return
     */
    @Override
    public final  <O> Promise<V, O> then(final Function<? super V, AsyncResult<O>> action,
                                   final Function<Exception, AsyncResult<O>> errorHandler) {
        return Promise.create(this, action, errorHandler);
    }

    /**
     * {@inheritDoc}
     * @param action
     * @param <O>
     * @return
     */
    @Override
    public final <O> Promise<V, O> then(final Function<? super V, AsyncResult<O>> action) {
        return Promise.create(this, action);
    }
}
