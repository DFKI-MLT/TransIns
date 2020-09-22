package de.dfki.mlt.transins;

import static de.dfki.mlt.transins.TagUtils.isBackwardTag;
import static de.dfki.mlt.transins.TagUtils.isClosingTag;
import static de.dfki.mlt.transins.TagUtils.isIsolatedTag;
import static de.dfki.mlt.transins.TagUtils.isOpeningTag;
import static de.dfki.mlt.transins.TagUtils.isTag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
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

  // special token to mark end of sentence of target sentence
  private static final String EOS = "end-of-target-sentence-marker";

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
   *          the fragment to query.
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
      String translatorResponse = this.translatorClient.send(translatorInput);

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

          String[] sourceTokensWithTags = preprocessedSourceSentence.split(" ");
          String[] targetTokensWithoutTags = translation.split(" ");

          // print alignments
          String[] sourceTokensWithoutTags = removeTags(preprocessedSourceSentence).split(" ");
          logger.debug(String.format("sentence alignments:%n%s", createSentenceAlignments(
              sourceTokensWithoutTags, targetTokensWithoutTags, algn)));

          // get mapping of closing to opening tags
          Map<String, String> closing2OpeningTag = createTagMap(sourceTokensWithTags);

          // assign each source token its tags
          Map<Integer, List<String>> sourceTokenIndex2tags =
              createSourceTokenIndex2Tags(sourceTokensWithTags);

          // move tags in case of no target token pointing to the associated source token
          List<String> unusedTags =
              moveSourceTagsToPointedTokens(sourceTokenIndex2tags, closing2OpeningTag,
                  algn.getPointedSourceTokens(), sourceTokensWithoutTags.length);

          String[] targetTokensWithTags = reinsertTags(
              sourceTokensWithoutTags, targetTokensWithoutTags, algn, sourceTokenIndex2tags,
              closing2OpeningTag);

          // clean up tags
          targetTokensWithTags = moveTagsFromBetweenBpeFragments(targetTokensWithTags);
          targetTokensWithTags = undoBytePairEncoding(targetTokensWithTags);
          targetTokensWithTags = handleInvertedTags(closing2OpeningTag, targetTokensWithTags);
          targetTokensWithTags = removeRedundantTags(closing2OpeningTag, targetTokensWithTags);
          targetTokensWithTags = balanceTags(closing2OpeningTag, targetTokensWithTags);
          targetTokensWithTags = mergeNeighborTagPairs(closing2OpeningTag, targetTokensWithTags);
          targetTokensWithTags = addUnusedTags(targetTokensWithTags, unusedTags);

          // prepare translation for postprocessing;
          // mask tags so that detokenizer in postprocessing works correctly
          translation = maskTags(targetTokensWithTags);
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
        postprocessedSentence = unmaskTags(postprocessedSentence);
        // remove space in front of and after tags
        postprocessedSentence = detokenizeTags(postprocessedSentence);
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
   * Convert from coded fragment to text without codes.
   *
   * @param fragment
   *          the fragment to convert
   * @return the resulting string
   */
  public static String removeCodes(TextFragment fragment) {

    if (fragment == null) {
      return "";
    }

    String codedText = fragment.getCodedText();
    return removeTags(codedText);
  }


  /**
   * Remove Okapi codes from the given text.
   *
   * @param codedText
   *          the text to remove the codes from
   * @return the resulting string
   */
  public static String removeTags(String codedText) {

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < codedText.length(); i++) {
      switch (codedText.charAt(i)) {
        case TextFragment.MARKER_OPENING:
        case TextFragment.MARKER_CLOSING:
        case TextFragment.MARKER_ISOLATED:
          // skip the second tag character and the following space
          i = i + 2;
          break;
        default:
          sb.append(codedText.charAt(i));
      }
    }
    return sb.toString();
  }


  /**
   * Remove Okapi codes from the given tokens.
   *
   * @param tokens
   *          the tokens to remove the codes from
   * @return the resulting tokens
   */
  public static String[] removeTags(String[] tokens) {

    List<String> tokenList = new ArrayList<>();
    for (int i = 0; i < tokens.length; i++) {
      String oneToken = tokens[i];
      switch (oneToken.charAt(0)) {
        case TextFragment.MARKER_OPENING:
        case TextFragment.MARKER_CLOSING:
        case TextFragment.MARKER_ISOLATED:
          continue;
        default:
          tokenList.add(oneToken);
      }
    }

    String[] resultAsArray = new String[tokenList.size()];
    return tokenList.toArray(resultAsArray);
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


  /**
   * Create map of closing tags to opening tags from the given source tokens with tags.
   * It is assumed that the tags are balanced.
   *
   * @param sourceTokensWithTags
   *          the source tokens with tags
   * @return map of closing tags to opening tags
   */
  public static Map<String, String> createTagMap(String[] sourceTokensWithTags) {

    Map<String, String> resultMap = new HashMap<>();

    Stack<String> openingTagsStack = new Stack<>();

    for (String oneToken : sourceTokensWithTags) {
      if (isOpeningTag(oneToken)) {
        openingTagsStack.push(oneToken);
      } else if (isClosingTag(oneToken)) {
        String openingTag = openingTagsStack.pop();
        resultMap.put(oneToken, openingTag);
      }
    }

    return resultMap;
  }


  /**
   * Advanced version of creating a map from indexes to tags. Take into account the
   * 'direction' of a tag, i.e. isolated and opening tags are assigned to the <b>next</b> token,
   * while a closing tag is assigned to the <b>previous</b> token.
   *
   * @param sourceTokensWithTags
   *          the source tokens with tags
   * @return the map
   */
  public static Map<Integer, List<String>> createSourceTokenIndex2Tags(
      String[] sourceTokensWithTags) {

    Map<Integer, List<String>> index2tags = new HashMap<>();

    int offset = 0;

    for (int i = 0; i < sourceTokensWithTags.length; i++) {
      String currentToken = sourceTokensWithTags[i];
      if (isTag(currentToken)) {
        // forward token is default
        int currentIndex = i - offset;
        if (isBackwardTag(currentToken)) {
          currentIndex = currentIndex - 1;
        }
        List<String> currentTags = index2tags.get(currentIndex);
        if (currentTags == null) {
          currentTags = new ArrayList<>();
          index2tags.put(currentIndex, currentTags);
        }
        currentTags.add(sourceTokensWithTags[i]);
        offset = offset + 1;
      }
    }

    return index2tags;
  }


  /**
   * Make sure that all source tokens with associated tags are actually 'pointed' to by at least
   * one target token:
   * <ul>
   * <li>if there is no pointing token between opening and closing tag, remove them
   * <li>move opening tags forwards until pointed token
   * <li>move closing tags backwards until pointed token
   * <li>move isolated tags forward until pointed token or end-of-sentence
   * </ul>
   * The provided map {@code sourceTokenIndex2tags} is adapted accordingly.
   *
   * @param sourceTokenIndex2tags
   *          map of source token indexes to associated tags
   * @param closing2OpeningTag
   *          map of closing tags to corresponding opening tags
   * @param pointedSourceTokens
   *          all source token indexes for which there is at least one target token pointing at them
   *          in the alignments
   * @param sourceTokensLength
   *          the number of source tokens
   * @return list of tags that cannot be assigned to a pointed token;
   *         contains all tag pairs with no pointed token in between
   */
  public static List<String> moveSourceTagsToPointedTokens(
      Map<Integer, List<String>> sourceTokenIndex2tags,
      Map<String, String> closing2OpeningTag,
      List<Integer> pointedSourceTokens,
      int sourceTokensLength) {

    // get list of tags no to be moved
    List<String> tagsToIgnore =
        getTagsToIgnore(sourceTokenIndex2tags, closing2OpeningTag, sourceTokensLength);

    // for each closing tag of non-pointed source tokens, check if there is
    // a pointed source on the way to the corresponding opening tag;
    // if not remove the tag pair
    List<String> unusedTags = new ArrayList<>();
    for (var oneEntry : new HashSet<>(sourceTokenIndex2tags.entrySet())) {
      int sourceTokenIndex = oneEntry.getKey();
      if (pointedSourceTokens.contains(sourceTokenIndex)) {
        continue;
      }

      List<String> tags = oneEntry.getValue();
      for (String oneTag : new ArrayList<>(tags)) {
        if (tagsToIgnore.contains(oneTag)) {
          continue;
        }
        if (isClosingTag(oneTag)) {
          // find corresponding opening tag in front of it
          String openingTag = closing2OpeningTag.get(oneTag);
          int openingTagSourceTokenIndex = -1;
          List<String> previousTags = null;
          for (int i = sourceTokenIndex; i >= 0; i--) {
            previousTags = sourceTokenIndex2tags.get(i);
            if (previousTags != null
                && previousTags.contains(openingTag)) {
              openingTagSourceTokenIndex = i;
              break;
            }
          }
          // there must ALWAYS be a matching opening tag, as the tags in
          // the source sentence are balanced
          assert openingTagSourceTokenIndex != -1;
          assert previousTags != null;
          boolean foundPointingToken = false;
          for (int i = openingTagSourceTokenIndex; i <= sourceTokenIndex; i++) {
            if (pointedSourceTokens.contains(i)) {
              foundPointingToken = true;
              break;
            }
          }
          if (!foundPointingToken) {
            // no pointing token between opening and closing tag
            tags.remove(oneTag);
            if (tags.isEmpty()) {
              sourceTokenIndex2tags.remove(sourceTokenIndex);
            }
            if (previousTags != null) {
              // just check for non-null to suppress warning
              previousTags.remove(openingTag);
              if (previousTags.isEmpty()) {
                sourceTokenIndex2tags.remove(openingTagSourceTokenIndex);
              }
            }
            unusedTags.add(oneTag);
            unusedTags.add(0, openingTag);
          }
        }
      }
    }

    // at this point, all remaining tags are either isolated or have at least one pointing
    // token between the opening and closing tag;
    // now move opening and isolated tags (when not at sentence borders) to the
    // following pointed token and closing tags to the preceding pointed token
    for (var oneEntry : new HashSet<>(sourceTokenIndex2tags.entrySet())) {
      int sourceTokenIndex = oneEntry.getKey();
      if (pointedSourceTokens.contains(sourceTokenIndex)) {
        continue;
      }

      List<String> tags = oneEntry.getValue();
      for (String oneTag : new ArrayList<>(tags)) {
        if (tagsToIgnore.contains(oneTag)) {
          continue;
        }
        if (isOpeningTag(oneTag) || isIsolatedTag(oneTag)) {
          boolean pointedSourceTokenFound = false;
          for (int i = sourceTokenIndex + 1; i < sourceTokensLength; i++) {
            if (pointedSourceTokens.contains(i)) {
              List<String> pointedSourceTokenTags = sourceTokenIndex2tags.get(i);
              if (pointedSourceTokenTags == null) {
                pointedSourceTokenTags = new ArrayList<>();
                sourceTokenIndex2tags.put(i, pointedSourceTokenTags);
              }
              pointedSourceTokenTags.add(0, oneTag);
              pointedSourceTokenFound = true;
              break;
            }
          }
          tags.remove(oneTag);
          if (tags.isEmpty()) {
            sourceTokenIndex2tags.remove(sourceTokenIndex);
          }
          if (!pointedSourceTokenFound) {
            // this can only happen for isolated tags that have no following pointed token;
            // these are assigned to end-of-sentence
            List<String> currentEosTags = sourceTokenIndex2tags.get(sourceTokensLength);
            if (currentEosTags == null) {
              currentEosTags = new ArrayList<>();
              sourceTokenIndex2tags.put(sourceTokensLength, currentEosTags);
            }
            currentEosTags.add(oneTag);
            // isolated tags at sentence end are ignored
            tagsToIgnore.add(oneTag);
          }
        } else if (isClosingTag(oneTag)) {
          for (int i = sourceTokenIndex - 1; i >= 0; i--) {
            if (pointedSourceTokens.contains(i)) {
              List<String> pointedSourceTokenTags = sourceTokenIndex2tags.get(i);
              if (pointedSourceTokenTags == null) {
                pointedSourceTokenTags = new ArrayList<>();
                sourceTokenIndex2tags.put(i, pointedSourceTokenTags);
              }
              pointedSourceTokenTags.add(oneTag);
              tags.remove(oneTag);
              if (tags.isEmpty()) {
                sourceTokenIndex2tags.remove(sourceTokenIndex);
              }
              break;
            }
          }
        }
      }
    }

    return unusedTags;
  }


  /**
   * Collect tags that are part of tag pairs over the whole sentence or isolated
   * tags at sentence beginning or end. These are to be ignored when moving tags as they get a
   * special treatment {@link #reinsertTags(String[], String[], Alignments, Map, Map)}.
   *
   * @param sourceTokenIndex2tags
   *          map of source token indexes to associated tags
   * @param closing2OpeningTag
   *          map of closing tags to corresponding opening tags
   * @param sourceTokensLength
   *          the number of source tokens
   * @return the tags to ignore
   */
  private static List<String> getTagsToIgnore(
      Map<Integer, List<String>> sourceTokenIndex2tags,
      Map<String, String> closing2OpeningTag, int sourceTokensLength) {

    List<String> tagsToIgnore = new ArrayList<>();

    List<String> sourceTagsAtBeginningOfSentence = sourceTokenIndex2tags.get(0);
    // closing tags are associated with the last token of the sentence
    List<String> sourceTagsAtEndOfSentence = sourceTokenIndex2tags.get(sourceTokensLength - 1);

    // collect tag pairs over the whole sentence
    if (sourceTagsAtEndOfSentence != null
        && sourceTagsAtBeginningOfSentence != null) {
      for (String oneTag : sourceTagsAtEndOfSentence) {
        if (isClosingTag(oneTag)) {
          String openingTag = closing2OpeningTag.get(oneTag);
          if (sourceTagsAtBeginningOfSentence.contains(openingTag)) {
            tagsToIgnore.add(openingTag);
            tagsToIgnore.add(oneTag);
          }
        }
      }
    }

    // collect isolated tags at sentence beginning
    if (sourceTagsAtBeginningOfSentence != null) {
      for (String oneTag : sourceTagsAtBeginningOfSentence) {
        if (isIsolatedTag(oneTag)) {
          tagsToIgnore.add(oneTag);
        }
      }
    }

    // collect isolated tags at sentence end;
    // isolated tag at the end of sentence are associated with the end-of-sentence after
    // the last token of the sentence
    List<String> isolatedTagsAtEndOfSentence = sourceTokenIndex2tags.get(sourceTokensLength);
    if (isolatedTagsAtEndOfSentence != null) {
      tagsToIgnore.addAll(isolatedTagsAtEndOfSentence);
    }

    return tagsToIgnore;
  }


  /**
   * Advanced version to re-insert tags from source. Takes into account the 'direction' of
   * a tag and special handling of isolated tags at the beginning and end of source sentence
   * and tag pairs over the whole sentence.
   *
   * @param sourceTokensWithoutTags
   *          list of source tokens without tags
   * @param targetTokensWithoutTags
   *          list of target tokens without tags
   * @param algn
   *          alignments of source and target tokens
   * @param sourceTokenIndex2tags
   *          map of source token indexes to associated tags
   * @param closing2OpeningTag
   *          map of closing tags to corresponding opening tags
   * @return target tokens with re-inserted tags
   */
  public static String[] reinsertTags(
      String[] sourceTokensWithoutTags, String[] targetTokensWithoutTags,
      Alignments algn, Map<Integer, List<String>> sourceTokenIndex2tags,
      Map<String, String> closing2OpeningTag) {

    // add explicit end-of-sentence marker to target sentence
    targetTokensWithoutTags =
        Arrays.copyOfRange(targetTokensWithoutTags, 0, targetTokensWithoutTags.length + 1);
    targetTokensWithoutTags[targetTokensWithoutTags.length - 1] = EOS;

    List<String> targetTokensWithTags = new ArrayList<>();

    // handle special case of isolated tag at the beginning and end of source sentence
    // and tag pairs over the whole sentence;
    // we assume that such tags refer to the whole sentence and not a specific token and
    // therefore add them at the beginning and end of the target sentence

    // collect isolated tags at beginning of sentence
    List<String> sourceTagsAtBeginningOfSentence = sourceTokenIndex2tags.get(0);
    if (sourceTagsAtBeginningOfSentence != null) {
      for (String oneSourceTag : new ArrayList<>(sourceTagsAtBeginningOfSentence)) {
        if (isIsolatedTag(oneSourceTag)) {
          targetTokensWithTags.add(oneSourceTag);
          // these tags are moved (not copied) to the target sentence
          sourceTagsAtBeginningOfSentence.remove(oneSourceTag);
        }
      }
    }

    // here we collect all tags that will be added at the sentence's end later
    List<String> targetTagsAtEndOfSentence = new ArrayList<>();

    // check for tag pairs over the whole sentence
    List<String> sourceTagsAtEndOfSentence =
        sourceTokenIndex2tags.get(sourceTokensWithoutTags.length - 1);
    if (sourceTagsAtEndOfSentence != null) {
      List<String> reversedSourceTagsAtEndOfSentence = new ArrayList<>(sourceTagsAtEndOfSentence);
      Collections.reverse(reversedSourceTagsAtEndOfSentence);
      for (String oneSourceTag : reversedSourceTagsAtEndOfSentence) {
        if (isClosingTag(oneSourceTag)
            && sourceTagsAtBeginningOfSentence != null) {
          String openingTag = closing2OpeningTag.get(oneSourceTag);
          if (sourceTagsAtBeginningOfSentence.contains(openingTag)) {
            targetTokensWithTags.add(openingTag);
            targetTagsAtEndOfSentence.add(0, oneSourceTag);
            // these tags are moved (not copied) to the target sentence
            sourceTagsAtBeginningOfSentence.remove(openingTag);
            sourceTagsAtEndOfSentence.remove(oneSourceTag);
          }
        }
      }
      if (sourceTagsAtEndOfSentence.isEmpty()) {
        sourceTokenIndex2tags.get(sourceTokensWithoutTags.length - 1);
      }
    }
    if (sourceTagsAtBeginningOfSentence != null && sourceTagsAtBeginningOfSentence.isEmpty()) {
      sourceTokenIndex2tags.remove(0);
    }

    // collect isolated tags to be added at end of target sentence
    List<String> sourceIsoTagsAtEndOfSentence =
        sourceTokenIndex2tags.get(sourceTokensWithoutTags.length);
    if (sourceIsoTagsAtEndOfSentence != null) {
      for (String oneSourceIsoTag : new ArrayList<>(sourceIsoTagsAtEndOfSentence)) {
        // only isolated tags can be assigned to end-of-sentence,
        // so no check of tag type is required
        targetTagsAtEndOfSentence.add(oneSourceIsoTag);
        // these tags are moved (not copied) to the target sentence
        sourceIsoTagsAtEndOfSentence.remove(oneSourceIsoTag);
      }
      if (sourceIsoTagsAtEndOfSentence.isEmpty()) {
        sourceTokenIndex2tags.remove(sourceTokensWithoutTags.length);
      }
    }

    // now move isolated and copy non-isolated tags from source to target
    Set<String> usedIsolatedTags = new HashSet<>();
    for (int targetTokenIndex = 0; targetTokenIndex < targetTokensWithoutTags.length;
        targetTokenIndex++) {

      String targetToken = targetTokensWithoutTags[targetTokenIndex];

      List<String> tagsToInsertBefore = new ArrayList<>();
      List<String> tagsToInsertAfter = new ArrayList<>();

      List<Integer> sourceTokenIndexes = algn.getSourceTokenIndexes(targetTokenIndex);
      for (int oneSourceTokenIndex : sourceTokenIndexes) {
        List<String> sourceTags = getTagsForSourceTokenIndex(
            oneSourceTokenIndex, sourceTokenIndex2tags, sourceTokensWithoutTags);
        for (String oneSourceTag : sourceTags) {
          if (isBackwardTag(oneSourceTag)) {
            tagsToInsertAfter.add(oneSourceTag);
          } else {
            if (isIsolatedTag(oneSourceTag)) {
              if (usedIsolatedTags.contains(oneSourceTag)) {
                continue;
              }
              usedIsolatedTags.add(oneSourceTag);
            }
            tagsToInsertBefore.add(oneSourceTag);
          }
        }
      }
      targetTokensWithTags.addAll(tagsToInsertBefore);
      if (!targetToken.equals(EOS)) {
        // don't add end-of-sentence marker
        targetTokensWithTags.add(targetToken);
      }
      targetTokensWithTags.addAll(tagsToInsertAfter);
    }

    // add end-of-sentence tags
    targetTokensWithTags.addAll(targetTagsAtEndOfSentence);

    // convert array list to array and return it
    return targetTokensWithTags.toArray(new String[targetTokensWithTags.size()]);
  }


  /**
   * Get all tags from given source tokens associated with the given source token index.
   * If the source token index points to a bpe fragments, this methods collects all tags
   * from all bpe fragments belonging to the original token.
   *
   * @param sourceTokenIndex
   *          the source token index
   * @param sourceTokenIndex2tags
   *          map of source token index to list of associated tags
   * @param sourceTokensWithoutTags
   *          the original source token sequence without tags
   *
   * @return list of tags associated with the index
   */
  private static List<String> getTagsForSourceTokenIndex(
      int sourceTokenIndex,
      Map<Integer, List<String>> sourceTokenIndex2tags,
      String[] sourceTokensWithoutTags) {

    List<String> resultTags = new ArrayList<>();

    // handle special case of index pointing to end-of-sentence of source sentence;
    // there is NO end-of-sentence token in sourceTokensWithoutTags
    if (sourceTokenIndex == sourceTokensWithoutTags.length) {
      List<String> sourceTags = sourceTokenIndex2tags.get(sourceTokenIndex);
      if (sourceTags != null) {
        resultTags = sourceTags;
      }
      return resultTags;
    }

    int currentIndex = -1;
    if (isBpeFragement(sourceTokensWithoutTags[sourceTokenIndex])) {
      currentIndex = sourceTokenIndex;
    } else if (sourceTokenIndex > 0
        && isBpeFragement(sourceTokensWithoutTags[sourceTokenIndex - 1])) {
      currentIndex = sourceTokenIndex - 1;
    }
    if (currentIndex != -1) {
      // source token index points to a bpe fragment;
      // go to first bpe fragment belonging to the token
      while (currentIndex >= 0 && isBpeFragement(sourceTokensWithoutTags[currentIndex])) {
        currentIndex--;
      }
      currentIndex++;
      // now collect tags beginning at the first bpe fragment of the token
      for (int i = currentIndex; i < sourceTokensWithoutTags.length; i++) {
        List<String> sourceTags = sourceTokenIndex2tags.get(i);
        if (sourceTags != null) {
          resultTags.addAll(sourceTokenIndex2tags.get(i));
        }
        if (!isBpeFragement(sourceTokensWithoutTags[i])) {
          // last bpe fragment found
          break;
        }
      }
    } else {
      // source token points to a non-bpe token, so just return the associated tags
      List<String> sourceTags = sourceTokenIndex2tags.get(sourceTokenIndex);
      if (sourceTags != null) {
        resultTags = sourceTags;
      }
    }

    return resultTags;
  }


  /**
   * When doing byte pair encoding, tags can end up between bpe fragments. Move opening and
   * isolated tags in front of the original token and closing tags after it.
   *
   * @param tokens
   *          the tokens
   * @return the tokens without any tags between bpe fragments
   */
  public static String[] moveTagsFromBetweenBpeFragments(String[] tokens) {

    ArrayList<String> tokenList = new ArrayList<>(Arrays.asList(tokens));

    int fragmentsStartIndex = -1;
    List<String> currentForwardTags = new ArrayList<>();
    List<String> currentBackwardTags = new ArrayList<>();
    for (int i = 0; i < tokenList.size(); i++) {
      String oneToken = tokenList.get(i);
      if (isBpeFragement(oneToken)) {
        if (fragmentsStartIndex == -1) {
          fragmentsStartIndex = i;
        }
      } else {
        if (isTag(oneToken)) {
          if (fragmentsStartIndex != -1) {
            if (isBackwardTag(oneToken)) {
              currentBackwardTags.add(oneToken);
            } else {
              currentForwardTags.add(oneToken);
            }
            tokenList.remove(i);
            i--;
          }
        } else {
          // non-tag
          if (fragmentsStartIndex != -1) {
            // we found the last fragment
            if (i < tokenList.size()) {
              tokenList.addAll(i + 1, currentBackwardTags);
            } else {
              tokenList.addAll(currentBackwardTags);
            }
            i = i + currentBackwardTags.size();
            tokenList.addAll(fragmentsStartIndex, currentForwardTags);
            i = i + currentForwardTags.size();
            fragmentsStartIndex = -1;
            currentBackwardTags.clear();
            currentForwardTags.clear();
          }
        }
      }
    }

    String[] resultAsArray = new String[tokenList.size()];
    return tokenList.toArray(resultAsArray);
  }


  /**
   * Undo byte pair encoding from given tokens.
   *
   * @param tokens
   *          the tokens
   * @return the tokens with byte pair encoding undone
   */
  public static String[] undoBytePairEncoding(String[] tokens) {

    List<String> tokenList = new ArrayList<>();

    StringBuilder currentToken = new StringBuilder();
    for (String oneToken : tokens) {
      if (oneToken.endsWith("@@")) {
        currentToken.append(oneToken.substring(0, oneToken.length() - 2));
      } else {
        currentToken.append(oneToken);
        tokenList.add(currentToken.toString());
        currentToken = new StringBuilder();
      }
    }

    String[] resultAsArray = new String[tokenList.size()];
    return tokenList.toArray(resultAsArray);
  }


  /**
   * Check if the tags in the given target sentence are inverted and try to fix them by swapping.
   * Example:
   *
   * <pre>
   * {@code
   * x <\it> y <it> z
   * }
   * </pre>
   * is changed into
   * <pre>
   * {@code
   * <it> x y z </it>
   * }
   * </pre>
   *
   * @param closing2OpeningTag
   *          map of closing tags to opening tags
   * @param targetTokensWithTags
   *          target sentence tokens with tags
   * @return target sentence tokens with handled inverted tags
   */
  public static String[] handleInvertedTags(
      Map<String, String> closing2OpeningTag, String[] targetTokensWithTags) {

    List<String> tokenList = new ArrayList<>(Arrays.asList(targetTokensWithTags));

    for (var oneEntry : closing2OpeningTag.entrySet()) {

      String openingTag = oneEntry.getValue();
      String closingTag = oneEntry.getKey();

      // flag to indicated that the current position in the token list is
      // between an opening and a closing tag
      boolean betweenTags = false;
      for (int i = 0; i < tokenList.size(); i++) {
        String oneToken = tokenList.get(i);

        if (!oneToken.equals(openingTag) && !oneToken.equals(closingTag)) {
          continue;
        }

        if (betweenTags) {
          if (isOpeningTag(oneToken)) {
            betweenTags = true;
          } else if (isClosingTag(oneToken)) {
            betweenTags = false;
          }
        } else {
          if (isOpeningTag(oneToken)) {
            // nothing to do
            betweenTags = true;
          } else if (isClosingTag(oneToken)) {
            // try to find an following opening tag and swap it with this closing tag
            boolean swapped = false;
            for (int j = i + 1; j < tokenList.size(); j++) {
              String oneFollowingToken = tokenList.get(j);
              if (isOpeningTag(oneFollowingToken)
                  && oneFollowingToken.equals(openingTag)) {
                // we found the corresponding opening tag, now swap them
                swapped = true;
                Collections.swap(tokenList, i, j);
                // move opening tag in front of the closest preceding non-tag
                int precIndex = i - 1;
                while (precIndex >= 0) {
                  Collections.swap(tokenList, precIndex, precIndex + 1);
                  if (!isTag(tokenList.get(precIndex + 1))) {
                    break;
                  }
                  precIndex--;
                }
                // move closing tag after the closest following non-tag
                int follIndex = j + 1;
                i = follIndex;
                while (follIndex < tokenList.size()) {
                  i = follIndex;
                  Collections.swap(tokenList, follIndex - 1, follIndex);
                  if (!isTag(tokenList.get(follIndex - 1))) {
                    break;
                  }
                  follIndex++;
                }
                break;
              }
            }
            if (!swapped) {
              // there is no following opening tag, so just remove the closing tag
              tokenList.remove(i);
              i--;
            }
          }
        }
      }
    }

    String[] resultAsArray = new String[tokenList.size()];
    return tokenList.toArray(resultAsArray);
  }


  /**
   * Remove all but the outer tags in tag pairs.<br/>
   * Example:
   *
   * <pre>
   * {@code
   * x <it> y <it> z a </it> b c </it>
   * }
   * </pre>
   * is changed into
   * <pre>
   * {@code
   * x <it> y z a b c </it>
   * }
   * </pre>
   *
   * @param closing2OpeningTag
   *          map of closing tags to opening tags
   * @param targetTokensWithTags
   *          target sentence tokens with tags
   * @return target sentence tokens with removed redundant tags
   */
  public static String[] removeRedundantTags(
      Map<String, String> closing2OpeningTag, String[] targetTokensWithTags) {

    List<String> tokenList = new ArrayList<>(Arrays.asList(targetTokensWithTags));

    for (var oneEntry : closing2OpeningTag.entrySet()) {

      String openingTag = oneEntry.getValue();
      String closingTag = oneEntry.getKey();

      // flag to indicated that the current position in the token list is
      // between an opening and a closing tag
      boolean betweenTags = false;
      int previousClosingTagIndex = -1;
      for (int i = 0; i < tokenList.size(); i++) {
        String oneToken = tokenList.get(i);
        if (!oneToken.equals(openingTag) && !oneToken.equals(closingTag)) {
          continue;
        }
        if (betweenTags) {
          if (isOpeningTag(oneToken)) {
            // remove opening tag
            tokenList.remove(i);
            i--;
          } else if (isClosingTag(oneToken)) {
            betweenTags = false;
            previousClosingTagIndex = i;
          }
        } else {
          if (isOpeningTag(oneToken)) {
            betweenTags = true;
            previousClosingTagIndex = -1;
          } else if (isClosingTag(oneToken)) {
            // remove previous closing tag; if available
            if (previousClosingTagIndex != -1) {
              tokenList.remove(previousClosingTagIndex);
              i--;
              previousClosingTagIndex = i;
            }
          }
        }
      }

      if (betweenTags) {
        // there is an opening tag but not a corresponding closing one, so remove the opening tag
        for (int i = tokenList.size() - 1; i >= 0; i--) {
          String oneToken = tokenList.get(i);
          if (oneToken.equals(openingTag)) {
            tokenList.remove(i);
            break;
          }
        }
      }
    }

    String[] resultAsArray = new String[tokenList.size()];
    return tokenList.toArray(resultAsArray);
  }


  /**
   * Balance tags so that they are XML conform.<br/>
   * Example 1:
   *
   * <pre>
   * {@code
   * x <it> y <b> z </it> a </b> b
   * }
   * </pre>
   * is changed into
   * <pre>
   * {@code
   * x <it> y <b> z </b> </it> <b> a </b> b
   * }
   * </pre>
   * Example 2:
   *
   * <pre>
   * {@code
   * <it> x <b> y z </it> </b> a
   * }
   * </pre>
   * is changed into
   * <pre>
   * {@code
   * <it> x <b> y z </b> </it> a
   * }
   * </pre>
   *
   * @param closing2OpeningTag
   *          map of closing tags to opening tags
   * @param targetTokensWithTags
   *          target sentence tokens with tags, potentially unbalanced
   * @return target sentence tokens with balanced tags
   */
  public static String[] balanceTags(
      Map<String, String> closing2OpeningTag, String[] targetTokensWithTags) {

    // sort consecutive sequences of opening tags
    int openingStartIndex = -1;
    for (int i = 0; i < targetTokensWithTags.length; i++) {
      if (isOpeningTag(targetTokensWithTags[i])) {
        if (openingStartIndex == -1) {
          openingStartIndex = i;
        }
      } else {
        if (openingStartIndex != -1 && i - openingStartIndex > 1) {
          //sort
          targetTokensWithTags =
              sortOpeningTags(openingStartIndex, i, targetTokensWithTags, closing2OpeningTag);
        }
        openingStartIndex = -1;
      }
    }

    // sort consecutive sequences of closing tags
    int closingStartIndex = -1;
    for (int i = 0; i < targetTokensWithTags.length; i++) {
      if (isClosingTag(targetTokensWithTags[i])) {
        if (closingStartIndex == -1) {
          closingStartIndex = i;
        }
      } else {
        if (closingStartIndex != -1 && i - closingStartIndex > 1) {
          //sort
          targetTokensWithTags =
              sortClosingTags(closingStartIndex, i, targetTokensWithTags, closing2OpeningTag);
        }
        closingStartIndex = -1;
      }
    }
    // handle closing tag sequence at end of sentence
    if (closingStartIndex != -1 && targetTokensWithTags.length - closingStartIndex > 1) {
      //sort
      targetTokensWithTags =
          sortClosingTags(
              closingStartIndex, targetTokensWithTags.length, targetTokensWithTags,
              closing2OpeningTag);
    }

    // fix overlapping tag ranges
    List<String> tokenList = new ArrayList<>(Arrays.asList(targetTokensWithTags));
    Stack<String> openingTags = new Stack<>();

    // we also need a mapping from opening to closing tag ids
    Map<String, String> opening2ClosingTag = new HashMap<>();
    for (var oneEntry : closing2OpeningTag.entrySet()) {
      opening2ClosingTag.put(oneEntry.getValue(), oneEntry.getKey());
    }
    for (int i = 0; i < tokenList.size(); i++) {
      String oneToken = tokenList.get(i);
      if (!isTag(oneToken) || isIsolatedTag(oneToken)) {
        continue;
      }
      if (isOpeningTag(oneToken)) {
        openingTags.push(oneToken);
      } else if (isClosingTag(oneToken)) {
        // check if top tag on stack matches
        // if not:
        // - pop stack until matching tag is found
        // - close all tags that have been popped BEFORE current closing tag
        // - open all tags that have been popped AFTER current closing tag
        String matchingOpeningTag = closing2OpeningTag.get(oneToken);
        Stack<String> tempStack = new Stack<>();
        while (!openingTags.peek().equals(matchingOpeningTag)) {
          tempStack.push(openingTags.pop());
        }
        for (int j = tempStack.size() - 1; j >= 0; j--) {
          tokenList.add(i, opening2ClosingTag.get(tempStack.get(j)));
        }
        for (int j = 0; j < tempStack.size(); j++) {
          tokenList.add(i + tempStack.size() + 1, tempStack.get(j));
        }
        i = i + 2 * tempStack.size();
        // remove the match opening tag of oneToken
        openingTags.pop();
        while (!tempStack.isEmpty()) {
          openingTags.push(tempStack.pop());
        }
      }
    }

    String[] resultAsArray = new String[tokenList.size()];
    return tokenList.toArray(resultAsArray);
  }


  /**
   * Sort the opening tags sequence in the given range of the given tokens in the reversed order
   * of their following corresponding closing tags.
   *
   * @param startIndex
   *          start index position of the opening tags (inclusive)
   * @param endIndex
   *          end index position of the opening tags (exclusive)
   * @param targetTokensWithTags
   *          target sentence tokens with tags
   * @param closing2OpeningTag
   *          map of closing tags to opening tags
   * @return target sentence tokens with sorted opening tags
   */
  private static String[] sortOpeningTags(
      int startIndex, int endIndex, String[] targetTokensWithTags,
      Map<String, String> closing2OpeningTag) {

    // collect opening tags
    List<String> openingTags = new ArrayList<>();
    for (int i = startIndex; i < endIndex; i++) {
      if (isOpeningTag(targetTokensWithTags[i])) {
        openingTags.add(targetTokensWithTags[i]);
      } else {
        throw new OkapiException(String.format(
            "non-opening tag \"%s\" found at position %d", targetTokensWithTags[i], i));
      }
    }

    // sort opening tags
    List<String> sortedOpeningTags = new ArrayList<>();
    for (int i = endIndex; i < targetTokensWithTags.length; i++) {
      String oneToken = targetTokensWithTags[i];
      if (isClosingTag(oneToken)) {
        String matchingOpeningTag = closing2OpeningTag.get(oneToken);
        if (openingTags.contains(matchingOpeningTag)) {
          sortedOpeningTags.add(0, matchingOpeningTag);
          openingTags.remove(matchingOpeningTag);
          if (openingTags.isEmpty()) {
            break;
          }
        }
      }
    }
    if (!openingTags.isEmpty()) {
      throw new OkapiException(String.format(
          "could not find closing tags for all %d opening tags", (endIndex - startIndex)));
    }

    // insert sorted opening tags in target tokens
    String[] targetTokensWithSortedTags =
        Arrays.copyOf(targetTokensWithTags, targetTokensWithTags.length);
    int tagIndex = 0;
    for (int i = startIndex; i < endIndex; i++) {
      targetTokensWithSortedTags[i] = sortedOpeningTags.get(tagIndex);
      tagIndex++;
    }

    return targetTokensWithSortedTags;
  }


  /**
   * Sort the closing tags sequence in the given range of the given tokens in the reversed order
   * of their preceding corresponding opening tags.
   *
   * @param startIndex
   *          start index position of the closing tags (inclusive)
   * @param endIndex
   *          end index position of the closing tags (exclusive)
   * @param targetTokensWithTags
   *          target sentence tokens with tags
   * @param closing2OpeningTag
   *          map of closing tags to opening tags
   * @return target sentence tokens with sorted closing tags
   */
  private static String[] sortClosingTags(
      int startIndex, int endIndex, String[] targetTokensWithTags,
      Map<String, String> closing2OpeningTag) {

    // collect closing tags
    List<String> closingTags = new ArrayList<>();
    for (int i = startIndex; i < endIndex; i++) {
      if (isClosingTag(targetTokensWithTags[i])) {
        closingTags.add(targetTokensWithTags[i]);
      } else {
        throw new OkapiException(String.format(
            "non-closing tag \"%s\" found at position %d", targetTokensWithTags[i], i));
      }
    }

    // sort closing tags
    List<String> sortedClosingTags = new ArrayList<>();
    outer:
    for (int i = startIndex - 1; i >= 0; i--) {
      String oneToken = targetTokensWithTags[i];
      if (isOpeningTag(oneToken)) {
        for (var oneEntry : closing2OpeningTag.entrySet()) {
          if (oneEntry.getValue().equals(oneToken)
              && closingTags.contains(oneEntry.getKey())) {
            sortedClosingTags.add(oneEntry.getKey());
            closingTags.remove(oneEntry.getKey());
            if (closingTags.isEmpty()) {
              break outer;
            }
            break;
          }
        }
      }
    }
    if (!closingTags.isEmpty()) {
      throw new OkapiException(String.format(
          "could not find opening tags for all %d closing tags", (endIndex - startIndex)));
    }

    // insert sorted closing tags in target tokens
    String[] targetTokensWithSortedTags =
        Arrays.copyOf(targetTokensWithTags, targetTokensWithTags.length);
    int tagIndex = 0;
    for (int i = startIndex; i < endIndex; i++) {
      targetTokensWithSortedTags[i] = sortedClosingTags.get(tagIndex);
      tagIndex++;
    }

    return targetTokensWithSortedTags;
  }


  /**
   * Merge tag pairs that are immediate neighbors.<br/>
   * Example:
   *
   * <pre>
   * {@code
   * x <it> y </it> <it> z a b </it>
   * }
   * </pre>
   * is changed into
   * <pre>
   * {@code
   * x <it> y z a b </it>
   * }
   * </pre>
   *
   * @param closing2OpeningTag
   *          map of closing tags to opening tags
   * @param targetTokensWithTags
   *          target sentence tokens with tags
   * @return target sentence tokens with merged neighbor tags
   */
  public static String[] mergeNeighborTagPairs(
      Map<String, String> closing2OpeningTag, String[] targetTokensWithTags) {

    List<String> tokenList = new ArrayList<>(Arrays.asList(targetTokensWithTags));

    for (var oneEntry : closing2OpeningTag.entrySet()) {

      String openingTag = oneEntry.getValue();
      String closingTag = oneEntry.getKey();

      for (int i = 1; i < tokenList.size(); i++) {
        String prevToken = tokenList.get(i - 1);
        String oneToken = tokenList.get(i);

        if (oneToken.equals(openingTag) && prevToken.equals(closingTag)) {
          tokenList.remove(i);
          tokenList.remove(i - 1);
          i = i - 2;
        }
      }
    }

    String[] resultAsArray = new String[tokenList.size()];
    return tokenList.toArray(resultAsArray);
  }


  /**
   * Add given unused tags at end of sentence.
   *
   * @param targetTokensWithTags
   *          target sentence tokens with tags
   * @param unusedTags
   *          unused tags to add
   * @return target sentence tokens with unused tags added
   */
  public static String[] addUnusedTags(String[] targetTokensWithTags, List<String> unusedTags) {

    List<String> tokenList = new ArrayList<>(Arrays.asList(targetTokensWithTags));

    for (String oneTag : unusedTags) {
      tokenList.add(oneTag);
    }

    String[] resultAsArray = new String[tokenList.size()];
    return tokenList.toArray(resultAsArray);
  }


  /**
   * Embed each Okapi tag with last/first character of preceding/following token (if available).
   * This makes sure that the detokenizer in postprocessing works correctly.
   *
   * @param targetTokensWithTags
   *          the target tokens with Okapi tags
   * @return string with embedded Okapi tags
   */
  public static String maskTags(String[] targetTokensWithTags) {

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
   * Undo the tag masking of {@link #maskTags(String[])}.
   *
   * @param postprocessedSentence
   *          the postprocessed sentence
   * @return the unmasked postprocessed sentence
   */
  @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
  public static String unmaskTags(String postprocessedSentence) {

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
   * left/right of the tags, depending on the type of tag. Opening and isolated tags have all
   * spaces to their right removed, closing tags have all spaces to their left removed.
   *
   * @param postprocessedSentence
   *          the postprocessed sentence with re-inserted tags
   * @return the postprocessed sentence with detokenized tags
   */
  @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
  public static String detokenizeTags(String postprocessedSentence) {

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

    Pattern isolatedTag = Pattern.compile("(\uE103.)( )+");
    Matcher isoMatcher = isolatedTag.matcher(sb.toString());
    sb = new StringBuilder();
    while (isoMatcher.find()) {
      isoMatcher.appendReplacement(sb, isoMatcher.group(1));
    }
    isoMatcher.appendTail(sb);

    return sb.toString();
  }


  /**
   * Check if given token is a bpe fragment.
   *
   * @param token
   *          the token
   * @return {@code true}if token is bpe fragment
   */
  public static boolean isBpeFragement(String token) {

    return token.endsWith("@@");
  }


  /**
   * Create table with source and target sentence tokens with index and alignments.
   *
   * @param sourceTokensWithTags
   *          the source sentence tokens with tags
   * @param targetTokensWithTags
   *          the target sentence tokens with tags
   * @param algn
   *          the hard alignments
   * @return the table as string
   */
  public static String createSentenceAlignments(
      String[] sourceTokensWithTags, String[] targetTokensWithTags, Alignments algn) {

    StringBuilder result = new StringBuilder();
    result.append(String.format("%s%n", algn.toString()));

    // get max source token length
    int maxSourceTokenLength = "source:".length();
    for (String oneToken : sourceTokensWithTags) {
      if (oneToken.length() > maxSourceTokenLength) {
        maxSourceTokenLength = oneToken.length();
      }
    }
    // get max target token length
    int maxTargetTokenLength = "target:".length();
    for (String oneToken : targetTokensWithTags) {
      if (oneToken.length() > maxTargetTokenLength) {
        maxTargetTokenLength = oneToken.length();
      }
    }

    result.append(
        String.format(
            "%" + maxTargetTokenLength + "s   \t\t\t   %" + maxSourceTokenLength + "s%n",
            "TARGET:", "SOURCE:"));
    for (int i = 0;
        i < Math.max(targetTokensWithTags.length, sourceTokensWithTags.length);
        i++) {
      if (i < targetTokensWithTags.length) {
        result.append(
            String.format("%" + maxTargetTokenLength + "s %2d\t\t\t",
                targetTokensWithTags[i], i));
      } else {
        result.append(String.format("%" + (maxTargetTokenLength + 3) + "s\t\t\t", " "));
      }
      if (i < sourceTokensWithTags.length) {
        result.append(
            String.format("%2d %" + maxSourceTokenLength + "s\t\t\t%n",
                i, sourceTokensWithTags[i]));
      } else {
        result.append(String.format("%n"));
      }
    }

    return result.toString();
  }
}
