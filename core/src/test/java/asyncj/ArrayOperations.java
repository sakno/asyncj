package asyncj;

import asyncj.ActiveObject;
import asyncj.AsyncCallback;
import asyncj.AsyncResult;
import asyncj.impl.TaskExecutor;

import java.lang.reflect.Array;

/**
 * Represents test active object.
 */
final class ArrayOperations extends ActiveObject {
    public ArrayOperations() {
        this(TaskExecutor.newSingleThreadExecutor());
    }

    public ArrayOperations(final TaskExecutor executor){
        super(executor);
    }

    @SuppressWarnings("unchecked")
    private <T> T[] reverseArraySync(final T[] array){
        if(array == null) throw new NullPointerException("array is null.");
        final T[] result = (T[])Array.newInstance(array.getClass().getComponentType(), array.length);
        for(int i = 0; i < array.length; i++)
            result[i] = array[array.length - i - 1];
        return result;
    }

    private <T> T[] reverseArraySyncLongTime(final T[] array) throws InterruptedException{
        if(array == null) throw new NullPointerException("array is null.");
        final T[] result = (T[])Array.newInstance(array.getClass().getComponentType(), array.length);
        for(int i = 0; i < array.length; i++) {
            result[i] = array[array.length - i - 1];
            Thread.sleep(500);
        }
        return result;
    }

    public <T> AsyncResult<T[]> reverseArray(final T[] array){
        return submit(() -> reverseArraySync(array));
    }

    public <T> void reverseArray(final T[] array, final AsyncCallback<T[]> callback){
        reverseArray(array).onCompleted(callback);
    }

    public <T> AsyncResult<T[]> reverseArrayLongTime(final T[] array) {
        return submit(() -> reverseArraySyncLongTime(array));
    }

    public <T> void reverseArrayLongTime(final T[] array, final AsyncCallback<T[]> callback) {
        reverseArrayLongTime(array).onCompleted(callback);
    }
}
