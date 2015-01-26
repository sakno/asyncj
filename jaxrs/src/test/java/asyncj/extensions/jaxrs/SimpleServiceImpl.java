package asyncj.extensions.jaxrs;

import asyncj.ActiveObject;
import asyncj.AsyncUtils;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;

import static asyncj.extensions.jaxrs.AsyncResponseUtils.*;

@Path("/")
@Singleton
public final class SimpleServiceImpl extends ActiveObject {

    public SimpleServiceImpl() {
        super(AsyncUtils.getGlobalScheduler());
    }

    @GET
    @Path("/simpleGet")
    @Produces(MediaType.TEXT_PLAIN)
    public void simpleGet(@Suspended final AsyncResponse response) {
        successful("Hello, ")
                .then((String value) -> value + "world!")
                .then(responseTask(response));
    }

    @GET
    @Path("/array")
    @Produces(MediaType.TEXT_PLAIN)
    public void emulateWork(@Suspended final AsyncResponse response,
                            @QueryParam("length") final int length) {
        reduceUntil(cond -> cond == 0 ? null : cond - 1,
                (accumulator, cond) -> cond + accumulator,
                length,
                "")
                .onCompleted(responseCallback(response));
    }
}
