package org.asyncj.impl;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Represents boolean latch used for primitive signal-based synchronization.
 * This class cannot be inherited.
 * @author Roman Sakno
 * @since 1.0
 * @version 1.0
 */
public final class BooleanLatch extends AbstractQueuedSynchronizer {

    /**
     * Initializes a new boolean latch in non-signalled state.
     */
    public BooleanLatch(){
        setState(0);
    }

    /**
     * Sets signalled state to this object.
     */
    public final void signal(){
        releaseShared(1);
    }

    /**
     * Gets state of this latch.
     * @return {@literal true}, if this object is in signalled state; otherwise, {@literal false}.
     */
    public final boolean isSignalled(){
        return getState() != 0;
    }

    /**
     * Awaits for signalled state.
     * @throws InterruptedException The awaiting thread is interrupted.
     */
    public final void await() throws InterruptedException{
        acquireSharedInterruptibly(1);
    }


    /**
     * Awaits for signalled state.
     * @param timeout Waiting timeout.
     * @param unit Timeout measurement unit.
     * @throws InterruptedException The awaiting thread ins interrupted.
     * @throws TimeoutException The signal was not received
     */
    public final void await(final long timeout, final TimeUnit unit) throws InterruptedException, TimeoutException{
        if(!tryAcquireSharedNanos(1, unit.toNanos(timeout)))
            throw new TimeoutException();
    }

    @Override
    protected final int tryAcquireShared(int arg) {
        return getState() == 0 ? -1 : 1;
    }

    @Override
    protected final boolean tryReleaseShared(int arg) {
        setState(1);
        return true;
    }
}
