package asyncj;

import asyncj.AsyncResult;
import asyncj.AsyncUtils;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Test set for {@link asyncj.AsyncUtils} class.
 * @author Roman Sakno
 * @version 1.1
 * @since 1.0
 */
public final class AsyncUtilsTest extends Assert {
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
    public void flatMapReduceTest() throws InterruptedException, ExecutionException, TimeoutException{
        final Integer[] array = new Integer[]{1, 2, 3, 4, 5, 6 ,7 ,8 , 9, 10};
        final AsyncResult<String> result = AsyncUtils.flatMapReduce(AsyncUtils.getGlobalScheduler(),
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

    @Test
    public void getAllTest() throws ExecutionException, InterruptedException {
        final AsyncResult<Integer> a1 = AsyncUtils.successful(AsyncUtils.getGlobalScheduler(), 42);
        final AsyncResult<Integer> a2 = AsyncUtils.successful(AsyncUtils.getGlobalScheduler(), 43);
        final AsyncResult<Integer> a3 = AsyncUtils.successful(AsyncUtils.getGlobalScheduler(), 44);
        final Collection<Integer> result = AsyncUtils.getAll(a1, a2, a3);
        assertEquals(3, result.size());
        assertTrue(result.contains(42));
        assertTrue(result.contains(43));
        assertTrue(result.contains(44));
    }

    @Test(expected = ExecutionException.class)
    public void getAllTestWithFailure() throws ExecutionException, InterruptedException{
        final AsyncResult<Integer> a1 = AsyncUtils.successful(AsyncUtils.getGlobalScheduler(), 42);
        final AsyncResult<Integer> a2 = AsyncUtils.successful(AsyncUtils.getGlobalScheduler(), 43);
        final AsyncResult<Integer> a3 = AsyncUtils.failure(AsyncUtils.getGlobalScheduler(), new Exception());
        AsyncUtils.getAll(a1, a2, a3);
    }

    @Test
    public void sequenceTest() throws ExecutionException, InterruptedException {
        final AsyncResult<Integer> a1 = AsyncUtils.successful(AsyncUtils.getGlobalScheduler(), 42);
        final AsyncResult<Integer> a2 = AsyncUtils.successful(AsyncUtils.getGlobalScheduler(), 43);
        final AsyncResult<Integer> a3 = AsyncUtils.successful(AsyncUtils.getGlobalScheduler(), 44);
        final Iterable<Integer> seq = AsyncUtils.sequence(AsyncUtils.getGlobalScheduler(), a1, a2, a3).get();
        final Set<Integer> s = new HashSet<>(3);
        for(final Integer elem: seq)
            s.add(elem);
        assertEquals(3, s.size());
        assertTrue(s.contains(42));
        assertTrue(s.contains(43));
        assertTrue(s.contains(44));
    }
}
