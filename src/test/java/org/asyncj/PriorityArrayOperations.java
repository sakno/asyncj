package org.asyncj;

import org.asyncj.impl.PriorityTaskExecutor;
import org.asyncj.impl.TaskExecutor;

import java.lang.reflect.Array;
import java.util.function.IntSupplier;

/**
 * Created by rvsakno on 18.07.2014.
 */
public class PriorityArrayOperations extends ActiveObject {

    private static enum Priority implements IntSupplier{
        LOW(0),
        NORMAL(1),
        HIGH(2);

        private final int p;

        private Priority(final int priorityNumber){
            p = priorityNumber;
        }


        @Override
        public int getAsInt() {
            return p;
        }
    }

    public PriorityArrayOperations() {
        super(PriorityTaskExecutor.createOptimalExecutor(Priority.class, Priority.NORMAL));
    }

    @SuppressWarnings("unchecked")
    private <T> T[] reverseArraySync(final T[] array){
        final T[] result = (T[]) Array.newInstance(array.getClass().getComponentType(), array.length);
        for(int i = 0; i < array.length; i++)
            result[i] = array[array.length - i - 1];
        return result;
    }

    public <T> AsyncResult<T[]> reverseArray(final T[] array){
        return enqueue(()->reverseArraySync(array), Priority.HIGH);
    }

    public <T> void reverseArray(final T[] array, final AsyncCallback<T[]> callback){
        reverseArray(array).onCompleted(callback);
    }
}
