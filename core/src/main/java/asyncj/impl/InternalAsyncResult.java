package asyncj.impl;

import asyncj.AsyncResult;
import asyncj.TaskScheduler;

/**
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
interface InternalAsyncResult<V> extends AsyncResult<V> {
    /**
     * Determines whether this task is scheduled by the specified scheduler.
     * @param scheduler The scheduler to check.
     * @return {@literal true}, if this task is scheduled by the specified scheduler; otherwise, {@literal false}.
     */
    boolean isScheduledBy(final TaskScheduler scheduler);

    /**
     * Gets ID of this task.
     * @return ID of this task.
     */
    long getID();
}
