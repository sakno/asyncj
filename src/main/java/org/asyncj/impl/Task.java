package org.asyncj.impl;

import org.asyncj.*;

import java.util.*;
import java.util.concurrent.*;
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
 * @version 1.0
 * @since 1.0
 */
public abstract class Task<V> extends SynchronizedStateMachine implements AsyncResult<V>, RunnableFuture<V>, Callable<V> {
    private static boolean useAdvancedStringRepresentation = false;

    private static final int CREATED_STATE = 1;
    private static final int EXECUTED_STATE = 2;
    private static final int COMPLETED_STATE = 4;//this is a complex state that covers COMPLETED, CANCELLED and FAILED
    private static final int ANY_STATE = CREATED_STATE | EXECUTED_STATE | COMPLETED_STATE;

    private volatile Exception error;
    private volatile V result;
    private final TaskScheduler scheduler;
    /*
     * Usually, the task has no more than one children task.
     * Therefore, it is reasonable not use LinkedList for storing a collection of task children.
     * Instead of LinkedList the task represents linked list Node itself.
     * Of course, more that 2-3 children tasks will not be processed effectively.
     */
    private volatile Task nextTask;

    /**
     * Initializes a new task prepared for execution in the specified scheduler.
     * @param scheduler The scheduler that owns by the newly created task. Cannot be {@literal null}.
     */
    protected Task(final TaskScheduler scheduler){
        super(CREATED_STATE);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler is null.");
        result = null;
        error = null;
        //setState(CREATED_STATE);
        nextTask = null;
    }

    private void appendChildTask(final Task childTask){
        if(nextTask == null) nextTask = childTask;
        else nextTask.appendChildTask(childTask);
    }

    private <O> Task<O> createAndAppendChildTask(final Callable<? extends O> task){
        final Task<O> childTask = newChildTask(scheduler, task);
        appendChildTask(childTask);
        return childTask;
    }

    /**
     * Advances implementation of {@link #toString()} method so that it return value will
     * include encapsulated result and error.
     * <p>
     *     By default, {@link #toString()} method of the task returns ID, state and marker.
     * </p>
     */
    public static void enableAdvancedStringRepresentation(){
        useAdvancedStringRepresentation = true;
    }

    /**
     * Gets unique identifier of this task.
     * @return The unique identifier of this task.
     */
    public final long getID(){
        return ((long)scheduler.hashCode() << 32) | ((long)hashCode() & 0xFFFFFFFL);
    }

    private static boolean isCancellationException(final Exception error){
        return error instanceof InterruptedException || error instanceof CancellationException;
    }

    private static AsyncResultState getAsyncState(final int state, final Exception error){
        switch (state){
            case CREATED_STATE: return AsyncResultState.CREATED;
            case EXECUTED_STATE: return AsyncResultState.EXECUTED;
            case COMPLETED_STATE:
                if(error == null) return AsyncResultState.COMPLETED;
                else if(isCancellationException(error))
                    return AsyncResultState.CANCELLED;
                else return AsyncResultState.FAILED;
                //never happens
            default: return null;
        }
    }


    /**
     * Gets state of this task.
     * @return The state of this task.
     */
    @Override
    public final AsyncResultState getAsyncState(){
        return getAsyncState(getState(), error);
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
        return writeOnTransition(ANY_STATE, COMPLETED_STATE, currentState -> {
            switch (currentState) {
                case CREATED_STATE:
                    error = new CancellationException(String.format("%s is cancelled.", this));
                    if (nextTask != null) nextTask.cancel(mayInterruptIfRunning);
                    nextTask = null; //help GC
                    return true;
                case EXECUTED_STATE:
                    return scheduler.interrupt(this);
                case COMPLETED_STATE:
                    return isCancellationException(error);
                default:
                    return false;
            }
        });
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
        return getState() == COMPLETED_STATE;
    }

