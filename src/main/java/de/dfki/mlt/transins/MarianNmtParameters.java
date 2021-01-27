package de.dfki.mlt.transins;

import de.dfki.mlt.transins.MarkupInserter.MarkupStrategy;
import net.sf.okapi.common.ParametersDescription;
import net.sf.okapi.common.StringParameters;
import net.sf.okapi.common.uidescription.EditorDescription;
import net.sf.okapi.common.uidescription.IEditorDescriptionProvider;

/**
 * Parameters used for {@link MarianNmtConnector}. These are initialized from a config file, but
 * we add further parameters at runtime, as this is the only way to pass arguments to
 * MarianNmtConnector. This currently applies for the document id and markup strategy.
 *
 * @author Jörg Steffen, DFKI
 */
public class MarianNmtParameters extends StringParameters implements IEditorDescriptionProvider {

  private static final String TRANSLATION_URL = "translation_url";
  private static final String PREPOST_HOST = "prepost_host";
  private static final String PREPOST_PORT = "prepost_port";
  private static final String DOC_ID = "doc_id";
  private static final String MARKUP_STRATEGY = "markup_strategy";


  /**
   * Create new Marian NMT parameters.
   */
  public MarianNmtParameters() {

    super();
  }


  /**
   * Create new Marian NMT parameters from the given data.
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


  @Override
  public ParametersDescription getParametersDescription() {

    ParametersDescription desc = new ParametersDescription(this);
    desc.add(TRANSLATION_URL, "Translation Server URL:",
        "Full URL of the translation server");
    desc.add(PREPOST_HOST, "Pre-/Postprocessing Host:",
        "Host where pre-/postprocessing server runs");
    desc.add(PREPOST_PORT, "Pre-/Postprocessing Port:",
        "Port of the pre-/postprocessing server");

    return desc;
  }


  @Override
  public EditorDescription createEditorDescription(ParametersDescription paramsDesc) {

    EditorDescription desc = new EditorDescription("Marian NMT Connector Settings");

    desc.addTextInputPart(paramsDesc.get(TRANSLATION_URL));
    desc.addTextInputPart(paramsDesc.get(PREPOST_HOST));
    desc.addTextInputPart(paramsDesc.get(PREPOST_PORT));
    return desc;
  }


  /**
   * @return the document id of the document to translate;
   *         not part of the config file, but set at runtime
   */
  public String getDocumentId() {

    return getString(DOC_ID);
  }


  /**
   * @param docId
   *          the document id of the document to translate;
   *          not part of the config file, but set at runtime
   */
  public void setDocumentId(String docId) {

    setString(DOC_ID, docId);
  }


  /**
   * @return the markup re-insertion strategy;
   *         not part of the config file, but set at runtime
   */
  public MarkupStrategy getMarkupStrategy() {

    return MarkupStrategy.valueOf(getString(MARKUP_STRATEGY));
  }


  /**
   * @param markupStrategy
   *          the markup re-insertion strategy;
   *          not part of the config file, but set at runtime
   */
  public void setMarkupStrategy(MarkupStrategy markupStrategy) {

    setString(MARKUP_STRATEGY, markupStrategy.toString());
  }
}
