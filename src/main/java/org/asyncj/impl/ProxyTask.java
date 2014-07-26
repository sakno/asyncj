package org.asyncj.impl;

import org.asyncj.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Represents proxy task that wraps another asynchronous result.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
abstract class ProxyTask<I, O> extends SynchronizedStateMachine implements AsyncResult<O>, RunnableFuture<O> {

    private static final int PENDING = 1, //represents initial state of the proxy task
                             CANCELLED = 2, //proxy task is cancelled
                             WRAPPED = 4, //proxy task wraps another asynchronous result
                             ANY_STATE = PENDING | CANCELLED | WRAPPED;


    private final TaskScheduler scheduler;
    private final AsyncResult<I> parent;
    private volatile AsyncResult<O> underlyingTask;

    protected ProxyTask(final TaskScheduler scheduler, final AsyncResult<I> parent){
        super(PENDING);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler is null.");
        this.parent = Objects.requireNonNull(parent, "parent task is null.");
        setState(PENDING);
    }

    /**
     * Validates state machine transition.
     * @param from The source state of the task.
     * @param to The sink state of the task.
     * @return {@literal true}, if the specified transition is valid; otherwise, {@literal false}.
     */
    @Override
    protected final boolean isValidTransition(int from, int to) {
        return from == PENDING || to == from;
    }

    /**
     * Gets unique identifier of this task.
     * @return The unique identifier of this task.
     */
    public final long getID(){
        return ((long)scheduler.hashCode() << 32) | ((long)hashCode() & 0xFFFFFFFL);
    }

    public final boolean isScheduledBy(final TaskScheduler scheduler){
        return this.scheduler == scheduler;
    }

    /**
     * Informs this task about parent task completion.
     * <p>
     *     It is allowed to use {@link #complete(org.asyncj.AsyncResult)} or {@link #failure(Exception)}
     *     methods only inside of the overridden version of this method.
     * </p>
     * @param result The result of the parent task.
     * @param err The error produced by the parent task.
     */
    protected abstract void run(final I result, final Exception err);

    private void atomicRun(final I result, final Exception err) {
        writeOnTransition(ANY_STATE, WRAPPED, (int currentState) -> {
            if (currentState == PENDING) run(result, err);
        });
    }

    protected final void complete(final AsyncResult<O> ar) {
        underlyingTask = ar;
    }

    protected final void failure(final Exception err){
        complete(scheduler.failure(err));
    }

    /**
     * Sets this Future to the result of its computation
     * unless it has been cancelled.
     */
    @Override
    public final void run() {
        parent.onCompleted(this::atomicRun);
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
        return readOnTransition(ANY_STATE, CANCELLED, currentState -> underlyingTask == null || underlyingTask.cancel(mayInterruptIfRunning));
    }

    /**
     * Gets state of this task.
     * @return The state of this task.
     */
    @Override
    public final AsyncResultState getAsyncState() {
        switch (getState()){
            case PENDING: return AsyncResultState.EXECUTED;
            case CANCELLED: return AsyncResultState.CANCELLED;
            case WRAPPED: return underlyingTask.getAsyncState();
            //never happens
            default: return null;
        }
    }

    /**
     * Returns {@code true} if this task was cancelled before it completed
     * normally.
     *
     * @return {@code true} if this task was cancelled before it completed
     */
    @Override
    public final boolean isCancelled() {
        switch (getState()){
            case CANCELLED: return true;
            case WRAPPED: return underlyingTask.isCancelled();
            default: return false;
        }
    }

    /**
     * Returns {@code true} if this task completed.
     * <p>
     * Completion may be due to normal termination, an exception, or
     * cancellation -- in all of these cases, this method will return
     * {@code true}.
     *
     * @return {@code true} if this task completed
     */
    @Override
    public final boolean isDone() {
        switch (getState()){
            case CANCELLED: return true;
            case WRAPPED: return underlyingTask.isDone();
            default: return false;
        }
    }

    /**
     * Waits if necessary for the computation to complete, and then
     * retrieves its result.
     *
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws java.util.concurrent.ExecutionException    if the computation threw an
     *                               exception
     * @throws InterruptedException  if the current thread was interrupted
     *                               while waiting
     */
    @Override
    public final O get() throws InterruptedException, ExecutionException {
        acquireSharedInterruptibly(WRAPPED);
        try {
            return underlyingTask.get();
        }
        finally {
            releaseShared(WRAPPED);
        }
    }

    private O get(final long nanos) throws InterruptedException, ExecutionException, TimeoutException {
        final Instant now = Instant.now();
        tryAcquireSharedNanos(WRAPPED, nanos);
        try {
            final Duration timeout = Duration.between(now, Instant.now());
            if (!timeout.isNegative())
                return underlyingTask.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } finally {
            releaseShared(WRAPPED);
        }
        throw new TimeoutException();
    }

