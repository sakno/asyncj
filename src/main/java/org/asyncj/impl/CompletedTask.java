package org.asyncj.impl;

import org.asyncj.TaskScheduler;

import java.util.concurrent.CancellationException;

/**
 * Represents completed task. This class cannot be inherited.
 * @param <V> Type of the asynchronous computation result.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public abstract class CompletedTask<V> extends Task<V> {
    private CompletedTask(final TaskScheduler scheduler){
        super(scheduler);
    }

    /**
     * Creates a new asynchronous task which returns the specified value.
     * @param scheduler Task scheduler used to enqueue the trivial task. Cannot be {@literal null}.
     * @param value The value exposed as asynchronous computation.
     * @param <V> Type of the asynchronous result.
     * @return A new instance of the trivial task.
     */
    public static <V> CompletedTask<V> successful(final TaskScheduler scheduler, final V value){
        return new CompletedTask<V>(scheduler){
            @Override
            public V call() throws Exception {
                return value;
            }
        };
    }

    public static <V> CompletedTask<V> failure(final TaskScheduler scheduler, final Exception err){
        return new CompletedTask<V>(scheduler) {
            @Override
            public V call() throws Exception {
                throw err;
            }
        };
    }

    public static <V> CompletedTask<V> cancelled(final TaskScheduler scheduler, final String reason){
        return failure(scheduler, new CancellationException(reason));
    }
}
