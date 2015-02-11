package asyncj.impl;

import asyncj.AsyncCallback;
import asyncj.AsyncResult;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
final class AsyncCallbackList<O> extends AtomicReference<AsyncCallbackChain<O>> {
    private static final AsyncCallbackChain EMPTY_CHAIN = new AsyncCallbackChain();

    AsyncCallbackList() {
        super(getEmptyChain());
    }

    @SuppressWarnings("unchecked")
    private static <O> AsyncCallbackChain<O> getEmptyChain() {
        return EMPTY_CHAIN;
    }

    void add(final AsyncCallback<? super O> callback) {
        updateAndGet(chain -> chain.put(callback));
    }

    Iterable<AsyncCallback<? super O>> dumpCallbacks() {
        return getAndSet(getEmptyChain());
    }

    void attachTo(final AsyncResult<O> ar){
        for(final AsyncCallback<? super O> callback: dumpCallbacks())
            ar.onCompleted(callback);
    }

    boolean isEmpty() {
        return EMPTY_CHAIN == get();
    }
}
