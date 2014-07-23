package org.asyncj.impl;

import org.asyncj.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.function.Function;

/**
 * Represents proxy task that wraps another asynchronous result.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
abstract class ProxyTask<I, O> extends AbstractQueuedSynchronizer implements AsyncResult<O>, RunnableFuture<O> {

    private static final int PENDING = 1, //represents inital state of the proxy task
                             CANCELLED = 2, //proxy task is cancelled
                             WRAPPED = 4, //proxy task wraps another asynchronous result
                             ANY_STATE = PENDING | CANCELLED | WRAPPED;


    private final TaskScheduler scheduler;
    private final AsyncResult<I> parent;
    private volatile AsyncResult<O> underlyingTask;

    protected ProxyTask(final TaskScheduler scheduler, final AsyncResult<I> parent){
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler is null.");
        this.parent = Objects.requireNonNull(parent, "parent task is null.");
        setState(PENDING);
    }

    /**
     * Attempts to acquire exclusive access to the fields of this task
     * or blocks the current thread until the task will not be transited to the specified state.
     * @param expectedState The state of the task expected by the caller code.
     * @return {@literal true} if this task is in requested state; otherwise, {@literal false}, and
     * caller thread join into waiting loop.
     */
    @Override
    protected final boolean tryAcquire(final int expectedState) {
        final Thread currentThread = Thread.currentThread(), ownerThread = getExclusiveOwnerThread();
        if((expectedState & getState()) == 0) return false;
        else if(currentThread == ownerThread || ownerThread == null){
            setExclusiveOwnerThread(currentThread);
            return true;
        }
        else return false;
    }

    /**
     * Releases exclusive lock to the fields of this task and assigns
     * a new task state.
     * @param newState A new task state.
     * @return {@literal true}, if the specified state is supported by transitive; otherwise, {@literal false}.
     */
    @Override
    protected final boolean tryRelease(final int newState) {
        int currentState;
        do {
            currentState = getState();
            /*
             * Possible transitions:
             * PENDING -> PENDING
             * PENDING -> CANCELLED
             * PENDING -> WRAPPED
             */
            if(currentState > PENDING && currentState != newState) return false;
        }
        while (!compareAndSetState(currentState, newState)); //set new task state
        return true;
    }

    /**
     * Attempts to acquire non-exclusive lock for read-only access to the
     * fields of this task.
     * @param expectedState The state of the task expected by the caller code.
     * @return {@code 1}, if read lock is acquired successfully; otherwise, {@code -1},
     * and caller thread join into waiting loop.
     */
    @Override
    protected final int tryAcquireShared(final int expectedState) {
        return tryAcquire(expectedState) ? 1 : -1;
    }

    /**
     * Releases non-exclusive lock for read-only access to the fields of
     * this task and assigns
     * a new task state.
     * @param newState A new task state.
     * @return {@literal true}, if the specified state is supported by transitive; otherwise, {@literal false}.
     */
    @Override
    protected final boolean tryReleaseShared(final int newState) {
        return tryRelease(newState);
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

    protected abstract void run(final I result, final Exception err);

    protected final void complete(final AsyncResult<O> ar) {
        underlyingTask = ar;
        releaseShared(1);
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
        acquire(ANY_STATE); //expects any state
        try{
            return underlyingTask == null || underlyingTask.cancel(mayInterruptIfRunning);
        }
        finally {
            release(CANCELLED);
        }
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
        if (tryAcquireSharedNanos(WRAPPED, nanos))
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
        acquireShared(ANY_STATE);
        try {
            switch (getState()){
                case PENDING:
                    return scheduler.enqueueDirect((TaskScheduler scheduler) -> new ProxyTask<O, O1>(scheduler, this) {
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
                default: throw createIllegalStateException();
            }
        }
        finally {
            releaseShared(getState());
        }
    }

    @Override
    public final  <O1> AsyncResult<O1> then(final Function<? super O, AsyncResult<O1>> action) {
        Objects.requireNonNull(action, "action is null.");
        acquireShared(ANY_STATE);
        try{
            switch (getState()){
                case PENDING: return scheduler.enqueueDirect((TaskScheduler scheduler) -> new ProxyTask<O, O1>(scheduler, this) {
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
                default: throw createIllegalStateException();
            }
        }
        finally {
            releaseShared(getState());
        }
    }

    @Override
    public final  <O1> AsyncResult<O1> then(final ThrowableFunction<? super O, ? extends O1> action,
                                            final ThrowableFunction<Exception, ? extends O1> errorHandler) {
        Objects.requireNonNull(action, "action is null.");
        acquireShared(ANY_STATE);
        try {
            switch (getState()){
                case PENDING:
                    final Function<Exception, AsyncResult<O1>> alternative = errorHandler != null ?
                        (Exception err)->{
                                try {
                                    return scheduler.successful(errorHandler.apply(err));
                                }
                                catch (final Exception e) {
                                    return scheduler.failure(e);
                                }
                            }: null;
                    return then((O value) -> {
                        try {
                            return scheduler.successful(action.apply(value));
                        }
                        catch (final Exception err) {
                            return scheduler.failure(err);
                        }
                    }, alternative);
                case CANCELLED: return scheduler.<O1>failure(createCancellationException());
                case WRAPPED: return underlyingTask.then(action, errorHandler);
                default: throw createIllegalStateException();
            }
        }
        finally {
            releaseShared(getState());
        }
    }

    @Override
    public final <O1> AsyncResult<O1> then(final ThrowableFunction<? super O, ? extends O1> action) {
        acquireShared(ANY_STATE);
        try {
            switch (getState()){
                case PENDING: return then((O value) -> {
                    try {
                        return scheduler.successful(action.apply(value));
                    }
                    catch (final Exception err) {
                        return scheduler.failure(err);
                    }
                });
                case WRAPPED: return underlyingTask.then(action);
                case CANCELLED: return scheduler.<O1>failure(createCancellationException());
                default: throw createIllegalStateException();
            }
        }
        finally {
            releaseShared(getState());
        }
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
        acquireShared(ANY_STATE);
        try {
            switch (getState()) {
                case PENDING:
                    scheduler.enqueueDirect(createCallbackTaskFactory(this, callback));
                    return;
                case WRAPPED:
                    underlyingTask.onCompleted(callback);
                    return;
                case CANCELLED:
                    scheduler.<O>failure(createCancellationException()).onCompleted(callback);
            }
        } finally {
            releaseShared(getState());
        }
    }

    @Override
    public final String toString() {
        return underlyingTask == null ? String.format("ProxyTask %s (state = %s)",
                getID(),
                getAsyncState()):
                underlyingTask.toString();
    }
}


