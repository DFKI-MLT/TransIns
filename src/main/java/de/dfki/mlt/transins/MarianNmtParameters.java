package de.dfki.mlt.transins;

import net.sf.okapi.common.ParametersDescription;
import net.sf.okapi.common.StringParameters;
import net.sf.okapi.common.uidescription.EditorDescription;
import net.sf.okapi.common.uidescription.IEditorDescriptionProvider;

/**
 * Parameters used for {@link MarianNmtConnector}.
 *
 * @author Jörg Steffen, DFKI
 */
public class MarianNmtParameters extends StringParameters implements IEditorDescriptionProvider {

  private static final String TRANSLATION_URL = "translation_url";
  private static final String PREPOST_HOST = "prepost_host";
  private static final String PREPOST_PORT = "prepost_port";
  private static final String MARKUP_INSERTION_STRATEGY = "markup_insertion_strategy";


  /**
   * Create new Marian NMT parameters.
   */
  public MarianNmtParameters() {

    super();
  }


  /**
   * Creates new Marian NMT parameters from the given data
   *
   * @param initialData
   *          the data
   */
  public MarianNmtParameters(String initialData) {

    super(initialData);
  }


  @Override
  public void reset() {

    super.reset();
    // set defaults
    setTranslationUrl("ws://localhost:8080/translate");
    setPrePostHost("localhost");
    setPrePostPort(5000);
  }


  /**
   * @return the translation server URL
   */
  public String getTranslationUrl() {

    return getString(TRANSLATION_URL);
  }


  /**
   * @param translationUrl
   *          the translation server URL to set
   */
  public void setTranslationUrl(String translationUrl) {

    setString(TRANSLATION_URL, translationUrl);
  }


  /**
   * @return the pre-/postprocessing host
   */
  public String getPrePostHost() {

    return getString(PREPOST_HOST);
  }


  /**
   * @param prePostHost
   *          the pre-/postprocessing host to set
   */
  public void setPrePostHost(String prePostHost) {

    setString(PREPOST_HOST, prePostHost);
  }


  /**
   * @return the pre-/postprocessing port
   */
  public int getPrePostPort() {

    return getInteger(PREPOST_PORT);
  }


  /**
   * @param prePostPort
   *          the pre-/postprocessing port to set
   */
  public void setPrePostPort(int prePostPort) {

    setInteger(PREPOST_PORT, prePostPort);
  }


  /**
   * @return the markup insertion strategy
   */
  public String getMarkupInsertionStrategy() {

    return getString(MARKUP_INSERTION_STRATEGY);
  }


  /**
   * @param markupInsertionStrategy
   *          the markup insertion strategy to set
   */
  public void setMarkupInsertionStrategy(String markupInsertionStrategy) {

    setString(MARKUP_INSERTION_STRATEGY, markupInsertionStrategy);
  }


  @Override
  public ParametersDescription getParametersDescription() {

    ParametersDescription desc = new ParametersDescription(this);
    desc.add(TRANSLATION_URL, "Translation Server URL:",
        "Full URL of the translation server");
    desc.add(PREPOST_HOST, "Pre-/Postprocessing Host:",
        "Host where pre-/postprocessing server runs");
    desc.add(PREPOST_PORT, "Pre-/Postprocessing Port:",
        "Port of the pre-/postprocessing server");
    desc.add(MARKUP_INSERTION_STRATEGY, "Markup Insertion Strategy:",
        "Strategy to insert markup, either 'mtrain' or 'advanced'");

    return desc;
  }


  @Override
  public EditorDescription createEditorDescription(ParametersDescription paramsDesc) {

    EditorDescription desc = new EditorDescription("Marian NMT Connector Settings");

    desc.addTextInputPart(paramsDesc.get(TRANSLATION_URL));
    desc.addTextInputPart(paramsDesc.get(PREPOST_HOST));
    desc.addTextInputPart(paramsDesc.get(PREPOST_PORT));
    desc.addTextInputPart(paramsDesc.get(MARKUP_INSERTION_STRATEGY));
    return desc;
  }
}