package asyncj.extensions.jaxrs;

import asyncj.AsyncCallback;
import asyncj.AsyncResult;
import asyncj.ThrowableFunction;

import javax.ws.rs.container.AsyncResponse;

/**
 * Represents a set of helper methods used to create asynchronous REST services.
 * <p>
 *     The following example demonstrates usage of AsyncJ JAX-RS helpers:
 *     <code>
 *         import asyncj.ActiveObject;
 *         import asyncj.AsyncUtils;
 *
 *         import javax.inject.Singleton;
 *         import javax.ws.rs.*;
 *         import javax.ws.rs.container.*;
 *         import javax.ws.rs.core.MediaType;
 *
 *         import static asyncj.extensions.jaxrs.AsyncResponseUtils.*;
 *
 *         @ Path("/")
 *         @ Singleton
 *         public final class SimpleServiceImpl extends ActiveObject {
 *
 *              public SimpleServiceImpl() {
 *                  super(AsyncUtils.getGlobalScheduler());
 *              }
 *
 *         @ GET
 *         @ Path("/simpleGet")
 *         @ Produces(MediaType.TEXT_PLAIN)
 *         public void simpleGet(@Suspended final AsyncResponse response) {
 *              successful("Hello, ")
 *                  .then((String value) -> value + "world!")
 *                  .then(responseTask(response));
 *          }
 *
 *          @ GET
 *          @ Path("/array")
 *          @ Produces(MediaType.TEXT_PLAIN)
 *          public void emulateWork(@Suspended final AsyncResponse response,
 *          @ QueryParam("length") final int length) {
 *              reduceUntil(cond -> cond == 0 ? null : cond - 1,
 *              (accumulator, cond) -> cond + accumulator,
 *              length,
 *              "")
 *              .onCompleted(responseCallback(response));
 *          }
 *      }
 *     </code>
 * </p>
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public final class AsyncResponseUtils {
    private AsyncResponseUtils(){

    }


    public static <V> AsyncCallback<V> responseCallback(final AsyncResponse response){
        return (input, error) -> {
            if (error != null) response.resume(error);
            else response.resume(input);
        };
    }

    /**
     * Associates REST service response with the specified asynchronous task.
     * @param ar An object that represents result of the asynchronous REST operation result. Cannot be {@literal null}.
     * @param response An object used to send REST operation response to the client. Cannot be {@literal null}.
     * @param <V> The result of the REST operation.
     * @return An object passed to the first argument (used for pipelining).
     */
    public static <V> AsyncResult<V> setResponse(final AsyncResult<V> ar, final AsyncResponse response){
        ar.onCompleted(responseCallback(response));
        return ar;
    }

    /**
     * Constructs a new function that can be used for completing the asynchronous JAX-RS response.
     * @param response The response to wrap into the function. Cannot be {@literal null}.
     * @param <V> The value to be passed into the
     * @return A new function that can be used for completing the asynchronous JAX-RS response.
     */
    public static <V> ThrowableFunction<V, V> responseTask(final AsyncResponse response){
        return result -> {
            response.resume(result);
            return result;
        };
    }
}
