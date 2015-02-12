package asyncj;

import asyncj.impl.Task;
import asyncj.impl.TaskExecutor;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.*;

public final class ActiveObjectTest extends Assert {

    @Test
    public void singleAsyncCallTest() throws ExecutionException, InterruptedException {
        final ArrayOperations obj = new ArrayOperations();
        final AsyncResult<Integer[]> result1 = obj.reverseArray(new Integer[]{1, 2, 3});
        final AsyncResult<Boolean[]> result2 = obj.reverseArray(new Boolean[]{false, true});
        assertArrayEquals(new Integer[]{3, 2, 1}, result1.get());
        assertArrayEquals(new Boolean[]{true, false}, result2.get());
    }

    @Test
    public void continuationTest() throws ExecutionException, InterruptedException, TimeoutException{
        final ArrayOperations obj = new ArrayOperations();
        final AsyncResult<Boolean[]> even = obj.reverseArray(new Integer[]{1, 2, 3}).then((Integer[] array)->{
            for(int i = 0; i < array.length; i++)
                array[i] = array[i] + 2;
            return array;
        }).then((Integer[] array)->{
           final Boolean[] result = new Boolean[array.length];
            for(int i = 0; i < array.length; i++)
                result[i] = array[i] % 2 == 0;
            return result;
        });
        final Boolean[] result = even.get(Duration.ofSeconds(3));
        assertEquals(3, result.length);
        assertFalse(result[0]);
        assertTrue(result[1]);
        assertFalse(result[0]);
    }

    @Test
    public void asyncContinuationTest() throws ExecutionException, InterruptedException, TimeoutException {
        final AsyncResult<Integer[]> array = AsyncUtils.getGlobalScheduler()
                .submit(() -> new Integer[5])
                .then((Integer[] a) -> {
                    for (int i = 0; i < a.length; i++) a[i] = i;
                    return AsyncUtils.successful(AsyncUtils.getGlobalScheduler(), a);
                });
        final Integer[] result = array.get(Duration.ofSeconds(3));
        assertArrayEquals(new Integer[]{0, 1, 2, 3, 4}, result);
    }

    @Test
    public void callbackTest() throws ExecutionException, InterruptedException, TimeoutException {
        final ArrayOperations obj = new ArrayOperations();
        final CountDownLatch signaller = new CountDownLatch(1);
        obj.reverseArray(new Integer[]{1, 2, 3}, (array1, err1)->{
            assertNull(err1);
            assertNotNull(array1);
            for(int i = 0; i < array1.length; i++)
                array1[i] = array1[i] + 2;
            obj.reverseArray(array1, (array2, err2)->{
                final Boolean[] result = new Boolean[array2.length];
                for(int i = 0; i < array2.length; i++)
                    result[i] = array2[i] % 2 == 0;
                assertEquals(3, result.length);
                assertFalse(result[0]);
                assertTrue(result[1]);
                assertFalse(result[0]);
                signaller.countDown();
            });
        });
        assertTrue(signaller.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void exceptionHandlingTest() throws TimeoutException, InterruptedException, ExecutionException {
        final ArrayOperations obj = new ArrayOperations();
        final Integer[] arr = null;
        final CountDownLatch latch = new CountDownLatch(1);
        obj.reverseArray(arr, (a, e)->{
            assertNull(a);
            assertNotNull(e);
            assertTrue(e instanceof NullPointerException);
            latch.countDown();
        });
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        final String errorMsg =
                obj.reverseArray(arr).then((Integer[] a)->"Not null", Exception::getMessage)
                        .get(2, TimeUnit.SECONDS);
        assertNotNull(errorMsg);
        assertFalse(errorMsg.isEmpty());
        assertNotEquals("Not null", errorMsg);
    }

    @Test
    public void exceptionChainHandlingTest() throws InterruptedException, ExecutionException, TimeoutException {
        final ArrayOperations obj = new ArrayOperations();
        final Integer[] arr = null;
        final String errorMsg = obj.reverseArray(arr)
                .then((Integer[] a)->"Not null")
                .then(ThrowableFunction.<String>identity(), Exception::getMessage).get(4, TimeUnit.SECONDS);
        assertNotNull(errorMsg);
        assertFalse(errorMsg.isEmpty());
        assertNotEquals("Not null", errorMsg);
    }

    @Test(expected = CancellationException.class)
    public void cancellationTest() throws InterruptedException, ExecutionException, TimeoutException {
        final ArrayOperations obj = new ArrayOperations();
        final Integer[] arr = new Integer[100];
        for(int i = 0; i< arr.length; i++)
            arr[i] = i + 10;
        final AsyncResult<Integer[]> ar = obj.reverseArrayLongTime(arr);
        Thread.sleep(300);//task must be executed inside of the thread, or cancel request will return false
        assertTrue(ar.cancel(true));
        final AsyncResult<Integer[]> a = ar.then(ThrowableFunction.<Integer[]>identity());
        a.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void pipeliningTest() throws InterruptedException, ExecutionException, TimeoutException {
        Task.enableAdvancedStringRepresentation();
        final TaskScheduler scheduler = TaskExecutor.newDefaultThreadExecutor();
        final ArrayOperations obj = new ArrayOperations(scheduler);
        AsyncResult<Integer[]> ar = obj.reverseArray(new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13});
        for (int i = 0; i < 100; i++) {
            ar = ar.then((Integer[] array) -> {
                for (int j = 0; j < array.length; j++)
                    array[j] += j;
                return array;
            });
        }
        final Integer[] result = ar.get(10, TimeUnit.SECONDS);
        assertTrue(scheduler.shutdownNow().isEmpty());
        assertNotNull(result);
        assertEquals(13, result.length);
    }

    @Test
    public void stressTest() throws InterruptedException, ExecutionException, TimeoutException {
        final int REPEAT_COUNT = 10;
        long averageTime = 0;
        for(int i = 0; i < REPEAT_COUNT; i++) {
            final long currentNanos = System.nanoTime();
            pipeliningTest();
            averageTime += System.nanoTime() - currentNanos;
        }
        averageTime /= REPEAT_COUNT;
        System.out.println("Average pipelining time: " + averageTime + " nanos");
    }
}
