package asyncj;

import java.util.function.Function;

/**
 * Represents alternative to {@link java.util.function.Function} functional interface that
 * supports exception.
 * @author Roman Sakno
 * @version 1.1
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

    /**
     * Converts an instance of {@link java.util.function.Function} into
     * {@code ThrowableFunction} instance.
     * @param func The function to convert. May be null.
     * @param <I> Type of the function argument.
     * @param <O> Type of the function result.
     * @return A new instance of the wrapped function.
     */
    static <I, O> ThrowableFunction<I, O> fromFunction(final Function<I, O> func) {
        return func != null ? func::apply : null;
    }
}
