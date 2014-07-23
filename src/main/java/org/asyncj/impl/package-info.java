/**
 * This package contains abstract and default implementations of
 * AsyncJ architectural components, such as task scheduler and asynchronous task.
 * <p> This package provides the following default implementations of task schedulers:
 * <ul>
 *     <li>{@link org.asyncj.impl.TaskExecutor} - flexible implementation of task
 *     scheduler without priority support.</li>
 *     <li>{@link org.asyncj.impl.PriorityTaskExecutor} - flexible implementation
 *     of task scheduler with priority support.</li>
 * </ul>
 * Both task executors supports bridging with {@link java.util.concurrent.ExecutorService} interface.
 *
 * @author Roman Sakno
 * @since 1.0
 * @version 1.0
 * @see org.asyncj.impl.AbstractTaskScheduler
 * @see org.asyncj.impl.AbstractPriorityTaskScheduler
 * @see org.asyncj.impl.Task
 */
package org.asyncj.impl;