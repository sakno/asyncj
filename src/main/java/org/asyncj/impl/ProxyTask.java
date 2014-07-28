package org.asyncj.impl;

import org.asyncj.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.function.*;

/**
 * Represents proxy task that wraps another asynchronous result.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
abstract class ProxyTask<I, O> extends AbstractQueuedSynchronizer implements AsyncResult<O>, RunnableFuture<O> {

    private static final int PENDING_STATE = 1, //represents initial state of the proxy task
                             CANCELLED_STATE = 2, //proxy task is cancelled
                             WRAPPED_STATE = 4, //proxy task wraps another asynchronous result
                             FINAL_STATE = CANCELLED_STATE | WRAPPED_STATE;


    private final TaskScheduler scheduler;
    final AsyncResult<I> parent;
    private AsyncResult<O> underlyingTask;
    private volatile boolean preventTransition;

    protected ProxyTask(final TaskScheduler scheduler, final AsyncResult<I> parent){
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler is null.");
        this.parent = Objects.requireNonNull(parent, "parent task is null.");
        setState(PENDING_STATE);
        preventTransition = false;
        underlyingTask = null;
    }

    @Override
    protected final int tryAcquireShared(final int ignore) {
        return (getState() & FINAL_STATE) != 0 ? 1 : -1;
    }

    @Override
    protected final boolean tryReleaseShared(final int ignore) {
        return true;
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

    protected final void complete(final O value){
        complete(AsyncUtils.successful(scheduler, value));
    }

    protected final void complete(final AsyncResult<O> ar) {
        int currentState;
        do {
            switch (currentState = getState()) {
                case CANCELLED_STATE:
                    releaseShared(0); //handle potential racing with a cancel request
                case WRAPPED_STATE:
                    return;
            }
        } while (preventTransition || !compareAndSetState(currentState, WRAPPED_STATE));
        underlyingTask = ar;
        releaseShared(0);
    }

    protected final void failure(final Exception err){
        complete(AsyncUtils.failure(scheduler, err));
    }

    /**
     * Sets this Future to the result of its computation
     * unless it has been cancelled.
     */
    @Override
    public final void run() {
        parent.onCompleted(this::run);
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
                case WRAPPED_STATE:
                    return false;
                case CANCELLED_STATE:
                    return true;
            }
        }
        while (preventTransition || !compareAndSetState(currentState, CANCELLED_STATE));
        releaseShared(0);
        final AsyncResult<O> ut = underlyingTask;
        return ut != null && ut.cancel(mayInterruptIfRunning);
    }

    /**
     * Gets state of this task.
     * @return The state of this task.
     */
    @Override
    public final AsyncResultState getAsyncState() {
        switch (getState()){
            case PENDING_STATE: return AsyncResultState.EXECUTED;
            case CANCELLED_STATE: return AsyncResultState.CANCELLED;
            case WRAPPED_STATE: return underlyingTask.getAsyncState();
            //never happens
            default: throw createIllegalStateException();
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
            case CANCELLED_STATE: return true;
            case WRAPPED_STATE: return underlyingTask.isCancelled();
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
            case CANCELLED_STATE: return true;
            case WRAPPED_STATE: return underlyingTask.isDone();
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
        acquireSharedInterruptibly(0);
        switch (getState()) {
            case CANCELLED_STATE:
                throw new CancellationException();
            case WRAPPED_STATE:
                return underlyingTask.get();
            default:
                throw createIllegalStateException();
        }
    }

    private O get(final long nanos) throws InterruptedException, ExecutionException, TimeoutException {
        final Instant now = Instant.now();
        if (!tryAcquireSharedNanos(0, nanos))
            throw new TimeoutException();
        switch (getState()) {
            case CANCELLED_STATE:
                throw new CancellationException();
            case WRAPPED_STATE:
                final Duration timeout = Duration.ofNanos(nanos).minus(Duration.between(now, Instant.now()));
                if (!timeout.isNegative())
                    return underlyingTask.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            default:
                throw new TimeoutException();
        }
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

    private <T> T nonTransitive(final IntFunction<T> action) {
        preventTransition = true;
        try {
            return action.apply(getState());
        } finally {
            preventTransition = false;
        }
    }

    private void nonTransitive(final IntConsumer action){
        preventTransition = true;
        try{
            action.accept(getState());
        }
        finally {
            preventTransition = false;
        }
    }

    @Override
    public final  <O1> AsyncResult<O1> then(final Function<? super O, AsyncResult<O1>> action,
                                            final Function<Exception, AsyncResult<O1>> errorHandler) {
        Objects.requireNonNull(action, "action is null.");
        return nonTransitive((int currentState) -> {
            switch (currentState) {
                case PENDING_STATE:
                    return scheduler.submitDirect((TaskScheduler scheduler) -> new ProxyTask<O, O1>(scheduler, this) {
                        @Override
                        protected void run(final O result, final Exception err) {
                            if (err != null)
                                if (errorHandler != null) super.complete(errorHandler.apply(err));
                                else super.failure(err);
                            else super.complete(action.apply(result));
                        }
                    });
                case CANCELLED_STATE:
                    return AsyncUtils.<O1>failure(scheduler, createCancellationException());
                case WRAPPED_STATE:
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
        return nonTransitive((int currentState) -> {
            switch (currentState) {
                case PENDING_STATE:
                    return scheduler.submitDirect((TaskScheduler scheduler) -> new ProxyTask<O, O1>(scheduler, this) {
                        @Override
                        protected void run(final O result, final Exception err) {
                            if (err != null) super.failure(err);
                            else super.complete(action.apply(result));
                        }
                    });
                case CANCELLED_STATE:
                    return AsyncUtils.<O1>failure(scheduler, createCancellationException());
                case WRAPPED_STATE:
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
        return nonTransitive((int currentState) -> {
            switch (currentState) {
                case PENDING_STATE:
                    final Function<Exception, AsyncResult<O1>> alternative = errorHandler != null ?
                            (Exception err) -> {
                                try {
                                    return AsyncUtils.successful(scheduler, errorHandler.apply(err));
                                }
                                catch (final Exception e) {
                                    return AsyncUtils.<O1>failure(scheduler, e);
                                }
                            } : null;
                    return then((O value) -> {
                        try {
                            return AsyncUtils.successful(scheduler, action.apply(value));
                        } catch (final Exception err) {
                            return AsyncUtils.<O1>failure(scheduler, err);
                        }
                    }, alternative);
                case CANCELLED_STATE:
                    return AsyncUtils.<O1>failure(scheduler, createCancellationException());
                case WRAPPED_STATE:
                    return underlyingTask.then(action, errorHandler);
                default:
                    throw createIllegalStateException();
            }
        });
    }

    @Override
    public final <O1> AsyncResult<O1> then(final ThrowableFunction<? super O, ? extends O1> action) {
        return nonTransitive((int currentState) -> {
            switch (currentState) {
                case PENDING_STATE:
                    return then((O value) -> {
                        try {
                            return AsyncUtils.successful(scheduler, action.apply(value));
                        } catch (final Exception err) {
                            return AsyncUtils.<O1>failure(scheduler, err);
                        }
                    });
                case WRAPPED_STATE:
                    return underlyingTask.then(action);
                case CANCELLED_STATE:
                    return AsyncUtils.<O1>failure(scheduler, createCancellationException());
                default:
                    throw createIllegalStateException();
            }
        });
    }

    private static <O> Function<TaskScheduler, ProxyTask<O, Void>> createCallbackTaskFactory(final AsyncResult<O> parent,
                                                                                             final AsyncCallback<? super O> callback) {
        return scheduler -> new ProxyTask<O, Void>(scheduler, parent) {
            @Override
            protected void run(final O result, final Exception err) {
                callback.invoke(result, err);
                if (err != null) failure(err);
                else complete((Void) null);
            }
        };
    }

    @Override
    public final void onCompleted(final AsyncCallback<? super O> callback) {
        Objects.requireNonNull(callback, "callback is null.");
        nonTransitive((int currentState) -> {
            switch (currentState) {
                case PENDING_STATE:
                    scheduler.submitDirect(createCallbackTaskFactory(this, callback));
                    return;
                case WRAPPED_STATE:
                    underlyingTask.onCompleted(callback);
                    return;
                case CANCELLED_STATE:
                    AsyncUtils.<O>failure(scheduler, createCancellationException()).onCompleted(callback);
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


