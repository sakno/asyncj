package asyncj.extensions.jaxrs;


import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
/**
 * Represents descriptor of the HTTP servlet container.
 * @author Roman Sakno
 */
final class TestServlet extends ServletContainer {

    private static ResourceConfig createResourceConfig(){
        return new ResourceConfig(SimpleServiceImpl.class);
    }

    /**
     * Initializes a new instance of the rest service.
     */
    TestServlet(){
        super(createResourceConfig());
    }
}

