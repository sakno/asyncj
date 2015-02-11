package asyncj.impl;

import asyncj.AsyncCallback;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
* @author Roman Sakno
* @version 1.0
* @since 1.0
*/ //represents buffer of callbacks
final class AsyncCallbackChain<O> implements Iterable<AsyncCallback<? super O>> {
    private static final class AsyncCallbackIterator<O> implements Iterator<AsyncCallback<? super O>>{
        private AsyncCallbackNode<O> current;

        private AsyncCallbackIterator(final AsyncCallbackNode<O> first){
            this.current = first;
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public AsyncCallback<? super O> next() {
            final AsyncCallbackNode<O> current = this.current;
            if(current == null) throw new NoSuchElementException();
            this.current = current.getNext();
            return current.getCallback();
        }
    }

    private final AsyncCallbackNode<O> first;
    private final AsyncCallbackNode<O> tail;

    AsyncCallbackChain(){
        this(null);
    }

    AsyncCallbackChain(final AsyncCallbackNode<O> first){
        this.first = this.tail = first;
    }

    AsyncCallbackChain(final AsyncCallbackChain<O> previous,
                       final AsyncCallback<? super O> next){
        this.first = Objects.requireNonNull(previous).first;
        this.tail = previous.tail.setNext(next);
    }

    AsyncCallbackChain<O> put(final AsyncCallback<? super O> callback) {
        return first == null || tail == null ?
                new AsyncCallbackChain<>(new AsyncCallbackNode<>(callback)) :
                new AsyncCallbackChain<>(this, callback);
    }

    /**
     * Returns an iterator over elements of type {@code T}.
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<AsyncCallback<? super O>> iterator() {
        return new AsyncCallbackIterator<>(first);
    }
}
