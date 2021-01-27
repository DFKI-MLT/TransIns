package de.dfki.mlt.transins.server;

/**
 * Define all configuration keys as constants.
 *
 * @author Jörg Steffen, DFKI
 */
public final class ConfigKeys {

  /** server port number **/
  public static final String PORT = "port";

  /** supported language pairs */
  public static final String SUPPORTED_LANG_PAIRS = "supported_lang_pairs";


  private ConfigKeys() {

    // private constructor to enforce noninstantiability
  }
}
