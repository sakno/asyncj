package asyncj.impl;

import asyncj.AsyncCallback;
import asyncj.AsyncResult;
import asyncj.AsyncResultState;
import asyncj.TaskScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.function.IntSupplier;

/**
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
abstract class AbstractTask<V> extends AbstractQueuedSynchronizer implements InternalAsyncResult<V>, IntSupplier {
    private static boolean useAdvancedStringRepresentation = false;

    static final int CREATED_STATE = 1;  //initial state
    static final int EXECUTED_STATE = 2; //temporary state
    static final int COMPLETED_STATE = 4; //task is completed (successfully or unsuccessfully)
    static final int CANCELLED_STATE = 8; //task is cancelled
    static final int FINAL_STATE = COMPLETED_STATE | CANCELLED_STATE;

    private Exception error;
    private V result;
    protected final TaskScheduler scheduler;
    private final AsyncCallbackList<V> callbacks;
    //this flag is used to prevent transition between states.
    // It is used for 'then' request to prevent dead children tasks.
    private volatile boolean preventTransition;


    AbstractTask(final TaskScheduler scheduler){
        setState(CREATED_STATE);
        this.scheduler = scheduler;
        preventTransition = false;
        callbacks = new AsyncCallbackList<>();
    }

    /**
     * Gets priority of this task.
     * @return The priority of this task.
     */
    @Override
    public int getAsInt() {
        return 0;
    }

    final Exception getError(){
        return error;
    }

    final V getResult(){
        return result;
    }

    final int complete(final V result, final Exception error){
        int currentState;
        final int newState = error instanceof InterruptedException ||
                error instanceof CancellationException ? CANCELLED_STATE : COMPLETED_STATE;
        do {
            switch (currentState = getState()) {
                case CANCELLED_STATE:
                    releaseShared(0); //handle potential racing with a cancel request
                case COMPLETED_STATE:
                    return currentState;
            }
        }
        while (preventTransition || !compareAndSetState(currentState, newState));
        this.error = error;
        this.result = result;
        releaseShared(0);
        return newState;
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
     * Determines whether this task is scheduled by the specified scheduler.
     * @param scheduler The scheduler to check.
     * @return {@literal true}, if this task is scheduled by the specified scheduler; otherwise, {@literal false}.
     */
    @Override
    public final boolean isScheduledBy(final TaskScheduler scheduler) {
        return this.scheduler == scheduler;
    }

    /**
     * Gets unique identifier of this task.
     * @return The unique identifier of this task.
     */
    @Override
    public final long getID(){
        return ((long)scheduler.hashCode() << 32) | ((long)hashCode() & 0xFFFFFFFL);
    }

    /**
     * Attaches completion callback to this task.
     * @param callback The completion callback. Cannot be {@literal null}.
     */
    @Override
    public final void onCompleted(final AsyncCallback<? super V> callback) {
        onCompletedImpl(callback);
    }

    final boolean cannotChangeState(){
        return preventTransition;
    }

    private static <V> Runnable wrapCallback(final V result,
                                               final Exception error,
                                               final AsyncCallback<? super V> callback){
        return ()->callback.invoke(result, error);
    }

    final AsyncResult<?> onCompletedImpl(final AsyncCallback<? super V> callback){
        preventTransition = true;
        try {
            switch (getState()) {
                case CANCELLED_STATE:
                    return scheduler.submit(wrapCallback(null, new CancellationException(), callback));
                case COMPLETED_STATE:
                    return scheduler.submit(wrapCallback(result, error, callback));
                case EXECUTED_STATE:
                case CREATED_STATE:
                    callbacks.add(callback);
                default:
                    return this;
            }
        } finally {
            preventTransition = false;
        }
    }

    /**
     * Gets state of this task.
     * @return The state of this task.
     */
    @Override
    public final AsyncResultState getAsyncState() {
        switch (getState()) {
            case CREATED_STATE:
                return AsyncResultState.CREATED;
            case COMPLETED_STATE:
                return error != null ? AsyncResultState.FAILED : AsyncResultState.COMPLETED;
            case CANCELLED_STATE:
                return AsyncResultState.CANCELLED;
            case EXECUTED_STATE:
                return AsyncResultState.EXECUTED;
            //never happens
            default:
                return null;
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
        return prepareResult(getState(), getResult(), getError());
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
        else return prepareResult(getState(), getResult(), getError());
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
     * Invoked when task transits into the final state: COMPLETED, EXCEPTIONAL, CANCELLED
     * @param finalState The final state of the task.
     */
    final void done(final int finalState) {
        //unwind callbacks
        final Exception error = this.error;
        final V result = this.result;
        for (final AsyncCallback<? super V> callback : callbacks.dumpCallbacks())
            scheduler.submit(()-> {
                switch (finalState) {
                    case CANCELLED_STATE:
                        callback.invoke(null,
                                error instanceof InterruptedException || error instanceof CancellationException ?
                                        error : new CancellationException());
                    default:
                        callback.invoke(result, error);
                }
            });
    }

    @Override
    protected final int tryAcquireShared(final int ignore) {
        return (getState() & FINAL_STATE) != 0 ? 1 : -1;
    }

    @Override
    protected final boolean tryReleaseShared(final int ignore) {
        return true;
    }

    @Override
    public final V get(final Duration timeout) throws InterruptedException, ExecutionException, TimeoutException {
        return timeout == null ? get() : get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private String toString(final Map<String, Object> fields){
        fields.put("state", getAsyncState());
        if(useAdvancedStringRepresentation){
            fields.put("result", result);
            fields.put("error", error);
            fields.put("hasNoChildren", callbacks.isEmpty());
        }
        final Collection<String> stringBuilder = new ArrayList<>(fields.size());
        fields.entrySet()
                .stream()
                .forEach(entry -> stringBuilder.add(String.format("%s = %s", entry.getKey(), entry.getValue())));
        return String.format("Task %s(%s)", getID(), String.join(", ", stringBuilder));
    }

    @Override
    public String toString() {
        return toString(new HashMap<>(7));
    }
}
