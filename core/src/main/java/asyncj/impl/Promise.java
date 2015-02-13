package asyncj.impl;

import asyncj.AsyncCallback;
import asyncj.AsyncResult;
import asyncj.TaskScheduler;
import asyncj.ThrowableFunction;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Represents implementation of the Promise pattern.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
abstract class Promise<I, V> extends AbstractTask<V> implements InternalAsyncResult<V>, AsyncCallback<V> {
    private final int priority;
    private final AsyncResult<?> linkedTask;

    private Promise(final AbstractTask<I> parent){
        super(parent.scheduler);
        this.priority = parent.getAsInt();
        linkedTask = parent.onCompletedImpl(this::onParentCompleted);
    }

    /**
     * Informs this task about completion of the parent task.
     * @param result The result comes from parent task.
     * @param error The error comes from the parent task.
     */
    protected abstract void onParentCompleted(final I result, final Exception error);

    @Override
    public final int getAsInt() {
        return priority;
    }

    final <S> void complete(final S input, final ThrowableFunction<? super S, ? extends V> action) {
        if(compareAndSetState(CREATED_STATE, EXECUTED_STATE)){
            int finalState = EXECUTED_STATE;
            try {
                finalState = super.complete(action.apply(input), null);
            }
            catch (final Exception e) {
                finalState = super.complete(null, e);
            }
            finally {
                done(finalState);
            }
        }
    }

    final void complete(final I input,
                          final ThrowableFunction<? super I, ? extends V> action,
                          final ThrowableFunction<Exception, ? extends V> errorHandler) {
        if (compareAndSetState(CREATED_STATE, EXECUTED_STATE)) {
            int finalState = EXECUTED_STATE;
            try {
                finalState = super.complete(action.apply(input), null);
            }
            catch (final Exception e) {
                try {
                    if ((finalState = getState()) == EXECUTED_STATE)//still running
                        finalState = super.complete(errorHandler.apply(e), null);
                }
                catch (final Exception e1) {
                    finalState = super.complete(null, e1);
                }
            } finally {
                done(finalState);
            }
        }
    }

    /**
     * Informs this object that the asynchronous computation is completed.
     *
     * @param input The result of the asynchronous computation.
     * @param error The error occurred during asynchronous computation.
     */
    @Override
    public final void invoke(final V input, final Exception error) {
        done(complete(input, error));
    }

    final <S> void complete(final S input,
                            final Function<? super S, AsyncResult<V>> action) {
        if (compareAndSetState(CREATED_STATE, EXECUTED_STATE))
            action.apply(input).onCompleted(this);
    }

    final void complete(final I inp,
                            final Function<? super I, AsyncResult<V>> action,
                            final Function<Exception, AsyncResult<V>> errorHandler) {
        if (compareAndSetState(CREATED_STATE, EXECUTED_STATE))
            action.apply(inp).onCompleted((input, error) -> {
                if (getState() == EXECUTED_STATE) { //still running
                    if (error == null)
                        invoke(input, null);
                    else errorHandler.apply(error).onCompleted(this);
                }
            });
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
                case EXECUTED_STATE:
                    if(!linkedTask.cancel(mayInterruptIfRunning)) return false;
            }
        }
        while (cannotChangeState() || !compareAndSetState(currentState, CANCELLED_STATE));
        releaseShared(0);
        done(CANCELLED_STATE);
        return true;
    }

    static <I, O> Promise<I, O> create(final AbstractTask<I> owner,
                                    final Function<? super I, AsyncResult<O>> action) {
        Objects.requireNonNull(action, "action is null.");
        //synchronization via AQS is not required
        return new Promise<I, O>(owner) {
            @Override
            protected void onParentCompleted(final I result, final Exception error) {
                if (error == null)
                    complete(result, action);
                else invoke(null, error);
            }
        };
    }

    /**
     * Attaches a new asynchronous computation to this state.
     * <p>
     * This method represents short version of {@link #then(java.util.function.Function, java.util.function.Function)}
     * method. If asynchronous computation represented by this object will fail then this method returns
     * the error without calling an error handler (because it is not presented).
     * </p>
     *
     * @param action The action implementing attached asynchronous computation if this computation
     *               is completed successfully. Cannot be {@literal null}.
     * @return The object that represents state of the attached asynchronous computation.
     */
    @Override
    public final <O> Promise<V, O> then(final Function<? super V, AsyncResult<O>> action) {
        return create(this, action);
    }

    static  <I, O> Promise<I, O> create(final AbstractTask<I> owner,
                                       final ThrowableFunction<? super I, ? extends O> action) {
        Objects.requireNonNull(action, "action is null.");
        return new Promise<I, O>(owner) {
            @Override
            protected void onParentCompleted(final I result, final Exception error) {
                if (error == null)
                    complete(result, action);
                else invoke(null, error);
            }
        };
    }

    /**
     * Schedules a new synchronous computation depends on completion of this computation.
     *
     * @param action The continuation action in synchronous style. Cannot be {@literal null}.
     * @return THe object that represents state of the chained computation.
     */
    @Override
    public final  <O> Promise<V, O> then(final ThrowableFunction<? super V, ? extends O> action) {
        return create(this, action);
    }

    static <I, O> Promise<I, O> create(final AbstractTask<I> owner,
                                    final Function<? super I, AsyncResult<O>> action,
                                   final Function<Exception, AsyncResult<O>> errorHandler) {
        Objects.requireNonNull(action, "action is null.");
        Objects.requireNonNull(errorHandler, "errorHandler is null.");
        //synchronization via AQS is not required
        return new Promise<I, O>(owner) {
            @Override
            protected void onParentCompleted(final I result, final Exception error) {
                if (error == null)
                    complete(result, action, errorHandler);
                else complete(error, errorHandler);
            }
        };
    }

    /**
     * Attaches a new asynchronous computation to this state.
     * <p>
     * Attached task will be executed after completion of this computation.
     * </p>
     *
     * @param action       The action implementing attached asynchronous computation if this computation
     *                     is completed successfully. Cannot be {@literal null}.
     * @param errorHandler The action implementing attached asynchronous computation if this computation
     *                     is failed. May be {@literal null}.
     * @return The object that represents state of the attached asynchronous computation.
     */
    @Override
    public final  <O> Promise<V, O> then(final Function<? super V, AsyncResult<O>> action,
                                   final Function<Exception, AsyncResult<O>> errorHandler) {
        return create(this, action, errorHandler);
    }

    static  <I, O> Promise<I, O> create(final AbstractTask<I> owner,
                                    final ThrowableFunction<? super I, ? extends O> action,
                                    final ThrowableFunction<Exception, ? extends O> errorHandler) {
        Objects.requireNonNull(action, "action is null.");
        Objects.requireNonNull(errorHandler, "errorHandler is null.");
        return new Promise<I, O>(owner) {
            @Override
            protected void onParentCompleted(final I result, final Exception error) {
                if (error == null)
                    complete(result, action, errorHandler);
                else complete(error, errorHandler);
            }
        };
    }

    /**
     * Attaches a new asynchronous computation to this state.
     *
     * @param action       The action implementing attached asynchronous computation if this computation
     *                     is completed successfully. Cannot be {@literal null}.
     * @param errorHandler The action implementing attached asynchronous computation if this computation
     *                     is failed. May be {@literal null}.
     * @return The object that represents state of the attached asynchronous computation.
     */
    @Override
    public final  <O> Promise<V, O> then(final ThrowableFunction<? super V, ? extends O> action,
                                   final ThrowableFunction<Exception, ? extends O> errorHandler) {
        return create(this, action, errorHandler);
    }
}
