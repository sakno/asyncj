package org.asyncj.impl;

import org.asyncj.*;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Represents asynchronously executing task.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public abstract class Task<V> extends BooleanLatch implements AsyncResult<V>, RunnableFuture<V>, Callable<V> {
    private volatile Exception error;
    private volatile V result;
    private volatile AsyncResultState state;
    private final TaskScheduler scheduler;
    private final ConcurrentLinkedQueue<Task<?>> children;

    /**
     * Initializes a new task enqueued in the specified scheduler.
     * @param scheduler The scheduler that owns by the newly created task. Cannot be {@literal null}.
     */
    protected Task(final TaskScheduler scheduler){
        Objects.requireNonNull(scheduler, "scheduler is null.");
        result = null;
        error = null;
        this.scheduler = scheduler;
        this.state = AsyncResultState.CREATED;
        children = new ConcurrentLinkedQueue<>();
    }

    /**
     * Gets state of this task.
     * @return The state of this task.
     */
    @Override
    public final AsyncResultState getAsyncState(){
        return state;
    }

    /**
     * Determines whether this task is scheduled by the specified scheduler.
     * @param scheduler The scheduler to check.
     * @return {@literal true}, if this task is scheduled by the specified scheduler; otherwise, {@literal false}.
     */
    public final boolean isScheduledBy(final TaskScheduler scheduler){
        return this.scheduler == scheduler;
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
        switch (state){
            case CREATED:
                state = AsyncResultState.CANCELLED;
                error = new CancellationException(String.format("Task %s is cancelled." , this));
                signal();
            case CANCELLED: return true;
            case EXECUTED:
                if(mayInterruptIfRunning && scheduler.interrupt(this)){
                    state = AsyncResultState.CANCELLED;
                    return true;
                }
                else return false;
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
        return getState() != 0;
    }

    /**
     * Waits if necessary for the computation to complete, and then
     * retrieves its result.
     *
     * @return the computed result
     * @throws java.util.concurrent.CancellationException if the computation was cancelled
     * @throws java.util.concurrent.ExecutionException    if the computation threw an
     *                               exception
     * @throws InterruptedException  if the current thread was interrupted
     *                               while waiting
     */
    @Override
    public final V get() throws InterruptedException, ExecutionException {
        await();
        switch (state){
            case CANCELLED: throw new CancellationException();
            case FAILED: throw new ExecutionException(error);
            default: return result;
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
    public final V get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        await(timeout, unit);
        switch (state) {
            case CANCELLED:
                throw new CancellationException();
            case FAILED:
                throw new ExecutionException(error);
            default:
                return result;
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
        return state == AsyncResultState.CANCELLED;
    }

    private void complete(final V value){
        result = value;
        state = AsyncResultState.COMPLETED;
    }

    private void fail(final Exception e){
        error = e;
        state = (e instanceof InterruptedException) || (e instanceof CancellationException) ? AsyncResultState.CANCELLED : AsyncResultState.FAILED;
    }

    /**
     * Executes the task synchronously.
     */
    @Override
    public final void run() {
        if(isDone()) return;
        state = AsyncResultState.EXECUTED;
        try {
            complete(call());
        }
        catch (final Exception e) {
            fail(e);
        }
        finally {
            signal();
        }
        //process all children tasks
        while (!children.isEmpty())
            scheduler.enqueue(children.poll());
    }

    /**
     * Invokes the task.
     * @return The task invocation result.
     * @throws java.lang.Exception Error occurred during task execution.
     */
    @Override
    public abstract V call() throws Exception;

    private <O> Task<O> enqueueChildTask(final Callable<O> task){
        final Task<O> result = new Task<O>(scheduler) {
            @Override
            public O call() throws Exception {
                return task.call();
            }
        };
        children.offer(result);
        return result;
    }

    private <O> AsyncResult<O> then(final Callable<O> task){
        switch (state){
            case FAILED:
            case COMPLETED: scheduler.enqueue(task);
            case CREATED:
            case EXECUTED: return enqueueChildTask(task);
            case CANCELLED: return scheduler.failure(error);
            //never happens
            default: throw new IllegalStateException(String.format("Invalid task state %s", state));
        }
    }

    @Override
    public final void onCompleted(final AsyncCallback<? super V> callback) {
        Objects.requireNonNull(callback, "callback is null.");
        then(()->{
            callback.invoke(result, error);
            return null;
        });
    }

    @Override
    public final  <O> AsyncResult<O> then(final ThrowableFuntion<? super V, ? extends O> action,
                                                        final ThrowableFuntion<Exception, ? extends O> errorHandler) {
        Objects.requireNonNull(action, "action is null.");
        return then(()->{
            if(error == null)
                return action.apply(result);
            else if(errorHandler == null)
                throw error;
            else return errorHandler.apply(error);
        });
    }

    @Override
    public final  <O> AsyncResult<O> then(ThrowableFuntion<? super V, ? extends O> action) {
        Objects.requireNonNull(action, "action is null.");
        return then(()->{
            if(error == null)
                return action.apply(result);
            else throw  error;
        });
    }

    @Override
    public final  <O> AsyncResult<O> then(final Function<? super V, AsyncResult<O>> action,
                                   final Function<Exception, AsyncResult<O>> errorHandler) {
        Objects.requireNonNull(action, "action is null.");
        return scheduler.enqueue((TaskScheduler scheduler)-> new ProxyTask<V, O>(scheduler, this) {
            @Override
            protected void run(final V result, final Exception err) {
                if(err != null)
                    if(errorHandler != null) complete(errorHandler.apply(err));
                    else failure(err);
                else complete(action.apply(result));
            }
        });
    }

    @Override
    public final  <O> AsyncResult<O> then(Function<? super V, AsyncResult<O>> action) {
        Objects.requireNonNull(action, "action is null.");
        return scheduler.enqueue((TaskScheduler scheduler)-> new ProxyTask<V, O>(scheduler, this) {
            @Override
            protected void run(final V result, final Exception err) {
                if(err != null) failure(err);
                else complete(action.apply(result));
            }
        });
    }
}
