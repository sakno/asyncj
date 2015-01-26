/**
 * Contains basic interfaces and classes for writing asynchronous applications.
 * <p>
 *     The architecture of AsyncJ library consists of the following parts:
 *     <ul>
 *         <li>{@link asyncj.ActiveObject} is an abstract implementation of Active Object pattern
 *         and contains all necessary things to write your own async-enabled custom classes.</li>
 *         <li>{@link asyncj.AbstractAsyncResourceAccess} helps to convert existing POJO into active objects.
 *         This is the most preferred way to enable asynchronous computation for existing classes.</li>
 *         <li>{@link asyncj.TaskScheduler} uses for scheduling and executing asynchronous computation. In most
 *         cases, you may not operate with this classes directly from your code.</li>
 *         <li>{@link asyncj.PriorityTaskScheduler} uses for scheduling and execution asynchronous computation
 *         based on task priority. In most
 *         cases, you may not operate with this classes directly from your code.</li>
 *     </ul>
 * @see asyncj.ActiveObject
 * @see asyncj.AsyncResourceAccess
 * @see asyncj.TaskScheduler
 * @see asyncj.PriorityTaskScheduler
 */
package asyncj;