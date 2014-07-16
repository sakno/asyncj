package org.asyncj.impl;

import org.asyncj.AsyncResult;

import java.util.concurrent.RunnableFuture;

/**
 * Represents traceable async result which supports debugging information.
 * <p>
 *     You should not implement this interface directly in your code.
 * </p>
 * @author Roman Sakno
 * @since 1.0
 * @version 1.0
 */
public interface TraceableAsyncResult<O> extends AsyncResult<O> {
    /**
     * Represents unique identifier assigned by scheduler for this task.
     * @return The unique identifier of this task.
     */
    long getID();

    /**
     * Determines whether the current asynchronous result is just wrapper for underlying asynchronous result.
     * @return {@literal true}, if this asynchronous result is a wrapper for underlying asynchronous result.
     */
    boolean isProxy();

    /**
     * Sets the user defined marker for this object.
     * @param marker The user defined marker for this object.
     */
    void setMarker(final Object marker);

    /**
     * Gets the user defined marker associated with this object.
     * @return The user defined marker associated with this object.
     */
    Object getMarker();
}
