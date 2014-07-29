/**
 * This package contains abstract and default implementations of
 * AsyncJ architectural components, such as task scheduler and asynchronous task.
 * <p> This package provides the following default implementations of task schedulers:
 * <ul>
 *     <li>{@link asyncj.impl.TaskExecutor} - flexible implementation of task
 *     scheduler without priority support.</li>
 *     <li>{@link asyncj.impl.PriorityTaskExecutor} - flexible implementation
 *     of task scheduler with priority support.</li>
 * </ul>
 * Both task executors supports bridging with {@link java.util.concurrent.ExecutorService} interface.
 *
 * @author Roman Sakno
 * @since 1.0
 * @version 1.0
 * @see asyncj.impl.AbstractTaskScheduler
 * @see asyncj.impl.AbstractPriorityTaskScheduler
 * @see asyncj.impl.Task
 */
package asyncj.impl;