package org.asyncj.impl;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Represents a base class for constructing state machines with
 * thread-safe transitions.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
abstract class SynchronizedStateMachine {
    /**
     * Represents linked list node to hold the waiting threads in Treiber stack.
     */
    private static final class WaitNode{
        private final Reference<Thread> waitThread;
        private WaitNode nextNode;

        WaitNode(final Thread wt, final WaitNode nextNode){
            this.waitThread = new WeakReference<>(wt);
            this.nextNode = nextNode;
        }

        WaitNode(final Thread wt){
            this(wt, null);
        }

        WaitNode() {
            this(Thread.currentThread());
        }

        //GC helper method
        void clear(){
            waitThread.clear();
            nextNode = null;
        }
    }

    private volatile int state;
    private volatile WaitNode waiters;

    protected SynchronizedStateMachine(final int initialState){
        this.state = initialState;
        waiters = null;
    }

    /**
     * Gets internal state of this object.
     * @return The state of this object.
     */
    protected final int getState(){
        return state;
    }

    /**
     * Invokes by this state machine to inform about final state.
     * @param finalState The final state.
     */
    protected abstract void done(final int finalState);
}
