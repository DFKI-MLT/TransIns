package de.dfki.mlt.transins;

import static de.dfki.mlt.transins.TagUtils.removeTags;

import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dfki.mlt.transins.PrePostProcessingClient.Mode;
import net.sf.okapi.common.IParameters;
import net.sf.okapi.common.exceptions.OkapiException;
import net.sf.okapi.common.query.MatchType;
import net.sf.okapi.common.query.QueryResult;
import net.sf.okapi.common.resource.TextFragment;
import net.sf.okapi.lib.translation.BaseConnector;
import net.sf.okapi.lib.translation.QueryUtil;

/**
 * Connector implementation for Marian NMT. Also runs pre- and postprocessing.
 *
 * @author Jörg Steffen, DFKI
 */
public class MarianNmtConnector extends BaseConnector {

  private static final Logger logger = LoggerFactory.getLogger(MarianNmtConnector.class);

  private MarianNmtParameters params;
  private PrePostProcessingClient prepostClient;
  private MarianNmtClient translatorClient;
  private QueryUtil util;


  /**
   * Create a new connector instance.
   */
  public MarianNmtConnector() {

    this.params = new MarianNmtParameters();
    this.prepostClient = new PrePostProcessingClient();
    this.util = new QueryUtil();
  }


  @Override
  public IParameters getParameters() {

    return this.params;
  }


  @Override
  public void setParameters(IParameters params) {

    this.params = (MarianNmtParameters)params;
  }


  @Override
  public String getName() {

    return "Marian NMT";
  }


  @Override
  public String getSettingsDisplay() {

    return String.format(
        "Translation Server URL: %s%nPre-/Postprocessing Host: %s%nPre-/Postprocessing Port: %d",
        this.params.getTranslationUrl(),
        this.params.getPrePostHost(), this.params.getPrePostPort());
  }


  @Override
  public void open() {

    if (this.translatorClient == null) {
      this.translatorClient = new MarianNmtClient(this.params.getTranslationUrl());
    }
  }


  @Override
  public void close() {

    this.translatorClient.close();
  }


  @Override
  public int query(String plainText) {

    return query(new TextFragment(plainText));
  }


  /**
   * Query the Marian NMT web socket via the client.
   *
   * @param fragment
   *          the fragment to query
   * @return the number of translations (1 or 0).
   */
  @Override
  public int query(TextFragment fragment) {

    logger.debug("translating from {} to {}", super.getSourceLanguage(), super.getTargetLanguage());

    open();

    super.result = null;
    super.current = -1;
    try {
      // check if there is actually text to translate
      if (!fragment.hasText(false)) {
        return 0;
      }
      logger.debug("source sentence: \"{}\"",
          TagUtils.asString(fragment.getCodedText(), fragment.getCodes()));

      // preprocessing
      String sentence = fragment.getCodedText();
      String preprocessedSourceSentence =
          this.prepostClient.process(
              super.getSourceLanguage().toString(),
              sentence,
              Mode.PREPROCESS,
              this.params.getPrePostHost(),
              this.params.getPrePostPort());
      logger.debug("preprocessed source sentence: \"{}\"",
          TagUtils.asString(preprocessedSourceSentence, fragment.getCodes()));

      // translate
      String translatorInput = removeTags(preprocessedSourceSentence);
      // add leading token with target language
      translatorInput = String.format("<to%s> %s", super.getTargetLanguage(), translatorInput);
      logger.debug("send to translator: \"{}\"", translatorInput);
      String translatorResponse = this.translatorClient.translate(translatorInput);

      // split into translation and alignments
      String[] parts = translatorResponse.split(" \\|\\|\\| ");
      String translation = null;

      boolean hasTags = fragment.hasCode();
      if (parts.length == 2) {
        // if tags and alignments are available, re-insert tags
        translation = parts[0].strip();

        if (hasTags) {
          // get alignments for tag re-insertion
          String rawAlignments = parts[1].strip();
          logger.debug("raw target sentence: \"{}\"", translation);
          logger.debug("raw alignments: \"{}\"", rawAlignments);
          Alignments algn = createAlignments(rawAlignments);
          // compensate for leading target language token in source sentence
          algn.shiftSourceIndexes(-1);
          translation = MarkupInserter.insertMarkupComplete(
              preprocessedSourceSentence, translation, algn);
        } else {
          // no tags, just undo byte pair encoding
          translation = translation.replaceAll("@@ ", "");
        }
      } else {
        translation = translatorResponse;
        logger.debug("raw target sentence: \"{}\"", translation);
        // undo byte pair encoding
        translation = translation.replaceAll("@@ ", "");
      }

      // postprocessing
      String postprocessedSentence =
          this.prepostClient.process(
              super.getTargetLanguage().toString(),
              translation,
              Mode.POSTPROCESS,
              this.params.getPrePostHost(),
              this.params.getPrePostPort());

      if (hasTags) {
        // unmask tags
        postprocessedSentence = MarkupInserter.unmaskTags(postprocessedSentence);
        // remove space in front of and after tags
        postprocessedSentence = MarkupInserter.detokenizeTags(postprocessedSentence);
        // add a space at the end, otherwise the next sentence immediately starts after this one
        // TODO deactivate segmentation step in translator and do the handling of sentences here
        postprocessedSentence = postprocessedSentence + " ";

        // print target sentence with human readable tags
        TextFragment postFragment = new TextFragment();
        postFragment.setCodedText(postprocessedSentence, fragment.getClonedCodes(), true);
        logger.debug("postprocessed target sentence with tags: \"{}\"",
            this.util.toCodedHTML(postFragment));
      } else {
        logger.debug("postprocessed target sentence: \"{}\"", postprocessedSentence);
      }

      super.result = new QueryResult();
      super.result.weight = getWeight();
      super.result.source = fragment;

      if (fragment.hasCode()) {
        super.result.target = new TextFragment();
        super.result.target.setCodedText(postprocessedSentence, fragment.getClonedCodes(), true);
      } else {
        super.result.target = new TextFragment(postprocessedSentence);
      }

      super.result.setFuzzyScore(95);
      super.result.origin = getName();
      super.result.matchType = MatchType.MT;
      super.current = 0;
    } catch (InterruptedException | ExecutionException e) {
      throw new OkapiException("Error querying the translation server." + e.getMessage(), e);
    }
    return ((super.current == 0) ? 1 : 0);
  }


  /**
   * Create either hard or soft alignments instance, depending on the given raw alignments
   *
   * @param rawAlignments
   *          the raw alignments
   * @return alignments instance
   */
  public static Alignments createAlignments(String rawAlignments) {

    if (Pattern.compile("\\d-\\d").matcher(rawAlignments).find()) {
      return new HardAlignments(rawAlignments);
    } else {
      return new SoftAlignments(rawAlignments);
    }
  }
}
