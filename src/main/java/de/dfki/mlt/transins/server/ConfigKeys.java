package de.dfki.mlt.transins.server;

/**
 * Define all configuration keys as constants.
 *
 * @author Jörg Steffen, DFKI
 */
public final class ConfigKeys {

  /** server port number **/
  public static final String PORT = "port";

  /** maximum number of jobs in queue */
  public static final String MAX_QUEUE_SIZE = "max_queue_size";

  /** maximum size of documents to translate in MB */
  public static final String MAX_FILE_SIZE = "max_file_size";

  /** switch for activating development mode; allows cross-origin resource sharing */
  public static final String DEVELOPMENT_MODE = "development_mode";

  /** supported translation directions */
  public static final String SUPPORTED_TRANS_DIRS = "supported_trans_dirs";

  /** Marian NMT server web socket URL */
  public static final String TRANSLATION_URL = "translation_url";

  /** switch to add target language tag as first token of source sentence */
  public static final String USE_TARGET_LANG_TAG = "use_target_lang_tag";

  /** pre-/postprocessing server host */
  public static final String PREPOST_HOST = "prepost_host";

  /** pre-/postprocessing server port */
  public static final String PREPOST_PORT = "prepost_port";


  private ConfigKeys() {

    // private constructor to enforce noninstantiability
  }
}
