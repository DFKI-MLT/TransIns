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

  /** maximum number of jobs in queue */
  public static final String MAX_QUEUE_SIZE = "max_queue_size";

  /** maximum size of documents to translate in MB */
  public static final String MAX_FILE_SIZE = "max_file_size";


  private ConfigKeys() {

    // private constructor to enforce noninstantiability
  }
}
