package org.asyncj;


import org.asyncj.impl.BooleanLatch;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    public void continuationTest() throws ExecutionException, InterruptedException{
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
        final Boolean[] result = even.get();
        assertEquals(3, result.length);
        assertFalse(result[0]);
        assertTrue(result[1]);
        assertFalse(result[0]);
    }

    @Test
    public void callbackTest() throws ExecutionException, InterruptedException, TimeoutException {
        final ArrayOperations obj = new ArrayOperations();
        final BooleanLatch signaller = new BooleanLatch();
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
                signaller.signal();
            });
        });
        signaller.await(2, TimeUnit.SECONDS);
    }

    @Test
    public void exceptionHandlingTest() throws TimeoutException, InterruptedException, ExecutionException {
        final ArrayOperations obj = new ArrayOperations();
        final Integer[] arr = null;
        final BooleanLatch latch = new BooleanLatch();
        obj.reverseArray(arr, (a, e)->{
            assertNull(a);
            assertNotNull(e);
            assertTrue(e instanceof NullPointerException);
            latch.signal();
        });
        latch.await(2, TimeUnit.SECONDS);
        final String errorMsg =
                obj.reverseArray(arr).then((Integer[] a)->"Not null", Exception::getMessage).get(2, TimeUnit.SECONDS);
        assertNotNull(errorMsg);
        assertFalse(errorMsg.isEmpty());
        assertNotEquals("Not null", errorMsg);
    }

    @Test
    public void exceptionChainableHandlingTest() throws InterruptedException, ExecutionException, TimeoutException {
        final ArrayOperations obj = new ArrayOperations();
        final Integer[] arr = null;
        final String errorMsg = obj.reverseArray(arr)
                .then((Integer[] a)->"Not null")
                .then(ThrowableFunction.<String>identity(), Exception::getMessage).get(2, TimeUnit.SECONDS);
        assertNotNull(errorMsg);
        assertFalse(errorMsg.isEmpty());
        assertNotEquals("Not null", errorMsg);
    }
}
