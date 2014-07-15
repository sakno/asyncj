package org.asyncj;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Represents active object which methods may be invoked asynchronously using its internal dedicated task scheduler.
 */
public abstract class ActiveObject {
    private final TaskScheduler scheduler;

    protected ActiveObject(final TaskScheduler scheduler){
        Objects.requireNonNull(scheduler, "scheduler is null.");
        this.scheduler = scheduler;
    }

    protected final <O> AsyncResult<O> successful(final O value){
        return scheduler.successful(value);
    }

    protected final <O> AsyncResult<O> failure(final Exception err){
        return scheduler.failure(err);
    }

    protected final <O> AsyncResult<O> enqueue(final Callable<O> task){
        return scheduler.enqueue(task);
    }

    protected final <I> void enqueue(final AsyncCallback<? super I> callback, final I value){
        Objects.requireNonNull(callback, "callback is null.");
        scheduler.enqueue(()->{
            callback.invoke(value, null);
            return null;
        });
    }

    protected final <I> void enqueue(final AsyncCallback<? super I> callback, final Exception err){
        Objects.requireNonNull(callback, "callback is null.");
        scheduler.enqueue(()->{
           callback.invoke(null, err);
            return null;
        });
    }
}
