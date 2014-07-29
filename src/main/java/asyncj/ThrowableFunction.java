package asyncj;

import java.util.function.Supplier;

/**
 * Represents alternative to {@link java.util.function.Function} functional interface that
 * supports exception.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public interface ThrowableFunction<I, O> {
    /**
     * Applies this function to the given argument.
     *
     * @param value the function argument
     * @return the function result
     * @throws java.lang.Exception Some exception occurred in the function.
     */
    O apply(final I value) throws Exception;

    static <I> ThrowableFunction<I, I> identity(){
        return i->i;
    }

    static <I, O> ThrowableFunction<I, O> throwException(final Supplier<? extends Exception> exceptionFactory){
        return i-> {
            throw exceptionFactory.get();
        };
    }
}
