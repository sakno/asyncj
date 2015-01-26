package asyncj.impl;

import asyncj.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

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
public abstract class Task<V> extends AbstractQueuedSynchronizer implements AsyncResult<V>, RunnableFuture<V>, Callable<V> {

    private static boolean useAdvancedStringRepresentation = false;

    private static final int CREATED_STATE = 1;  //initial state
    private static final int EXECUTED_STATE = 2; //temporary state
    private static final int COMPLETED_STATE = 4; //task is completed (successfully or unsuccessfully)
    private static final int CANCELLED_STATE = 8; //task is cancelled
    private static final int FINAL_STATE = COMPLETED_STATE | CANCELLED_STATE;

    private Exception error;
    private V result;
    private final TaskScheduler scheduler;
    //this flag is used to prevent transition between states.
    // It is used for 'then' request to prevent dead children tasks.
    private volatile boolean preventTransition;

    /*
     * Usually, the task has no more than one children task.
     * Therefore, it is reasonable not use LinkedList for storing a collection of task children.
     * Instead of LinkedList the task represents linked list Node itself.
     * Of course, more that 2-3 children tasks will not be processed effectively.
     */
    private final AtomicReference<Task> nextTask;

    /**
     * Initializes a new task prepared for execution in the specified scheduler.
     * @param scheduler The scheduler that owns by the newly created task. Cannot be {@literal null}.
     */
    protected Task(final TaskScheduler scheduler){
        setState(CREATED_STATE);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler is null.");
        result = null;
        error = null;
        nextTask = new AtomicReference<>(null);
        preventTransition = false;
    }

    @SuppressWarnings("unchecked")
    private <O> Task<O> appendChildTask(final Task<O> childTask) {
        return nextTask.updateAndGet(nextTask -> nextTask == null ? childTask : nextTask.appendChildTask(childTask));
    }

    private <O> Task<O> createAndAppendChildTask(final Callable<O> task) {
        return appendChildTask(newChildTask(scheduler, task));
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

    @Override
    protected final int tryAcquireShared(final int ignore) {
        return (getState() & FINAL_STATE) != 0 ? 1 : -1;
    }

    @Override
    protected final boolean tryReleaseShared(final int ignore) {
        return true;
    }

    /**
     * Gets state of this task.
     * @return The state of this task.
     */
    @Override
    public final AsyncResultState getAsyncState(){
        final int currentState = getState();
        switch (currentState){
            case CREATED_STATE: return AsyncResultState.CREATED;
            case COMPLETED_STATE: return error != null ? AsyncResultState.FAILED : AsyncResultState.COMPLETED;
            case CANCELLED_STATE: return AsyncResultState.CANCELLED;
            case EXECUTED_STATE: return AsyncResultState.EXECUTED;
            //never happens
            default: throw new IllegalStateException(String.format("Invalid task state: %s", currentState));
        }
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
        int currentState;
        do {
            switch (currentState = getState()) {
                case COMPLETED_STATE:
                    return false;
                case CANCELLED_STATE:
                    return true;
            }
        }
        while (preventTransition || !compareAndSetState(currentState, CANCELLED_STATE));
        try {
            return !mayInterruptIfRunning || scheduler.interrupt(this);
        } finally {
            releaseShared(0);
            done(CANCELLED_STATE);
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
        return (getState() & FINAL_STATE) != 0;
    }

    private static <V> V prepareResult(final int currentState, final V result, final Exception error) throws ExecutionException, CancellationException {
        if (currentState == CANCELLED_STATE)
            throw new CancellationException();
        else if (error instanceof CancellationException)
            throw (CancellationException) error;
        else if (error instanceof InterruptedException)
            throw new CancellationException(error.getMessage());
        else if (error != null)
            throw new ExecutionException(error);
        else return result;
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
        acquireSharedInterruptibly(0);
        return prepareResult(getState(), result, error);
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
        if (!tryAcquireSharedNanos(0, unit.toNanos(timeout)))
            throw new TimeoutException();
        else return prepareResult(getState(), result, error);
    }

    /**
     * Returns {@code true} if this task was cancelled before it completed
     * normally.
     *
     * @return {@code true} if this task was cancelled before it completed
     */
    @Override
    public final boolean isCancelled() {
        return getState() == CANCELLED_STATE;
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
        if (compareAndSetState(CREATED_STATE, EXECUTED_STATE))
            try {
                setResult(call(), value -> {
                    this.result = value;
                    return COMPLETED_STATE;
                });
            } catch (final Exception e) {
                setResult(e, value -> {
                    this.error = value;
                    return value instanceof CancellationException || value instanceof InterruptedException ? CANCELLED_STATE : COMPLETED_STATE;
                });
            }
    }

    /**
     * Invoked when task transits into the final state: COMPLETED, EXCEPTIONAL, CANCELLED
     * @param finalState The final state of the task.
     */
    private void done(final int finalState) {
        final Task nt = nextTask.getAndUpdate(t -> null); //help GC
        if (nt != null)
            switch (finalState) {
                case CANCELLED_STATE:
                    nt.cancel(true);
                    return;
                case COMPLETED_STATE:
                    scheduler.submit((Runnable) nt);
            }
    }

    private <T> void setResult(final T result, final ToIntFunction<T> fieldChanger) {
        int currentState;
        do {
            switch (currentState = getState()) {
                case CANCELLED_STATE:
                    releaseShared(0); //handle potential racing with a cancel request
                case COMPLETED_STATE:
                    return;
            }
        }
        while (preventTransition || !compareAndSetState(currentState, COMPLETED_STATE));
        final int finalState = fieldChanger.applyAsInt(result);
        releaseShared(0);
        done(finalState);
    }

    /**
     * Invokes the task.
     * @return The task invocation result.
     * @throws java.lang.Exception Error occurred during task execution.
     */
    @Override
    public abstract V call() throws Exception;

    /**
     * Creates a new instance of the child task.
     * @param scheduler The scheduler that owns by the newly created task. Cannot be {@literal null}.
     * @param task The implementation of the child task. Cannot be {@literal null}.
     * @param <O> Type of the asynchronous computation result.
     * @return A new instance of the child task.
     */
    protected <O> Task<O> newChildTask(final TaskScheduler scheduler, final Callable<O> task){
        return new Task<O>(scheduler) {
            @Override
            public O call() throws Exception {
                return task.call();
            }
        };
    }

    private <O> AsyncResult<O> then(final Callable<O> task) {
        preventTransition = true;
        try {
            switch (getState()) {
                case COMPLETED_STATE:
                    return scheduler.submit(task);
                case EXECUTED_STATE:
                case CREATED_STATE:
                    return createAndAppendChildTask(task);
                case CANCELLED_STATE:
                    return AsyncUtils.cancellation(scheduler);
                //never happens
                default:
                    throw new IllegalStateException(String.format("Invalid task state %s", getAsyncState()));
            }
        } finally {
            preventTransition = false;
        }
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
        return scheduler.submitDirect((TaskScheduler scheduler) -> new ProxyTask<V, O>(scheduler, this) {
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
        return scheduler.submitDirect((TaskScheduler scheduler) -> new ProxyTask<V, O>(scheduler, this) {
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
