package org.asyncj;

import org.asyncj.impl.SingleThreadScheduler;

import java.lang.reflect.Array;

/**
 * Represents test active object.
 */
final class ArrayOperations extends ActiveObject {
    public ArrayOperations(){
        super(new SingleThreadScheduler());
    }

    private <T> T[] reverseArraySync(final T[] array){
        final T[] result = (T[])Array.newInstance(array.getClass().getComponentType(), array.length);
        for(int i = 0; i < array.length; i++)
            result[i] = array[array.length - i - 1];
        return result;
    }

    public <T> AsyncResult<T[]> reverseArray(final T[] array){
        return enqueue(()->reverseArraySync(array));
    }

    public <T> void reverseArray(final T[] array, final AsyncCallback<T[]> callback){
        reverseArray(array).onCompleted(callback);
    }
}
