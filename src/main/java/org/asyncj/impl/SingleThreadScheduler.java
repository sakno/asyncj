package org.asyncj.impl;

import org.asyncj.AsyncResult;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RunnableFuture;

/**
 * Represents a scheduler that executes all tasks in the single background thread.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public final class SingleThreadScheduler extends AbstractTaskScheduler {

    private static final class TaskExecutionThread extends Thread{
        private final Queue<RunnableFuture<?>> tasks;
        private volatile RunnableFuture<?> executingTask;

        public TaskExecutionThread(final ThreadGroup tg, final Queue<RunnableFuture<?>> tasks){
            super(tg, (Runnable)null);
            this.tasks = tasks;
            this.executingTask = null;
        }

        @Override
        public void run() {
            while (!tasks.isEmpty()){
                executingTask = tasks.poll();
                executingTask.run();
                if(isInterrupted()) return;
            }
        }

        public boolean interrupt(final AsyncResult<?> ar){
            if(ar == executingTask){
                interrupt();
                return true;
            }
            else return false;
        }
    }

    private final Queue<RunnableFuture<?>> tasks;
    private final ThreadGroup group;
    private volatile TaskExecutionThread executionThread;
    private final Object mutex;

    public SingleThreadScheduler(final ThreadGroup tg){
        group = tg != null ? tg : new ThreadGroup(String.format("Single threaded task scheduler %s", hashCode()));
        tasks = new ConcurrentLinkedQueue<>();
        executionThread = null;
        mutex = new Object();
    }

    public SingleThreadScheduler(){
        this(null);
    }

    @Override
    protected <V, T extends AsyncResult<V> & RunnableFuture<V>> AsyncResult<V> enqueueTask(final T task) {
        synchronized (mutex) {
            if (executionThread == null || !executionThread.isAlive()) {
                executionThread = new TaskExecutionThread(group, tasks);
                executionThread.start();
            }
        }
        tasks.offer(task);
        return task;
    }

    /**
     * Interrupts thread associated with the specified asynchronous computation.
     * <p>
     * This is infrastructure method and you should not use it directly from your code.
     * </p>
     *
     * @param ar The asynchronous computation to interrupt.
     * @return {@literal true}, if the specified asynchronous computation is interrupted; otherwise, {@literal false}.
     */
    @Override
    public boolean interrupt(final AsyncResult<?> ar) {
        synchronized (mutex) {
            if (executionThread == null || !executionThread.isAlive())
                return false;
            else return executionThread.interrupt(ar);
        }
    }
}
