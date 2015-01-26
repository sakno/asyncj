package asyncj.extensions.jaxrs;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Assert;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

/**
 * @author Roman Sakno
 * @version 1.0
 * @since 1.0
 */
public final class JettyBasedTest extends Assert {
    private static Server startServer() throws Exception {
        //Assign global scheduler
        final Server jettyServer = new Server();

        //initializes a new connector.
        final ServerConnector connector = new ServerConnector(jettyServer);
        connector.setPort(3344);
        connector.setHost("localhost");
        jettyServer.setConnectors(new Connector[]{connector});
        final ServletContextHandler resourcesHandler = new ServletContextHandler(ServletContextHandler.SECURITY);
        resourcesHandler.setContextPath("/asyncj");
        //Setup REST service
        resourcesHandler.addServlet(new ServletHolder(new TestServlet()), "/test/*");
        //Setup static pages

        jettyServer.setHandler(resourcesHandler);
        jettyServer.start();
        return jettyServer;
    }

    @Test
    public void simpleGetTest() throws Exception{
        final Server server = startServer();
        try{
            final Client restClient = ClientBuilder.newClient();
            final WebTarget target = restClient.target("http://127.0.0.1:3344/asyncj/test/simpleGet");
            final String response = target.request(MediaType.TEXT_PLAIN).get(String.class);
            assertNotNull(response);
            assertFalse(response.isEmpty());
            assertEquals("Hello, world!", response);
        }
        finally {
            server.stop();
        }
    }

    @Test
    public void getArrayAsyncTest() throws Exception {
        final Server server = startServer();
        try{
            final Client restClient = ClientBuilder.newClient();
            final WebTarget target = restClient.target("http://127.0.0.1:3344/asyncj/test/array?length=10");
            final String response = target.request(MediaType.TEXT_PLAIN).get(String.class);
            assertNotNull(response);
            assertFalse(response.isEmpty());
            assertEquals("0123456789", response);
        }
        finally {
            server.stop();
        }
    }
}
