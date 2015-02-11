package asyncj.extensions.jaxrs;


import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
/**
 * Represents descriptor of the HTTP servlet container.
 * @author Roman Sakno
 */
final class TestServlet extends ServletContainer {

    private static ResourceConfig createResourceConfig(){
        final ResourceConfig result = new ResourceConfig();
        result.register(new SimpleServiceImpl());
        return result;
    }

    /**
     * Initializes a new instance of the rest service.
     */
    TestServlet(){
        super(createResourceConfig());
    }
}

