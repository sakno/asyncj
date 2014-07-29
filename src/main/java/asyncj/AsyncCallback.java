package asyncj;

/**
 * Represents asynchronous callback that can be called by asynchronous task on its completion.
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
@FunctionalInterface
public interface AsyncCallback<I> {
    /**
     * Informs this object that the asynchronous computation is completed.
     * @param input The result of the asynchronous computation.
     * @param error The error occurred during asynchronous computation.
     */
    void invoke(final I input, final Exception error);
}
