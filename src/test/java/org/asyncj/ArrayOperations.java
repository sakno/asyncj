package org.asyncj;

import org.asyncj.impl.TaskExecutor;

import java.lang.reflect.Array;

/**
 * Represents test active object.
 */
final class ArrayOperations extends ActiveObject {
    public ArrayOperations() {
        super(TaskExecutor.newSingleThreadExecutor());
    }

    @SuppressWarnings("unchecked")
    private <T> T[] reverseArraySync(final T[] array){
        if(array == null) throw new NullPointerException("array is null.");
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
