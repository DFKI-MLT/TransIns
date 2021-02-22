package de.dfki.mlt.transins.server;

import java.io.IOException;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server providing the TransIns REST service.
 *
 * @author Jörg Steffen, DFKI
 */
public final class TransInsServer {

  private static final Logger logger = LoggerFactory.getLogger(TransInsServer.class);


  private TransInsServer() {

    // private constructor to enforce noninstantiability
  }


  /**
   * Start the server.
   *
   * @param args
   *          the arguments
   */
  @SuppressWarnings("checkstyle:IllegalCatch")
  public static void main(String[] args) {

    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
    context.setContextPath("/");

    // configure Jersey servlet
    ResourceConfig resourceConfig = new ResourceConfig();
    resourceConfig.property(
        ServerProperties.PROVIDER_CLASSNAMES, TransInsService.class.getCanonicalName());
    resourceConfig.property(
        ServerProperties.WADL_FEATURE_DISABLE, true);
    resourceConfig.register(MultiPartFeature.class);
    // init Jersey servlet and add to context
    ServletHolder jerseyServlet = new ServletHolder(new ServletContainer(resourceConfig));
    jerseyServlet.setInitOrder(0);
    context.addServlet(jerseyServlet, "/*");

    try {
      String configFileName = "server.cfg";
      if (args.length > 0) {
        configFileName = args[0];
      }
      PropertiesConfiguration serverConfig = Utils.readConfigFromClasspath(configFileName);
      TransInsService.init(serverConfig);
      Server server = new Server(serverConfig.getInt(ConfigKeys.PORT, 7777));
      server.setHandler(context);

      try {
        server.start();
        server.join();
      } catch (Exception e) {
        logger.error(e.getLocalizedMessage(), e);
        server.destroy();
      }
    } catch (ConfigurationException | IOException e) {
      logger.error(e.getLocalizedMessage(), e);
    }
  }
}
