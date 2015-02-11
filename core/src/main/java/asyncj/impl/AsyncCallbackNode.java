package asyncj.impl;

import asyncj.AsyncCallback;

import java.util.Objects;

/**
* @author Roman Sakno
* @version 1.0
* @since 1.0
*/
final class AsyncCallbackNode<O> {
    private AsyncCallbackNode<O> next;
    private final AsyncCallback<? super O> callback;

    AsyncCallbackNode(final AsyncCallback<? super O> callback) {
        this.callback = Objects.requireNonNull(callback);
        this.next = null;
    }

    AsyncCallbackNode<O> setNext(final AsyncCallback<? super O> callback) {
        return next = new AsyncCallbackNode<>(callback);
    }

    AsyncCallback<? super O> getCallback(){
        return callback;
    }

    AsyncCallbackNode<O> getNext(){
        return next;
    }
}
