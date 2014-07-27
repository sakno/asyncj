package org.asyncj;

import org.asyncj.impl.PriorityTaskExecutor;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.IntSupplier;

/**
 * Represents a set of tests for asynchronous priority-based tests.
 */
public final class PriorityTest extends Assert {

    private static enum Priority implements IntSupplier {
        HIGHEST(4),
        HIGH(3),
        NORMAL(2),
        LOW(1),
        LOWEST(0);

        private final int priorityNum;

        private Priority(final int pn) {
            priorityNum = pn;
        }

        public int getAsInt() {
            return priorityNum;
        }
    }

    @Test
    public void simplePriorityTest() throws InterruptedException, ExecutionException, TimeoutException {
        final PriorityTaskScheduler scheduler = PriorityTaskExecutor.createOptimalExecutor(Priority.NORMAL);
        final Callable<Integer> normPriv = ()->{
            Thread.sleep(100);
            return 10;
        };
        final Callable<Integer> highPriv = ()->{
            Thread.sleep(100);
            return 42;
        };
        scheduler.submit(normPriv);
        scheduler.submit(normPriv);
        scheduler.submit(normPriv);
        scheduler.submit(normPriv);
        final AsyncResult<Integer> normResult = scheduler.submit(normPriv);
        scheduler.submit(AsyncUtils.prioritize(highPriv, Priority.HIGHEST));
        final AsyncResult<Integer> highResult = scheduler.submit(AsyncUtils.prioritize(highPriv, Priority.HIGHEST));
        //the task with highest priority must be completed before the task with normal priority ignoring scheduling order
        assertEquals(42, (int)highResult.get(Duration.ofSeconds(1000)));
        assertFalse(normResult.isDone());
    }
}
