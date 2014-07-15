package org.asyncj;


import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

public final class ActiveObjectsTest extends Assert {

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
        assertTrue(result[0]);
        assertFalse(result[1]);
        assertTrue(result[0]);
    }
}
