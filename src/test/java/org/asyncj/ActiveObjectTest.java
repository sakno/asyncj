package org.asyncj;


import org.junit.Assert;
import org.junit.Test;

import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

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
    public void callbackTest() throws ExecutionException, InterruptedException{
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
        signaller.await(2, TimeUnit.SECONDS);
    }
}