    private static <V> V prepareTaskResult(final V result, final Exception error) throws CancellationException, ExecutionException{
        if(error == null) return result;
        else if(error instanceof CancellationException) throw (CancellationException)error;
        else if(error instanceof InterruptedException) throw new CancellationException(error.getMessage());
        else throw new ExecutionException(error);
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
        acquireSharedInterruptibly(COMPLETED_STATE);
        try {
            return prepareTaskResult(result, error);
        } finally {
            releaseShared(COMPLETED_STATE);
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
        tryAcquireSharedNanos(COMPLETED_STATE, unit.toNanos(timeout));
        try{
            return prepareTaskResult(result, error);
        }
        finally {
            releaseShared(COMPLETED_STATE);
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
        return isDone() && isCancellationException(error);
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
        switch (acquireAndGetState(ANY_STATE)) {
            case CREATED_STATE:
                try {
                    result = call();
                } catch (final Exception e) {
                    error = e;
                } finally {
                    release(COMPLETED_STATE);
                }
                if (nextTask != null)
                    if (isCancellationException(error))
                        nextTask.cancel(false);
                    else scheduler.enqueue(nextTask);
                nextTask = null; //help GC
                return;
            default:
                release(getState());
        }
    }

    /**
     * Validates state machine transition.
     * @param from The source state of the task.
     * @param to The sink state of the task.
     * @return {@literal true}, if the specified transition is valid; otherwise, {@literal false}.
     */
    @Override
    protected final boolean isValidTransition(final int from, final int to) {
        return to >= from;
    }

    /**
     * Invokes the task.
     * @return The task invocation result.
     * @throws java.lang.Exception Error occurred during task execution.
     */
    @Override
    public abstract V call() throws Exception;

    <O> Task<O> newChildTask(final TaskScheduler scheduler, final Callable<? extends O> task){
        return new Task<O>(scheduler) {
            @Override
            public O call() throws Exception {
                return task.call();
            }
        };
    }

    private <O> AsyncResult<O> then(final Callable<? extends O> task) {
        return writeOnTransition(ANY_STATE, currentState -> {
            switch (currentState) {
                case COMPLETED_STATE:
                    return isCancellationException(error) ? scheduler.failure(error) : scheduler.enqueue(task);
                case EXECUTED_STATE:
                case CREATED_STATE:
                    return createAndAppendChildTask(task);
                //never happens
                default:
                    throw new IllegalStateException(String.format("Invalid task state %s", getAsyncState()));
            }
        });
    }

    /**
     * Attaches completion callback to this task.
     * @param callback The completion callback. Cannot be {@literal null}.
     */
    @Override
    public final void onCompleted(final AsyncCallback<? super V> callback) {
        Objects.requireNonNull(callback, "callback is null.");
        then(()->{
            callback.invoke(result, error);
            return null;
        });
    }

    /**
     * {@inheritDoc}
     * @param action The action implementing attached asynchronous computation if this computation
     *               is completed successfully. Cannot be {@literal null}.
     * @param errorHandler The action implementing attached asynchronous computation if this computation
     *               is failed. May be {@literal null}.
     * @param <O> Type of the attached asynchronous computation result.
     * @return The object that represents state of the attached asynchronous computation.
     */
    @Override
    public final  <O> AsyncResult<O> then(final ThrowableFunction<? super V, ? extends O> action,
                                                        final ThrowableFunction<Exception, ? extends O> errorHandler) {
        Objects.requireNonNull(action, "action is null.");
        return then(()->{
            if(error == null)
                return action.apply(result);
            else if(errorHandler == null)
                throw error;
            else return errorHandler.apply(error);
        });
    }

    /**
     * {@inheritDoc}
     * @param action
     * @param <O>
     * @return
     */
    @Override
    public final  <O> AsyncResult<O> then(final ThrowableFunction<? super V, ? extends O> action) {
        Objects.requireNonNull(action, "action is null.");
        return this.<O>then(() -> {
            if(error == null)
                return action.apply(result);
            else throw error;
        });
    }

    @Override
    public final  <O> AsyncResult<O> then(final Function<? super V, AsyncResult<O>> action,
                                   final Function<Exception, AsyncResult<O>> errorHandler) {
        Objects.requireNonNull(action, "action is null.");
        //synchronization via AQS is not required
        return scheduler.enqueueDirect((TaskScheduler scheduler) -> new ProxyTask<V, O>(scheduler, this) {
            @Override
            protected void run(final V result, final Exception err) {
                if (err != null)
                    if (errorHandler != null) complete(errorHandler.apply(err));
                    else failure(err);
                else complete(action.apply(result));
            }
        });
    }

    @Override
    public final <O> AsyncResult<O> then(Function<? super V, AsyncResult<O>> action) {
        Objects.requireNonNull(action, "action is null.");
        //synchronization via AQS is not required
        return scheduler.enqueueDirect((TaskScheduler scheduler) -> new ProxyTask<V, O>(scheduler, this) {
            @Override
            protected void run(final V result, final Exception err) {
                if (err != null) failure(err);
                else complete(action.apply(result));
            }
        });
    }

    final String toString(final Map<String, Object> fields){
        fields.put("state", getAsyncState());
        if(useAdvancedStringRepresentation){
            fields.put("result", result);
            fields.put("error", error);
            fields.put("hasNoChildren", nextTask == null);
        }
        final Collection<String> stringBuilder = new ArrayList<>(fields.size());
        fields.entrySet()
                .stream()
                .forEach(entry -> stringBuilder.add(String.format("%s = %s", entry.getKey(), entry.getValue())));
        return String.format("Task %s(%s)", getID(), String.join(", ", stringBuilder));
    }

    @Override
    public String toString() {
        return toString(new HashMap<>(3));
    }
}
