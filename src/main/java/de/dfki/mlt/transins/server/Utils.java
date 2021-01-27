package de.dfki.mlt.transins.server;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;

/**
 * Provide utility methods.
 *
 * @author Jörg Steffen, DFKI
 */
public final class Utils {

  private Utils() {

    // private constructor to enforce noninstantiability
  }


  /**
   * Read the configuration from the given file name searched in the Java classpath.
   *
   * @param configFileName
   *          config file name
   * @return the configuration
   * @throws ConfigurationException
   *           if configuration fails
   */
  public static PropertiesConfiguration readConfigFromClasspath(String configFileName)
      throws ConfigurationException {

    Parameters params = new Parameters();
    FileBasedConfigurationBuilder<PropertiesConfiguration> builder =
        new FileBasedConfigurationBuilder<PropertiesConfiguration>(PropertiesConfiguration.class)
            .configure(params.properties()
                .setFileName(configFileName)
                .setEncoding("UTF-8")
                .setListDelimiterHandler(new DefaultListDelimiterHandler(',')));
    return builder.getConfiguration();
  }
}
