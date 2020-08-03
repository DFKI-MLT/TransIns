package de.dfki.mlt.transins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    logger.debug(String.format("translating from %s to %s",
        super.getSourceLanguage(), super.getTargetLanguage()));

    open();

    super.result = null;
    super.current = -1;
    try {
      // check if there is actually text to translate
      if (!fragment.hasText(false)) {
        return 0;
      }
      logger.debug(String.format("source sentence: \"%s\"", fragment.getCodedText()));

      // preprocessing
      String sentence = fragment.getCodedText();
      String preprocessedSourceSentence =
          this.prepostClient.process(
              super.getSourceLanguage().toString(),
              sentence,
              Mode.PREPROCESS,
              this.params.getPrePostHost(),
              this.params.getPrePostPort());
      logger.debug(String.format("preprocessed source sentence: \"%s\"",
          preprocessedSourceSentence));

      // translate
      String translatorInput = removeCodes(preprocessedSourceSentence);
      // add leading token with target language
      translatorInput = String.format("<to%s> %s", super.getTargetLanguage(), translatorInput);
      logger.debug(String.format("send to translator: \"%s\"", translatorInput));
      String translatorResponse = this.translatorClient.send(translatorInput);

      // split into translation and alignments
      String[] parts = translatorResponse.split(" \\|\\|\\| ");
      String translation = null;

      boolean hasMarkup = fragment.hasCode();
      if (parts.length == 2) {
        // if markup and alignments are available, re-insert markup
        translation = parts[0].trim();
        String rawAlignments = parts[1].trim();
        logger.debug(String.format("raw target sentence: \"%s\"", translation));
        logger.debug(String.format("raw alignments: \"%s\"", rawAlignments));
        Alignments algn = createAlignments(rawAlignments);
        // compensate for leading target language token in source sentence
        algn.shiftSourceIndexes(-1);
        if (hasMarkup) {
          // re-insert markup
          String[] sourceTokensWithMarkup = preprocessedSourceSentence.split(" ");
          String[] targetTokens = translation.split(" ");

          // print alignments
          String[] sourceTokensWithoutMarkup = removeCodes(preprocessedSourceSentence).split(" ");
          logger.debug(String.format("sentence alignments:%n%s", createSentenceAlignments(
              sourceTokensWithoutMarkup, targetTokens, algn)));

          Map<Integer, Integer> closing2OpeningTagIdMap =
              createTagIdMapping(preprocessedSourceSentence);

          String[] targetTokensWithMarkup = null;
          targetTokensWithMarkup =
              reinsertMarkup(
                  sourceTokensWithMarkup, sourceTokensWithoutMarkup, targetTokens, algn);

          // make sure markup is not between bpe fragments
          targetTokensWithMarkup = moveMarkupBetweenBpeFragments(targetTokensWithMarkup);

          // make sure markup is balanced
          balanceTags(closing2OpeningTagIdMap, targetTokensWithMarkup);

          // prepare translation for postprocessing;
          // mask tags so that detokenizer in postprocessing works correctly
          translation = maskMarkup(targetTokensWithMarkup);
        }
      } else {
        translation = translatorResponse;
        logger.debug(String.format("raw target sentence: \"%s\"", translation));
      }

      // postprocessing
      String postprocessedSentence =
          this.prepostClient.process(
              super.getTargetLanguage().toString(),
              translation,
              Mode.POSTPROCESS,
              this.params.getPrePostHost(),
              this.params.getPrePostPort());

      if (hasMarkup) {
        // unmask markup
        postprocessedSentence = unmaskMarkup(postprocessedSentence);
        // remove space in front of and after markup
        postprocessedSentence = detokenizeMarkup(postprocessedSentence);
        // add a space at the end, otherwise the next sentence immediately starts after this one
        // TODO deactivate segmentation step in translator and do the handling of sentences here
        postprocessedSentence = postprocessedSentence + " ";

        // print target sentence with human readable markup
        TextFragment postFragment = new TextFragment();
        postFragment.setCodedText(postprocessedSentence, fragment.getClonedCodes(), true);
        logger.debug(String.format("postprocessed target sentence with markup: \"%s\"",
            this.util.toCodedHTML(postFragment)));
      } else {
        logger.debug(String.format("postprocessed target sentence: \"%s\"", postprocessedSentence));
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
    return removeCodes(codedText);
  }


  /**
   * Convert from coded text to text without codes.
   *
   * @param codedText
   *          the coded text to convert
   * @return the resulting string
   */
  public static String removeCodes(String codedText) {

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < codedText.length(); i++) {
      switch (codedText.charAt(i)) {
        case TextFragment.MARKER_OPENING:
        case TextFragment.MARKER_CLOSING:
        case TextFragment.MARKER_ISOLATED:
          // skip the second markup character and the following space
          i = i + 2;
          break;
        default:
          sb.append(codedText.charAt(i));
      }
    }
    return sb.toString();
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
   * Create mapping of closing tag ids to opening tag ids from the given preprocessed source
   * sentence. It is assumed that the tags are balanced.
   *
   * @param preprocessedSourceSentence
   *          the preprocessed source sentence
   * @return map of closing tag ids to opening tag ids
   */
  public static Map<Integer, Integer> createTagIdMapping(String preprocessedSourceSentence) {

    Map<Integer, Integer> resultMap = new HashMap<>();

    Stack<Integer> openingIdsStack = new Stack<>();

    String[] tokens = preprocessedSourceSentence.split(" ");
    for (String oneToken : tokens) {
      if (isOpeningTag(oneToken)) {
        openingIdsStack.push(getTagId(oneToken));
      } else if (isClosingTag(oneToken)) {
        int closingTagId = getTagId(oneToken);
        int openingTagId = openingIdsStack.pop();
        resultMap.put(closingTagId, openingTagId);
      }
    }

    return resultMap;
  }


  /**
   * Advanced version to re-insert markup from source. Takes into account the 'direction' of
   * a tag and special handling of isolated tags at sentence beginning.
   *
   * @param sourceTokensWithMarkup
   *          list of source tokens, including the 2-character markup encoding used by
   *          Okapi
   * @param sourceTokensWithoutMarkup
   *          list of source tokens without markup
   * @param targetTokens
   *          list of target tokens (without any markup)
   * @param algn
   *          hard alignments of source and target tokens
   * @return target tokens with re-inserted markup
   */
  public static String[] reinsertMarkup(
      String[] sourceTokensWithMarkup, String[] sourceTokensWithoutMarkup,
      String[] targetTokens, Alignments algn) {

    List<String> targetTokensWithMarkup = new ArrayList<>();

    Map<Integer, List<String>> sourceTokenIndex2tags =
        createSourceTokenIndex2Tags(sourceTokensWithMarkup);

    // handle special case of isolated tag at the beginning of source sentence;
    // we assume that such tags refer to the whole sentence and not a specific token and
    // therefore add them at the beginning of the target sentence
    List<String> sourceTagsAtBeginningOfSentence = sourceTokenIndex2tags.get(0);
    if (sourceTagsAtBeginningOfSentence != null) {
      for (String oneSourceTag : new ArrayList<>(sourceTagsAtBeginningOfSentence)) {
        if (oneSourceTag.charAt(0) == TextFragment.MARKER_ISOLATED) {
          targetTokensWithMarkup.add(oneSourceTag);
          sourceTagsAtBeginningOfSentence.remove(oneSourceTag);
        }
      }
      if (sourceTagsAtBeginningOfSentence.isEmpty()) {
        sourceTokenIndex2tags.remove(0);
      }
    }

    for (int targetTokenIndex = 0; targetTokenIndex < targetTokens.length; targetTokenIndex++) {

      String targetToken = targetTokens[targetTokenIndex];

      List<String> tagsToInsertBefore = new ArrayList<>();
      List<String> tagsToInsertAfter = new ArrayList<>();

      List<Integer> sourceTokenIndexes = algn.getSourceTokenIndexes(targetTokenIndex);
      for (int oneSourceTokenIndex : sourceTokenIndexes) {
        List<String> sourceTags = getTagsForSourceTokenIndex(
            oneSourceTokenIndex, sourceTokenIndex2tags, sourceTokensWithoutMarkup);
        if (sourceTags != null) {
          for (String oneSourceTag : sourceTags) {
            if (isBackwardTag(oneSourceTag)) {
              tagsToInsertAfter.add(oneSourceTag);
            } else {
              tagsToInsertBefore.add(oneSourceTag);
            }
          }
        }
      }
      targetTokensWithMarkup.addAll(tagsToInsertBefore);
      targetTokensWithMarkup.add(targetToken);
      targetTokensWithMarkup.addAll(tagsToInsertAfter);
    }

    // get EOS markup from source sentence
    int eosTokenIndex = sourceTokensWithoutMarkup.length;
    List<String> eosTags = sourceTokenIndex2tags.get(eosTokenIndex);
    if (eosTags != null) {
      targetTokensWithMarkup.addAll(eosTags);
      sourceTokenIndex2tags.remove(eosTokenIndex);
    }

    // add any remaining tags
    if (!sourceTokenIndex2tags.isEmpty()) {
      List<Integer> keys = new ArrayList<>(sourceTokenIndex2tags.keySet());
      Collections.sort(keys);
      for (Integer oneKey : keys) {
        targetTokensWithMarkup.addAll(sourceTokenIndex2tags.get(oneKey));
      }
    }

    // convert array list to array and return it
    return targetTokensWithMarkup.toArray(new String[targetTokensWithMarkup.size()]);
  }


  /**
   * Advanced version of creating a mapping from indexes to markup tags. Take into account the
   * 'direction' of a tag, i.e. isolated and opening tags are assigned to the <b>next</b> token,
   * while a closing tag is assigned to the <b>previous</b> token.
   *
   * @param sourceTokensWithMarkup
   *          the source tokens with markup
   * @return the mapping
   */
  private static Map<Integer, List<String>> createSourceTokenIndex2Tags(
      String[] sourceTokensWithMarkup) {

    Map<Integer, List<String>> index2tags = new HashMap<>();

    int offset = 0;

    for (int i = 0; i < sourceTokensWithMarkup.length; i++) {
      String currentToken = sourceTokensWithMarkup[i];
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
        currentTags.add(sourceTokensWithMarkup[i]);
        offset = offset + 1;
      }
    }

    return index2tags;
  }


  /**
   * Get all tags from given source tokens associated with the given source token index.
   * If the source token index points to a bpe fragments, this methods collects all tags
   * from all bpe fragments belonging to the original token.<br>
   * All collected tags are removed from the given sourceTokenIndex2tags map.
   *
   * @param sourceTokenIndex
   *          the source token index
   * @param sourceTokenIndex2tags
   *          map of source token index to list of associated tags
   * @param sourceTokensWithoutMarkup
   *          the original source token sequence without markup
   *
   * @return list of tags associated with the index
   */
  private static List<String> getTagsForSourceTokenIndex(
      int sourceTokenIndex,
      Map<Integer, List<String>> sourceTokenIndex2tags,
      String[] sourceTokensWithoutMarkup) {

    List<String> resultTags = new ArrayList<>();

    // handle special case of index pointing to EOS of source sentence;
    // there is NO token for EOS in sourceTokensWithoutMarkup
    if (sourceTokenIndex == sourceTokensWithoutMarkup.length) {
      List<String> sourceTags = sourceTokenIndex2tags.get(sourceTokenIndex);
      if (sourceTags != null) {
        resultTags = sourceTags;
        sourceTokenIndex2tags.remove(sourceTokenIndex);
      }
      return resultTags;
    }

    int currentIndex = -1;
    if (isBpeFragement(sourceTokensWithoutMarkup[sourceTokenIndex])) {
      currentIndex = sourceTokenIndex;
    } else if (sourceTokenIndex > 0
        && isBpeFragement(sourceTokensWithoutMarkup[sourceTokenIndex - 1])) {
      currentIndex = sourceTokenIndex - 1;
    }
    if (currentIndex != -1) {
      // source token index points to a bpe fragment;
      // go to first bpe fragment belonging to the token
      while (currentIndex >= 0 && isBpeFragement(sourceTokensWithoutMarkup[currentIndex])) {
        currentIndex--;
      }
      currentIndex++;
      // now collect tags beginning at the first bpe fragment of the token
      for (int i = currentIndex; i < sourceTokensWithoutMarkup.length; i++) {
        List<String> sourceTags = sourceTokenIndex2tags.get(i);
        if (sourceTags != null) {
          resultTags.addAll(sourceTokenIndex2tags.get(i));
          sourceTokenIndex2tags.remove(i);
        }
        if (!isBpeFragement(sourceTokensWithoutMarkup[i])) {
          // last bpe fragment found
          break;
        }
      }
    } else {
      // source token points to a non-bpe token, so just return the associated tags and
      // remove them from the map
      List<String> sourceTags = sourceTokenIndex2tags.get(sourceTokenIndex);
      if (sourceTags != null) {
        resultTags = sourceTags;
        sourceTokenIndex2tags.remove(sourceTokenIndex);
      }
    }

    return resultTags;
  }


  /**
   * Checks if the tags in the given target sentence are balanced. If not, swaps opening and closing
   * tags.
   *
   * @param closing2OpeningTagIdMap
   *          map of closing tag ids to opening tag ids
   * @param targetTokensWithMarkup
   *          target sentence tokens
   * @return target sentence tokens with balanced tags
   */
  public static String[] balanceTags(
      Map<Integer, Integer> closing2OpeningTagIdMap, String[] targetTokensWithMarkup) {

    List<Integer> currentOpeningIds = new ArrayList<>();
    outer:
    while (true) {
      boolean swapped = false;
      for (int i = 0; i < targetTokensWithMarkup.length; i++) {
        String oneToken = targetTokensWithMarkup[i];
        if (isOpeningTag(oneToken)) {
          currentOpeningIds.add(getTagId(oneToken));
        } else if (isClosingTag(oneToken)) {
          // get opening tag id
          int openingTagId = closing2OpeningTagIdMap.get(getTagId(oneToken));
          if (!currentOpeningIds.contains(openingTagId)) {
            // tags have to be swapped
            for (int j = i + 1; j < targetTokensWithMarkup.length; j++) {
              String oneFollowingToken = targetTokensWithMarkup[j];
              if (isOpeningTag(oneFollowingToken)
                  && getTagId(oneFollowingToken) == openingTagId) {
                // we found the corresponding opening tag, now swap them
                swap(targetTokensWithMarkup, i, j);
                // move opening tag in front of the closest preceding non-tag;
                // tag must NOT end between bpe fragments
                int precIndex = i - 1;
                while (precIndex >= 0) {
                  swap(targetTokensWithMarkup, precIndex, precIndex + 1);
                  if (!isBetweenBpeFragments(targetTokensWithMarkup, precIndex)) {
                    break;
                  }
                  precIndex--;
                }
                // move closing tag after the closest following non-tag;
                // tag must NOT end between bpe fragments
                int follIndex = j + 1;
                while (follIndex < targetTokensWithMarkup.length) {
                  swap(targetTokensWithMarkup, follIndex - 1, follIndex);
                  if (!isBetweenBpeFragments(targetTokensWithMarkup, follIndex)) {
                    break;
                  }
                  follIndex++;
                }
              }
            }
            swapped = true;
            break;
          }
        }
      }
      if (!swapped) {
        break outer;
      }
    }

    return targetTokensWithMarkup;
  }


  private static void swap(String[] array, int firstIndex, int secondIndex) {

    String temp = array[firstIndex];
    array[firstIndex] = array[secondIndex];
    array[secondIndex] = temp;
  }


  /**
   * Check if token at the given index is between bpe fragments.
   *
   * @param targetTokensWithMarkup
   *          the tokens
   * @param tokenIndex
   *          the index of the token to check
   * @return {@code true} if between bpe fragments, {@code false} otherwise
   */
  private static boolean isBetweenBpeFragments(
      String[] targetTokensWithMarkup, int tokenIndex) {

    if (tokenIndex == 0
        || tokenIndex == targetTokensWithMarkup.length - 1) {
      // token at beginning or end cannot be between bpe fragments
      return false;
    }

    if (isBpeFragement(targetTokensWithMarkup[tokenIndex - 1])) {
      return true;
    }

    return false;
  }


  /**
   * When doing byte pair encoding, markup can end up between bpe fragments. Move opening and
   * isolated markup in front of the original token and closing markup after it.
   *
   * @param tokens
   *          the tokens
   * @return the tokens without any markup between bpe fragments
   */
  public static String[] moveMarkupBetweenBpeFragments(String[] tokens) {

    ArrayList<String> tokenList = new ArrayList<>(Arrays.asList(tokens));

    // start at 1, as the first token cannot be between two bpe fragments
    for (int i = 1; i < tokenList.size(); i++) {
      String oneToken = tokenList.get(i);
      if (isTag(oneToken) && isBpeFragement(tokenList.get(i - 1))) {
        String removedToken = tokenList.remove(i);
        if (isBackwardTag(oneToken)) {
          // get index of closest following token that is not a bpe fragment;
          // attention: The last bpe fragment has no @@ ending!
          for (int nextIndex = i; nextIndex < tokenList.size(); nextIndex++) {
            if (isBpeFragement(tokenList.get(nextIndex))
                || isTag(tokenList.get(nextIndex))) {
              continue;
            }
            tokenList.add(nextIndex + 1, removedToken);
            i--;
            break;
          }
        } else {
          // get index of the closest previous token that is not a bpe fragment
          boolean swapCompleted = false;
          for (int prevIndex = i - 1; prevIndex >= 0; prevIndex--) {
            if (isBpeFragement(tokenList.get(prevIndex))) {
              continue;
            }
            tokenList.add(prevIndex + 1, removedToken);
            swapCompleted = true;
            break;
          }
          if (!swapCompleted) {
            // bpe fragment at beginning of sentence, so add markup in front of it
            tokenList.add(0, removedToken);
          }
        }
      }
    }

    String[] resultAsArray = new String[tokenList.size()];
    return tokenList.toArray(resultAsArray);
  }


  /**
   * Embed each Okapi tag with last/first character of preceding/following token (if available).
   * This makes sure that the detokenizer in postprocessing works correctly.
   *
   * @param targetTokensWithMarkup
   *          the target tokens with markup
   * @return string with embedded Okapi tags
   */
  public static String maskMarkup(String[] targetTokensWithMarkup) {

    StringBuilder result = new StringBuilder();

    for (int i = 0; i < targetTokensWithMarkup.length; i++) {
      String currentToken = targetTokensWithMarkup[i];
      if (isTag(currentToken)) {
        for (int j = i - 1; j >= 0; j--) {
          String precedingToken = targetTokensWithMarkup[j];
          if (!isTag(precedingToken)) {
            currentToken = currentToken + precedingToken.charAt(precedingToken.length() - 1);
            break;
          }
        }
        for (int j = i + 1; j < targetTokensWithMarkup.length; j++) {
          String followingToken = targetTokensWithMarkup[j];
          if (!isTag(followingToken)) {
            currentToken = followingToken.charAt(0) + currentToken;
            break;
          }
        }
      }
      result.append(currentToken + " ");
    }
    return result.toString().trim();
  }


  /**
   * Undo the markup masking of {@link #maskMarkup(String[])}.
   *
   * @param postprocessedSentence
   *          the postprocessed sentence
   * @return the unmasked postprocessed sentence
   */
  @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
  public static String unmaskMarkup(String postprocessedSentence) {

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
   * Remove from the given postprocessed sentence with re-inserted markup the spaces to the
   * left/right of the markup, depending on the type of markup. Opening and isolated markup have all
   * spaces to their right removed, closing markups have all spaces to their left removed.
   *
   * @param postprocessedSentence
   *          the postprocessed sentence with re-inserted markup
   * @return the postprocessed sentence with detokenized markup
   */
  @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
  public static String detokenizeMarkup(String postprocessedSentence) {

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
   * Check if given token is an Okapi tag.
   *
   * @param token
   *          the token
   * @return {@code true}if token is Okapi tag
   */
  public static boolean isTag(String token) {

    return token.charAt(0) == TextFragment.MARKER_OPENING
        || token.charAt(0) == TextFragment.MARKER_CLOSING
        || token.charAt(0) == TextFragment.MARKER_ISOLATED;
  }


  /**
   * Check if the given token is an opening Okapi tag.
   *
   * @param token
   *          the token
   * @return {@code true} if token is opening Okapi tag
   */
  public static boolean isOpeningTag(String token) {

    return token.charAt(0) == TextFragment.MARKER_OPENING;
  }


  /**
   * Check if the given token is a closing Okapi tag.
   *
   * @param token
   *          the token
   * @return {@code true} if token is closing Okapi tag
   */
  public static boolean isClosingTag(String token) {

    return token.charAt(0) == TextFragment.MARKER_CLOSING;
  }


  /**
   * Return the id of the given tag
   *
   * @param tag
   *          the tag
   * @return the id or -1 if none
   */
  public static int getTagId(String tag) {

    if (!isTag(tag)) {
      return -1;
    }

    return TextFragment.toIndex(tag.charAt(1));
  }


  /**
   * Check if given token is an Okapi backward tag.
   *
   * @param token
   *          the token
   * @return {@code true}if token is backward tag
   */
  public static boolean isBackwardTag(String token) {

    return token.charAt(0) == TextFragment.MARKER_CLOSING;
  }


  /**
   * Check if given token is an Okapi forward tag
   *
   * @param token
   *          the token
   * @return {@code true}if token is forward tag
   */
  public static boolean isForwardTag(String token) {

    return token.charAt(0) == TextFragment.MARKER_OPENING
        || token.charAt(0) == TextFragment.MARKER_ISOLATED;
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
   * @param sourceTokensWithMarkup
   *          the source sentence tokens with markup
   * @param targetTokensWithMarkup
   *          the target sentence tokens with markup
   * @param algn
   *          the hard alignments
   * @return the table as string
   */
  public static String createSentenceAlignments(
      String[] sourceTokensWithMarkup, String[] targetTokensWithMarkup, Alignments algn) {

    StringBuilder result = new StringBuilder();
    result.append(String.format("%s%n", algn.toString()));

    // get max source token length
    int maxSourceTokenLength = "source:".length();
    for (String oneToken : sourceTokensWithMarkup) {
      if (oneToken.length() > maxSourceTokenLength) {
        maxSourceTokenLength = oneToken.length();
      }
    }
    // get max target token length
    int maxTargetTokenLength = "target:".length();
    for (String oneToken : targetTokensWithMarkup) {
      if (oneToken.length() > maxTargetTokenLength) {
        maxTargetTokenLength = oneToken.length();
      }
    }

    result.append(
        String.format(
            "%" + maxTargetTokenLength + "s   \t\t\t   %" + maxSourceTokenLength + "s%n",
            "TARGET:", "SOURCE:"));
    for (int i = 0;
        i < Math.max(targetTokensWithMarkup.length, sourceTokensWithMarkup.length);
        i++) {
      if (i < targetTokensWithMarkup.length) {
        result.append(
            String.format("%" + maxTargetTokenLength + "s %2d\t\t\t",
                targetTokensWithMarkup[i], i));
      } else {
        result.append(String.format("%" + (maxTargetTokenLength + 3) + "s\t\t\t", " "));
      }
      if (i < sourceTokensWithMarkup.length) {
        result.append(
            String.format("%2d %" + maxSourceTokenLength + "s\t\t\t%n",
                i, sourceTokensWithMarkup[i]));
      } else {
        result.append(String.format("%n"));
      }
    }

    return result.toString();
  }


  /**
   * Replace Okapi markup with human readable tags in given String array
   *
   * @param targetTokensWithMarkup
   *          string array with tokens
   * @return string array with human readable tags
   */
  public static String[] replaceOkapiMarkup(String[] targetTokensWithMarkup) {

    String[] resultTokens = new String[targetTokensWithMarkup.length];

    int index = -1;
    for (String oneToken : targetTokensWithMarkup) {
      index++;
      if (oneToken.charAt(0) == TextFragment.MARKER_ISOLATED) {
        resultTokens[index] = String.format("<iso%d/>",
            TextFragment.toIndex(oneToken.charAt(1)));
      } else if (oneToken.charAt(0) == TextFragment.MARKER_OPENING) {
        resultTokens[index] = String.format("<u%d>",
            TextFragment.toIndex(oneToken.charAt(1)));
      } else if (oneToken.charAt(0) == TextFragment.MARKER_CLOSING) {
        resultTokens[index] = String.format("</u%d>",
            TextFragment.toIndex(oneToken.charAt(1)));
      } else {
        resultTokens[index] = oneToken;
}
    }

    return resultTokens;
  }
}
