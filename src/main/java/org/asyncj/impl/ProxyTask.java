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
abstract class ProxyTask<I, O> extends ConcurrentLinkedQueue<AsyncCallback<? super O>> implements TraceableAsyncResult<O>, RunnableFuture<O> {
    private volatile AsyncResultState state;
    private final TaskScheduler scheduler;
    private final AsyncResult<I> parent;
    private final BooleanLatch signaller;
    private volatile AsyncResult<? extends O> underlyingTask;
    private volatile Object marker;

    protected ProxyTask(final TaskScheduler scheduler, final AsyncResult<I> parent){
        Objects.requireNonNull(scheduler, "scheduler is null.");
        this.scheduler = scheduler;
        this.parent = parent;
        state = AsyncResultState.CREATED;
        signaller = new BooleanLatch();
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

    protected final void complete(final AsyncResult<? extends O> ar){
        underlyingTask = ar;
        signaller.signal();
        while (!isEmpty())
            underlyingTask.onCompleted(poll());
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
        if(underlyingTask == null){
            state = AsyncResultState.CANCELLED;
            return true;
        }
        else return underlyingTask.cancel(mayInterruptIfRunning);
    }

    /**
     * Gets state of this task.
     * @return The state of this task.
     */
    @Override
    public final AsyncResultState getAsyncState() {
        return underlyingTask != null ? underlyingTask.getAsyncState() : state;
    }

    /**
     * Returns {@code true} if this task was cancelled before it completed
     * normally.
     *
     * @return {@code true} if this task was cancelled before it completed
     */
    @Override
    public final boolean isCancelled() {
        return underlyingTask != null ? underlyingTask.isCancelled() : state == AsyncResultState.CANCELLED;
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
        if(underlyingTask != null) return underlyingTask.isDone();
        else switch (state){
            case CANCELLED:
            case COMPLETED:
            case FAILED: return true;
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
        signaller.await();
        return underlyingTask.get();
    }

    private O get(final long millis) throws InterruptedException, ExecutionException, TimeoutException {
        final Instant now = Instant.now();
        signaller.await(millis, TimeUnit.MILLISECONDS);
        final Duration timeout = Duration.between(now, Instant.now());
        if (timeout.isNegative()) throw new TimeoutException();
        else return underlyingTask.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
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
        return timeout < 0 || timeout == Long.MAX_VALUE ? get() : get(unit.toMillis(timeout));
    }

    @Override
    public final  <O1> AsyncResult<O1> then(final Function<? super O, AsyncResult<O1>> action,
                                            final Function<Exception, AsyncResult<O1>> errorHandler) {
        Objects.requireNonNull(action, "action is null.");
        if(underlyingTask != null) return underlyingTask.then(action, errorHandler);
        else return scheduler.enqueue((TaskScheduler scheduler) -> new ProxyTask<O, O1>(scheduler, this) {
            @Override
            protected void run(final O result, final Exception err) {
                if (err != null)
                    if (errorHandler != null) super.complete(errorHandler.apply(err));
                    else super.failure(err);
                else super.complete(action.apply(result));
            }
        });
    }

    @Override
    public final  <O1> AsyncResult<O1> then(Function<? super O, AsyncResult<O1>> action) {
        Objects.requireNonNull(action, "action is null.");
        if(underlyingTask != null) return underlyingTask.then(action);
        else return scheduler.enqueue((TaskScheduler scheduler) -> new ProxyTask<O, O1>(scheduler, this) {
            @Override
            protected void run(final O result, final Exception err) {
                if (err != null) super.failure(err);
                else super.complete(action.apply(result));
            }
        });
    }

    @Override
    public final  <O1> AsyncResult<O1> then(final ThrowableFunction<? super O, ? extends O1> action,
                                            final ThrowableFunction<Exception, ? extends O1> errorHandler) {
        Objects.requireNonNull(action, "action is null.");
        if(underlyingTask != null) return underlyingTask.then(action, errorHandler);
        else {
            Function<Exception, AsyncResult<O1>> alternative = null;
            if(errorHandler != null)
                alternative = (Exception err)->{
                    try {
                        return scheduler.successful(errorHandler.apply(err));
                    }
                    catch (final Exception e) {
                        return scheduler.failure(e);
                    }
                };

            return then((O value) -> {
                try {
                    return scheduler.successful(action.apply(value));
                }
                catch (final Exception err) {
                    return scheduler.failure(err);
                }
            }, alternative);
        }
    }

    @Override
    public <O1> AsyncResult<O1> then(final ThrowableFunction<? super O, ? extends O1> action) {
        if (underlyingTask != null) return underlyingTask.then(action);
        else
            return then((O value) -> {
                try {
                    return scheduler.successful(action.apply(value));
                }
                catch (final Exception err) {
                    return scheduler.failure(err);
                }
            });
    }

    @Override
    public final void onCompleted(final AsyncCallback<? super O> callback) {
        Objects.requireNonNull(callback, "callback is null.");
        if (underlyingTask != null) underlyingTask.onCompleted(callback);
        else offer(callback);
    }

    /**
     * Always returns {@literal true}.
     * @return {@literal true}.
     */
    @Override
    public final boolean isProxy() {
        return true;
    }

    @Override
    public final void setMarker(final Object marker) {
        this.marker = marker;
    }

    @Override
    public final Object getMarker() {
        return marker;
    }

    @Override
    public final String toString() {
        return underlyingTask == null ? String.format("ProxyTask %s (state = %s, marker = %s)",
                getID(),
                state,
                marker):
                underlyingTask.toString();
    }
}


