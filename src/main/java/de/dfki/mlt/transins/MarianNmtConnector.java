package de.dfki.mlt.transins;

import static de.dfki.mlt.transins.TagUtils.isTag;
import static de.dfki.mlt.transins.TagUtils.removeTags;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dfki.mlt.transins.MarkupInserter.MarkupStrategy;
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
  private Map<String, String> batchResult;


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

    super.result = null;
    super.current = -1;

    // check if there is actually text to translate
    if (!fragment.hasText(false)) {
      return 0;
    }

    // get the document id as passed via the parameter;
    // the document id is only set when in batch mode
    String docId = this.params.getDocumentId();
    if (!docId.isEmpty()) {
      // get batch result from batch runner
      if (this.batchResult == null) {
        this.batchResult = BatchRunner.INSTANCE.getBatchResult(this.params.getDocumentId());
      }
      String postprocessedSentence = this.batchResult.get(fragment.toString());
      if (postprocessedSentence != null) {
        return createQueryResult(fragment, postprocessedSentence);
      }
    }

    // fragment not found in batch result or batch result not available,
    // so process the fragment

    logger.debug("translating from {} to {}", super.getSourceLanguage(), super.getTargetLanguage());

    logger.debug("source sentence: \"{}\"",
        TagUtils.asString(fragment.getCodedText(), fragment.getCodes()));

    // preprocessing
    String sentence = fragment.getCodedText();
    String preprocessedSourceSentence =
        this.prepostClient.process(
            String.format("%s-%s",
                super.getSourceLanguage().toString(), super.getTargetLanguage().toString()),
            sentence,
            Mode.PREPROCESS,
            this.params.getPrePostHost(),
            this.params.getPrePostPort());
    logger.debug("preprocessed source sentence: \"{}\"",
        TagUtils.asString(preprocessedSourceSentence, fragment.getCodes()));

    // translate
    String translatorInput = removeTags(preprocessedSourceSentence);
    if (translatorInput.length() == 0) {
      logger.debug("empty translator input, skipping...");
      return 0;
    }
    if (this.params.isUseTargetLangTag()) {
      // add leading token with target language
      translatorInput = String.format("<to%s> %s", super.getTargetLanguage(), translatorInput);
    }
    logger.debug("send to translator: \"{}\"", translatorInput);
    String rawTranslation;
    try {
      open();
      rawTranslation = this.translatorClient.translate(translatorInput);
    } catch (InterruptedException | ExecutionException e) {
      throw new OkapiException("Error querying the translation server." + e.getMessage(), e);
    }
    String translation = processRawTranslation(
        rawTranslation, fragment, preprocessedSourceSentence, translatorInput,
        this.params.getMarkupStrategy(), this.params.getMaxGapSize(),
        this.params.isUseTargetLangTag());

    // postprocessing
    String postprocessedSentence =
        this.prepostClient.process(
            String.format("%s-%s",
                super.getSourceLanguage().toString(), super.getTargetLanguage().toString()),
            translation,
            Mode.POSTPROCESS,
            this.params.getPrePostHost(),
            this.params.getPrePostPort());
    if (fragment.hasCode()) {
      postprocessedSentence = cleanPostProcessedSentence(postprocessedSentence);
      // print target sentence with human readable tags
      TextFragment postFragment = new TextFragment();
      postFragment.setCodedText(postprocessedSentence, fragment.getClonedCodes(), true);
      logger.debug("postprocessed target sentence with tags: \"{}\"",
          this.util.toCodedHTML(postFragment));
    } else {
      logger.debug("postprocessed target sentence: \"{}\"", postprocessedSentence);
    }

    // add space at sentence end when translating MS Office documents
    boolean addSpaceAtSentenceEnd = this.params.getOkapiFilterConfigId().equals("okf_openxml");
    if (addSpaceAtSentenceEnd) {
      postprocessedSentence = postprocessedSentence + " ";
    }

    // create query result
    return createQueryResult(fragment, postprocessedSentence);
  }


  private int createQueryResult(TextFragment fragment, String postprocessedSentence) {

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

    return ((super.current == 0) ? 1 : 0);
  }


  /**
   * Process the given raw translation: Re-insert tags (if required) and resolve byte pair encoding.
   *
   * @param rawTranslation
   *          the raw translation as provided by the translator, potentially containing
   *          alignments
   * @param fragment
   *          the fragment from which the source sentence to translate was provided
   * @param preprocessedSourceSentence
   *          the preprocessed source sentence
   * @param translatorInput
   *          the source sentence as it was sent to the translator
   * @param markupStrategy
   *          the markup re-insertion strategy to use
   * @param maxGapSize
   *          the maximum gap size to use with COMPLETE_MAPPING
   * @param useTargetLangTag
   *          if <code>true</code>, target language tag was added as first token to source sentence,
   *          so alignments have to be corrected
   * @return the translation with re-inserted tags (if required) and resolved byte pair encoding
   */
  static String processRawTranslation(
      String rawTranslation, TextFragment fragment, String preprocessedSourceSentence,
      String translatorInput, MarkupStrategy markupStrategy, int maxGapSize,
      boolean useTargetLangTag) {

    // split into translation and alignments
    String[] parts = rawTranslation.split(" \\|\\|\\| ");
    String translation = null;

    if (parts.length == 2) {
      // if tags and alignments are available, re-insert tags
      translation = parts[0].strip();

      if (isSentencePieceEncoded(translation)) {
        // convert SentencePiece subtokenization format into BPE format
        translation = convertSentencePieceToBpe(translation);
      }

      if (fragment.hasCode()) {
        logger.debug("raw target sentence: \"{}\"", translation);
        // check if there are hand annotated alignments
        String rawAlignments = null;
        if (AlignmentProvider.INSTANCE.isInitialized()) {
          rawAlignments = AlignmentProvider.INSTANCE.getAlignments(translatorInput, translation);
        }
        if (rawAlignments != null) {
          logger.debug("hand annotated alignments: \"{}\"", rawAlignments);
        } else {
          // use alignments as provided by Marian NMT
          rawAlignments = parts[1].strip();
          logger.debug("raw alignments: \"{}\"", rawAlignments);
        }

        if (translation.isEmpty()) {
          // if the correct translation model is used, this shouldn't happen
          return translation;
        }
        Alignments algn = createAlignments(rawAlignments);
        if (useTargetLangTag) {
          // compensate for leading target language token in source sentence
          algn.shiftSourceIndexes(-1);
        }
        String[] targetTokensWithTags = MarkupInserter.insertMarkup(
            preprocessedSourceSentence, translation, algn, markupStrategy, maxGapSize);

        // prepare translation for postprocessing;
        // mask tags so that detokenizer in postprocessing works correctly
        translation = maskTags(targetTokensWithTags);

      } else {
        // no tags, just undo byte pair encoding
        translation = translation.replaceAll("@@ ", "");
      }
    } else {
      translation = rawTranslation;
      logger.debug("raw target sentence: \"{}\"", translation);
      // undo byte pair encoding
      translation = translation.replaceAll("@@ ", "");
    }
    return translation;
  }


  /**
   * Create either hard or soft alignments instance, depending on the given raw alignments.
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


  /**
   * Clean up the given postprocessed sentence by unmasking and detokenizing tags.
   *
   * @param postprocessedSentence
   *          the postprocessed sentence
   * @return the cleaned up postprocessed sentence
   */
  public static String cleanPostProcessedSentence(String postprocessedSentence) {

    // unmask tags
    postprocessedSentence = unmaskTags(postprocessedSentence);
    // remove space in front of and after tags
    postprocessedSentence = detokenizeTags(postprocessedSentence);
    return postprocessedSentence;
  }


  /**
   * Embed each Okapi tag with last/first character of preceding/following token (if available).
   * This makes sure that the detokenizer in postprocessing works correctly.
   *
   * @param targetTokensWithTags
   *          the target tokens with Okapi tags
   * @return string with embedded Okapi tags
   */
  static String maskTags(String[] targetTokensWithTags) {

    StringBuilder result = new StringBuilder();

    for (int i = 0; i < targetTokensWithTags.length; i++) {
      String currentToken = targetTokensWithTags[i];
      if (isTag(currentToken)) {
        for (int j = i - 1; j >= 0; j--) {
          String precedingToken = targetTokensWithTags[j];
          if (!isTag(precedingToken)) {
            currentToken = currentToken + precedingToken.charAt(precedingToken.length() - 1);
            break;
          }
        }
        for (int j = i + 1; j < targetTokensWithTags.length; j++) {
          String followingToken = targetTokensWithTags[j];
          if (!isTag(followingToken)) {
            currentToken = followingToken.charAt(0) + currentToken;
            break;
          }
        }
      }
      result.append(currentToken + " ");
    }
    return result.toString().strip();
  }


  /**
   * Undo the tag masking of {@link MarkupInserter#maskTags(String[])}.
   *
   * @param postprocessedSentence
   *          the postprocessed sentence
   * @return the unmasked postprocessed sentence
   */
  @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
  static String unmaskTags(String postprocessedSentence) {

    Pattern tag = Pattern.compile("\\S?([\uE101\uE102\uE103].)\\S?");
    Matcher matcher = tag.matcher(postprocessedSentence);
    StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(sb, matcher.group(1));
    }
    matcher.appendTail(sb);

    return sb.toString();
  }


  /**
   * Remove from the given postprocessed sentence with re-inserted tags the spaces to the
   * left/right of the tags, depending on the type of tag. Opening tags have all
   * spaces to their right removed, closing tags have all spaces to their left removed,
   * isolated tags have all spaces to their left and right removed.
   *
   * @param postprocessedSentence
   *          the postprocessed sentence with re-inserted tags
   * @return the postprocessed sentence with detokenized tags
   */
  @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
  static String detokenizeTags(String postprocessedSentence) {

    Pattern openingTag = Pattern.compile("(\uE101.)( )+");
    Matcher openingMatcher = openingTag.matcher(postprocessedSentence);
    StringBuilder sb = new StringBuilder();
    while (openingMatcher.find()) {
      openingMatcher.appendReplacement(sb, openingMatcher.group(1));
    }
    openingMatcher.appendTail(sb);

    Pattern closingTag = Pattern.compile("( )+(\uE102.)");
    Matcher closingMatcher = closingTag.matcher(sb.toString());
    sb = new StringBuilder();
    while (closingMatcher.find()) {
      closingMatcher.appendReplacement(sb, closingMatcher.group(2));
    }
    closingMatcher.appendTail(sb);

    Pattern isolatedTagRight = Pattern.compile("(\uE103.)( )+");
    Matcher isoMatcherRight = isolatedTagRight.matcher(sb.toString());
    sb = new StringBuilder();
    while (isoMatcherRight.find()) {
      isoMatcherRight.appendReplacement(sb, isoMatcherRight.group(1));
    }
    isoMatcherRight.appendTail(sb);

    Pattern isolatedTagLeft = Pattern.compile("( )+(\uE103.)");
    Matcher isoMatcherLeft = isolatedTagLeft.matcher(sb.toString());
    sb = new StringBuilder();
    while (isoMatcherLeft.find()) {
      isoMatcherLeft.appendReplacement(sb, isoMatcherLeft.group(2));
    }
    isoMatcherLeft.appendTail(sb);

    return sb.toString();
  }


  /**
   * Check if the given translation is encoded with SentencePiece.
   *
   * @param translation
   *          the translation to check
   * @return flag indicating SentencePiece encoding
   */
  @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
  static boolean isSentencePieceEncoded(String translation) {

    return translation.startsWith("\u2581");
  }


  /**
   * Convert a string that has been subtokenized with SentencePiece into to format used
   * with byte pair encoding.
   *
   * @param translation
   *          the string to convert
   * @return the converted string
   */
  @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
  static String convertSentencePieceToBpe(String translation) {

    String[] tokens = translation.split(" ");
    StringBuilder result = new StringBuilder();
    for (String oneToken : tokens) {
      if (oneToken.charAt(0) == '\u2581') {
        result.append(" ");
        result.append(oneToken.substring(1));
      } else {
        result.append("@@ ");
        result.append(oneToken);
      }
    }

    return result.toString().trim();
  }
}
