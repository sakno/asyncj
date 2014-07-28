package org.asyncj;

import org.asyncj.impl.Task;
import org.asyncj.impl.TaskExecutor;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
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
        final TaskExecutor executor = TaskExecutor.newSingleThreadExecutor();
        final ArrayOperations obj = new ArrayOperations(executor);
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
                .then(ThrowableFunction.<String>identity(), Exception::getMessage).get(2, TimeUnit.SECONDS);
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
    public void longChainTest() throws InterruptedException, ExecutionException, TimeoutException {
        Task.enableAdvancedStringRepresentation();
        final TaskExecutor executor = TaskExecutor.newSingleThreadExecutor();
        final ArrayOperations obj = new ArrayOperations(executor);
        AsyncResult<Integer[]> ar = obj.reverseArray(new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13});
        for (int i = 0; i < 1000; i++) {
            ar = ar.then((Integer[] array) -> {
                for (int j = 0; j < array.length; j++)
                    array[j] += j;
                return array;
            });
        }
        final Integer[] result = ar.get(4, TimeUnit.SECONDS);
        assertNotNull(result);
        assertEquals(13, result.length);
    }

    @Test
    public void stressTest() throws InterruptedException, ExecutionException, TimeoutException {
        for(int i = 0; i < 30; i++) {
            final long currentNanos = System.nanoTime();
            longChainTest();
            System.out.println(System.nanoTime() - currentNanos);
        }
    }

    @Test
    public void simpleReduceTest() throws InterruptedException, ExecutionException, TimeoutException {
        final AsyncResult<Integer> ar1 = AsyncUtils.getGlobalScheduler().submit(() -> {
            Thread.sleep(100);
            return 10;
        });
        final AsyncResult<Byte> ar2 = AsyncUtils.getGlobalScheduler().submit(() -> {
            Thread.sleep(50);
            return (byte) 42;
        });
        final AsyncResult<Integer> result = AsyncUtils.reduce(ar1, ar2, (value1, value2) -> value2 - value1);
        assertEquals(32, (int) result.get(3, TimeUnit.SECONDS));
    }

    @Test
    public void mapReduceTest() throws InterruptedException, ExecutionException, TimeoutException{
        final Integer[] array = new Integer[]{1, 2, 3, 4, 5, 6 ,7 ,8 , 9, 10};
        final AsyncResult<String> result = AsyncUtils.mapReduce(AsyncUtils.getGlobalScheduler(),
                Arrays.asList(array).iterator(),
                (input, output)->output + input,
                "");
        assertEquals("12345678910", result.get(Duration.ofSeconds(5)));
    }

    @Test
    public void asyncMapReduceTest() throws InterruptedException, ExecutionException, TimeoutException{
        final Integer[] array = new Integer[]{1, 2, 3, 4, 5, 6 ,7 ,8 , 9, 10};
        final AsyncResult<String> result = AsyncUtils.mapReduceAsync(AsyncUtils.getGlobalScheduler(),
                Arrays.asList(array).iterator(),
                (input, output) -> AsyncUtils.successful(AsyncUtils.getGlobalScheduler(), output + input),
                "");
        assertEquals("12345678910", result.get(Duration.ofSeconds(20)));
    }

    @Test
    public void reduceMany() throws InterruptedException, ExecutionException, TimeoutException{
        final AsyncResult<String> ar1 = AsyncUtils.getGlobalScheduler().submit(() -> {
            Thread.sleep(100);
            return "Hello";
        });
        final AsyncResult<String> ar2 = AsyncUtils.getGlobalScheduler().submit(() -> {
            Thread.sleep(50);
            return ",";
        });
        final AsyncResult<String> ar3 = AsyncUtils.getGlobalScheduler().submit(() -> {
            Thread.sleep(100);
            return " ";
        });
        final AsyncResult<String> ar4 = AsyncUtils.getGlobalScheduler().submit(() -> {
            Thread.sleep(50);
            return "world!";
        });
        final AsyncResult<String> reduceResult = AsyncUtils.<String, String>reduce(AsyncUtils.getGlobalScheduler(), Arrays.asList(ar1, ar2, ar3, ar4).iterator(),  parts->{
            final StringBuilder result = new StringBuilder();
            parts.stream().forEach(result::append);
            return result.toString();
        });
        assertEquals("Hello, world!", reduceResult.get(Duration.ofSeconds(3)));
    }

    @Test
    public void untilTest() throws InterruptedException, ExecutionException, TimeoutException {
        final AsyncResult<Integer> result = AsyncUtils.until(AsyncUtils.getGlobalScheduler(),
                i -> i < 1000,  //condition
                i -> i * 2,     //loop body
                1);
        assertEquals(1024, (int) result.get(Duration.ofSeconds(20)));
    }
}