    /**
     * Waits if necessary for at most the given time for the computation
     * to complete, and then retrieves its result, if available.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return the computed result
     * @throws java.util.concurrent.CancellationException if the computation was cancelled
     * @throws java.util.concurrent.ExecutionException    if the computation threw an
     *                               exception
     * @throws InterruptedException  if the current thread was interrupted
     *                               while waiting
     * @throws java.util.concurrent.TimeoutException      if the wait timed out
     */
    @SuppressWarnings("NullableProblems")
    @Override
    public final O get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return timeout < 0 || timeout == Long.MAX_VALUE ? get() : get(unit.toNanos(timeout));
    }

    private CancellationException createCancellationException(){
        return new CancellationException(String.format("Cancelled by ProxyTask %s", getID()));
    }

    private IllegalStateException createIllegalStateException(){
        return new IllegalStateException(String.format("Illegal state of the proxy task: %s", getState()));
    }

    @Override
    public final  <O1> AsyncResult<O1> then(final Function<? super O, AsyncResult<O1>> action,
                                            final Function<Exception, AsyncResult<O1>> errorHandler) {
        Objects.requireNonNull(action, "action is null.");
        return readOnTransition(ANY_STATE, (int currentState) -> {
            switch (currentState) {
                case PENDING:
                    return scheduler.submitDirect((TaskScheduler scheduler) -> new ProxyTask<O, O1>(scheduler, this) {
                        @Override
                        protected void run(final O result, final Exception err) {
                            if (err != null)
                                if (errorHandler != null) super.complete(errorHandler.apply(err));
                                else super.failure(err);
                            else super.complete(action.apply(result));
                        }
                    });
                case CANCELLED:
                    return scheduler.<O1>failure(createCancellationException());
                case WRAPPED:
                    return underlyingTask.then(action, errorHandler);
                //never happens
                default:
                    throw createIllegalStateException();
            }
        });
    }

    @Override
    public final  <O1> AsyncResult<O1> then(final Function<? super O, AsyncResult<O1>> action) {
        Objects.requireNonNull(action, "action is null.");
        return readOnTransition(ANY_STATE, (int currentState) -> {
            switch (currentState) {
                case PENDING:
                    return scheduler.submitDirect((TaskScheduler scheduler) -> new ProxyTask<O, O1>(scheduler, this) {
                        @Override
                        protected void run(final O result, final Exception err) {
                            if (err != null) super.failure(err);
                            else super.complete(action.apply(result));
                        }
                    });
                case CANCELLED:
                    return scheduler.<O1>failure(createCancellationException());
                case WRAPPED:
                    return underlyingTask.then(action);
                //never happens
                default:
                    throw createIllegalStateException();
            }
        });
    }

    @Override
    public final  <O1> AsyncResult<O1> then(final ThrowableFunction<? super O, ? extends O1> action,
                                            final ThrowableFunction<Exception, ? extends O1> errorHandler) {
        Objects.requireNonNull(action, "action is null.");
        return readOnTransition(ANY_STATE, (int currentState) -> {
            switch (currentState) {
                case PENDING:
                    final Function<Exception, AsyncResult<O1>> alternative = errorHandler != null ?
                            (Exception err) -> {
                                try {
                                    return scheduler.successful(errorHandler.apply(err));
                                } catch (final Exception e) {
                                    return scheduler.failure(e);
                                }
                            } : null;
                    return then((O value) -> {
                        try {
                            return scheduler.successful(action.apply(value));
                        } catch (final Exception err) {
                            return scheduler.failure(err);
                        }
                    }, alternative);
                case CANCELLED:
                    return scheduler.<O1>failure(createCancellationException());
                case WRAPPED:
                    return underlyingTask.then(action, errorHandler);
                default:
                    throw createIllegalStateException();
            }
        });
    }

    @Override
    public final <O1> AsyncResult<O1> then(final ThrowableFunction<? super O, ? extends O1> action) {
        return readOnTransition(ANY_STATE, (int currentState) -> {
            switch (currentState) {
                case PENDING:
                    return then((O value) -> {
                        try {
                            return scheduler.successful(action.apply(value));
                        } catch (final Exception err) {
                            return scheduler.<O1>failure(err);
                        }
                    });
                case WRAPPED:
                    return underlyingTask.then(action);
                case CANCELLED:
                    return scheduler.<O1>failure(createCancellationException());
                default:
                    throw createIllegalStateException();
            }
        });
    }

    private static <O> Function<TaskScheduler, ProxyTask<O, Void>> createCallbackTaskFactory(final AsyncResult<O> parent,
                                                                                             final AsyncCallback<? super O> callback){
        return scheduler -> new ProxyTask<O, Void>(scheduler, parent){
            @Override
            protected void run(final O result, final Exception err) {
                callback.invoke(result, err);
                if(err != null) failure(err);
                else complete(scheduler.<Void>successful(null));
            }
        };
    }

    @Override
    public final void onCompleted(final AsyncCallback<? super O> callback) {
        Objects.requireNonNull(callback, "callback is null.");
        readOnTransition(ANY_STATE, (int currentState) -> {
            switch (currentState) {
                case PENDING:
                    scheduler.submitDirect(createCallbackTaskFactory(this, callback));
                    return;
                case WRAPPED:
                    underlyingTask.onCompleted(callback);
                    return;
                case CANCELLED:
                    scheduler.<O>failure(createCancellationException()).onCompleted(callback);
                default:
                    throw createIllegalStateException();
            }
        });
    }

    @Override
    public final String toString() {
        return underlyingTask == null ? String.format("ProxyTask %s (state = %s)",
                getID(),
                getAsyncState()):
                underlyingTask.toString();
    }
}


