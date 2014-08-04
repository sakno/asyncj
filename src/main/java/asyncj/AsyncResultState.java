package asyncj;

/**
 * Represents state of the asynchronous task.
 * @author Roman Sakno
 * @version 1.1
 * @since 1.0
 */
public enum AsyncResultState {
    /**
     * Task is created.
     */
    CREATED,

    /**
     * Task is cancelled
     */
    CANCELLED,

    /**
     * Task is successfully completed.
     */
    COMPLETED,

    /**
     * Task is unsuccessfully completed.
     */
    FAILED,

    /**
     * Task is executed
     */
    EXECUTED
}
