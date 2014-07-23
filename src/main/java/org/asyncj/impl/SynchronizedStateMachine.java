package org.asyncj.impl;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.function.IntFunction;

/**
 * Represents a base class for constructing state machines with
 * thread-safe transitions.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
abstract class SynchronizedStateMachine extends AbstractQueuedSynchronizer {
    /**
     * Initializes a new synchronized state machine.
     * @param initialState The initial state.
     */
    protected SynchronizedStateMachine(final int initialState){
        setState(initialState);
    }

    /**
     * Attempts to acquire exclusive access to the fields of this task
     * or blocks the current thread until the task will not be transited to the specified state.
     * @param expectedState The state of the task expected by the caller code.
     * @return {@literal true} if this task is in requested state; otherwise, {@literal false}, and
     * caller thread join into waiting loop.
     */
    @Override
    protected final boolean tryAcquire(final int expectedState) {
        final Thread currentThread = Thread.currentThread(), ownerThread = getExclusiveOwnerThread();
        if((expectedState & getState()) == 0) return false;
        else if(currentThread == ownerThread || ownerThread == null){
            setExclusiveOwnerThread(currentThread);
            return true;
        }
        else return false;
    }

    /**
     * Validates state machine transition.
     * @param from The source state of the task.
     * @param to The sink state of the task.
     * @return {@literal true}, if the specified transition is valid; otherwise, {@literal false}.
     */
    protected abstract boolean isValidTransition(final int from, final int to);

    /**
     * Releases exclusive lock to the fields of this task and assigns
     * a new task state.
     * @param newState A new task state.
     * @return {@literal true}, if the specified state is supported by transitive; otherwise, {@literal false}.
     * @throws java.lang.IllegalMonitorStateException Invalid state transition.
     */
    @Override
    protected final boolean tryRelease(final int newState) throws IllegalMonitorStateException {
        final Thread blocker = getExclusiveOwnerThread();
        if (Thread.currentThread() == blocker || blocker == null) {
            int currentState;
            do {
                currentState = getState();
                //checks transition from old state to new state
                if (!isValidTransition(currentState, newState))
                    throw new IllegalMonitorStateException(String.format("Invalid state transition: %s -> %s", currentState, newState));
            }
            while (!compareAndSetState(currentState, newState)); //set new task state
            setExclusiveOwnerThread(null);
            return true;
        } else return false;
    }

    /**
     * Attempts to acquire non-exclusive lock for read-only access to the
     * fields of this task.
     * @param expectedState The state of the task expected by the caller code.
     * @return {@code 1}, if read lock is acquired successfully; otherwise, {@code -1},
     * and caller thread join into waiting loop.
     */
    @Override
    protected final int tryAcquireShared(final int expectedState) {
        //checks whether this task has exclusive lock
        final Thread currentThread = Thread.currentThread(), blocker = getExclusiveOwnerThread();
        if (currentThread == blocker || blocker == null)
            return (expectedState & getState()) == 0 ? -1 : 1;
        else return -1;
    }

    /**
     * Releases non-exclusive lock for read-only access to the fields of
     * this task and assigns
     * a new task state.
     * @param newState A new task state.
     * @return {@literal true}, if the specified state is supported by transitive; otherwise, {@literal false}.
     * @throws java.lang.IllegalMonitorStateException Invalid state transition.
     */
    @Override
    protected final boolean tryReleaseShared(final int newState) throws IllegalMonitorStateException{
        final Thread blocker = getExclusiveOwnerThread();
        if (Thread.currentThread() == blocker || blocker == null) {
            int currentState;
            do {
                currentState = getState();
                //checks transition from old state to new state
                if (!isValidTransition(currentState, newState))
                    throw new IllegalMonitorStateException(String.format("Invalid state transition: %s -> %s", currentState, newState));
            }
            while (!compareAndSetState(currentState, newState)); //set new task state
            return true;
        } else return false;
    }

    protected final <T> T writeOnTransition(final int expectedState, final IntFunction<T> transition){
        acquire(expectedState);
        final int currentState = getState();
        try{
            return transition.apply(currentState);
        }
        finally {
            release(currentState);
        }
    }

    protected final <T> T writeOnTransition(final int expectedState, final int newState, final IntFunction<T> transition){
        acquire(expectedState);
        try{
            return transition.apply(getState());
        }
        finally {
            release(newState);
        }
    }

    protected final <T> T readOnTransition(final int expectedState, final IntFunction<T> transition){
        acquireShared(expectedState);
        final int currentState = getState();
        try{
            return transition.apply(currentState);
        }
        finally {
            releaseShared(currentState);
        }
    }

    protected final <T> T readOnTransition(final int expectedState, final int newState, final IntFunction<T> transition){
        acquireShared(expectedState);
        try{
            return transition.apply(getState());
        }
        finally {
            tryReleaseShared(newState);
        }
    }

    protected final int acquireAndGetState(final int expectedState){
        acquire(expectedState);
        return getState();
    }
}
