package org.asyncj.impl;

import org.asyncj.TaskScheduler;

import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Represents completed task. This class cannot be inherited.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public abstract class CompletedTask<V> extends Task<V> {
    private CompletedTask(final TaskScheduler scheduler){
        super(scheduler);
    }

    public static <V> CompletedTask<V> successful(final TaskScheduler scheduler, final V value){
        return new CompletedTask<V>(scheduler){

            @Override
            public V call() throws Exception {
                return value;
            }
        };
    }

    public static <V> CompletedTask<V> failure(final TaskScheduler scheduler, final Exception err){
        Objects.requireNonNull(err, "err is null.");
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
