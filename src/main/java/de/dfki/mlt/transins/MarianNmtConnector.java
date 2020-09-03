package de.dfki.mlt.transins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

    logger.debug("translating from {} to {}", super.getSourceLanguage(), super.getTargetLanguage());

    open();

    super.result = null;
    super.current = -1;
    try {
      // check if there is actually text to translate
      if (!fragment.hasText(false)) {
        return 0;
      }
      logger.debug("source sentence: \"{}\"", fragment.getCodedText());

      // preprocessing
      String sentence = fragment.getCodedText();
      String preprocessedSourceSentence =
          this.prepostClient.process(
              super.getSourceLanguage().toString(),
              sentence,
              Mode.PREPROCESS,
              this.params.getPrePostHost(),
              this.params.getPrePostPort());
      logger.debug("preprocessed source sentence: \"{}\"", preprocessedSourceSentence);

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
        translation = parts[0].trim();
        String rawAlignments = parts[1].trim();
        logger.debug("raw target sentence: \"{}\"", translation);
        logger.debug("raw alignments: \"{}\"", rawAlignments);
        Alignments algn = createAlignments(rawAlignments);
        // compensate for leading target language token in source sentence
        algn.shiftSourceIndexes(-1);
        if (hasTags) {

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
          moveSourceTagsToPointedTokens(sourceTokenIndex2tags, closing2OpeningTag,
              algn.getPointedSourceTokens(), sourceTokensWithoutTags.length);

          String[] targetTokensWithTags = reinsertTags(
              sourceTokensWithoutTags, targetTokensWithoutTags, algn, sourceTokenIndex2tags);


          // make sure tags are not between bpe fragments
          targetTokensWithTags = moveTagsFromBetweenBpeFragments(targetTokensWithTags);

          // clean up tags
          handleInvertedTags(closing2OpeningTag, targetTokensWithTags);
          removeRedundantTags(closing2OpeningTag, targetTokensWithTags);

          // prepare translation for postprocessing;
          // mask tags so that detokenizer in postprocessing works correctly
          translation = maskTags(targetTokensWithTags);
        }
      } else {
        translation = translatorResponse;
        logger.debug("raw target sentence: \"{}\"", translation);
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
   * <li>if there is no pointing token between opening and closing tag, move both to eos
   * <li>move opening tags forwards until pointing token
   * <li>move closing tags backwards until pointing token
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
   */
  public static void moveSourceTagsToPointedTokens(
      Map<Integer, List<String>> sourceTokenIndex2tags,
      Map<String, String> closing2OpeningTag,
      List<Integer> pointedSourceTokens,
      int sourceTokensLength) {

    // for each closing tag of non-pointed source tokens, check if there is
    // a pointed source on the way to the corresponding opening tag;
    // if not remove the tag pair (i.e. move to end-of-sentence)
    List<String> eosTags = new ArrayList<>();
    for (var oneEntry : new HashSet<>(sourceTokenIndex2tags.entrySet())) {
      int sourceTokenIndex = oneEntry.getKey();
      if (pointedSourceTokens.contains(sourceTokenIndex)) {
        continue;
      }

      List<String> tags = oneEntry.getValue();
      for (String oneTag : new ArrayList<>(tags)) {
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
            eosTags.add(oneTag);
            eosTags.add(0, openingTag);
          }
        }
      }
    }

    // at this point, all remaining tags are either isolated or have at least on pointing
    // token between the opening and closing tag;
    // now move opening and isolated tags (when not at sentence beginning) to the
    // following pointed token and closing tags to the preceding pointed token
    for (var oneEntry : new HashSet<>(sourceTokenIndex2tags.entrySet())) {
      int sourceTokenIndex = oneEntry.getKey();
      if (pointedSourceTokens.contains(sourceTokenIndex)) {
        continue;
      }

      List<String> tags = oneEntry.getValue();
      for (String oneTag : new ArrayList<>(tags)) {
        if (isOpeningTag(oneTag)
            || (isIsolatedTag(oneTag) && sourceTokenIndex > 0)) {
          for (int i = sourceTokenIndex + 1; i < sourceTokensLength; i++) {
            if (pointedSourceTokens.contains(i)) {
              List<String> pointedSourceTokenTags = sourceTokenIndex2tags.get(i);
              if (pointedSourceTokenTags == null) {
                pointedSourceTokenTags = new ArrayList<>();
                sourceTokenIndex2tags.put(i, pointedSourceTokenTags);
              }
              pointedSourceTokenTags.add(0, oneTag);
              tags.remove(oneTag);
              if (tags.isEmpty()) {
                sourceTokenIndex2tags.remove(sourceTokenIndex);
              }
              break;
            }
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

    // add end-of-sentence tags;
    // Okapi will complain if tags from source sentence are missing
    List<String> currentEosTags = sourceTokenIndex2tags.get(sourceTokensLength);
    if (currentEosTags == null) {
      currentEosTags = new ArrayList<>();
      sourceTokenIndex2tags.put(sourceTokensLength, currentEosTags);
    }
    for (String oneTag : eosTags) {
      if (isClosingTag(oneTag)) {
        currentEosTags.add(oneTag);
      } else {
        currentEosTags.add(0, oneTag);
      }
    }
  }


  /**
   * Advanced version to re-insert tags from source. Takes into account the 'direction' of
   * a tag and special handling of isolated tags at sentence beginning.
   *
   * @param sourceTokensWithoutTags
   *          list of source tokens without tags
   * @param targetTokensWithoutTags
   *          list of target tokens without tags
   * @param algn
   *          alignments of source and target tokens
   * @param sourceTokenIndex2tags
   *          map of source token indexes to associated tags
   * @return target tokens with re-inserted tags
   */
  public static String[] reinsertTags(
      String[] sourceTokensWithoutTags, String[] targetTokensWithoutTags,
      Alignments algn, Map<Integer, List<String>> sourceTokenIndex2tags) {

    List<String> targetTokensWithTags = new ArrayList<>();

    // handle special case of isolated tag at the beginning of source sentence;
    // we assume that such tags refer to the whole sentence and not a specific token and
    // therefore add them at the beginning of the target sentence
    List<String> sourceTagsAtBeginningOfSentence = sourceTokenIndex2tags.get(0);
    if (sourceTagsAtBeginningOfSentence != null) {
      for (String oneSourceTag : new ArrayList<>(sourceTagsAtBeginningOfSentence)) {
        if (isIsolatedTag(oneSourceTag)) {
          targetTokensWithTags.add(oneSourceTag);
          // these tags are moved (not copied) to the target sentence
          sourceTagsAtBeginningOfSentence.remove(oneSourceTag);
        }
      }
      if (sourceTagsAtBeginningOfSentence.isEmpty()) {
        sourceTokenIndex2tags.remove(0);
      }
    }

    // now copy (not move) tags from source to target
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
            tagsToInsertBefore.add(oneSourceTag);
          }
        }
      }
      targetTokensWithTags.addAll(tagsToInsertBefore);
      targetTokensWithTags.add(targetToken);
      targetTokensWithTags.addAll(tagsToInsertAfter);
    }

    // get end-of-sentence tags from source sentence
    int eosTokenIndex = sourceTokensWithoutTags.length;
    List<String> eosTags = sourceTokenIndex2tags.get(eosTokenIndex);
    if (eosTags != null) {
      targetTokensWithTags.addAll(eosTags);
    }

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
            // bpe fragment at beginning of sentence, so add tag in front of it
            tokenList.add(0, removedToken);
          }
        }
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
   * Check if token at the given index is between bpe fragments.
   *
   * @param targetTokensWithTags
   *          the tokens
   * @param tokenIndex
   *          the index of the token to check
   * @return {@code true} if between bpe fragments, {@code false} otherwise
   */
  private static boolean isBetweenBpeFragments(
      String[] targetTokensWithTags, int tokenIndex) {

    if (tokenIndex == 0
        || tokenIndex == targetTokensWithTags.length - 1) {
      // token at beginning or end cannot be between bpe fragments
      return false;
    }

    if (isBpeFragement(targetTokensWithTags[tokenIndex - 1])) {
      return true;
    }

    return false;
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
    return result.toString().trim();
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
   * @param tagId
   *          the tag id
   * @return an opening tag with the given tag id
   */
  public static String createOpeningTag(int tagId) {

    return String.format("%c%c", TextFragment.MARKER_OPENING, tagId + TextFragment.CHARBASE);
  }


  /**
   * @param tagId
   *          the tag id
   * @return a closing tag with the given tag id
   */
  public static String createClosingTag(int tagId) {

    return String.format("%c%c", TextFragment.MARKER_CLOSING, tagId + TextFragment.CHARBASE);
  }


  /**
   * @param tagId
   *          the tag id
   * @return an isolated tag with the given tag id
   */
  public static String createIsolatedTag(int tagId) {

    return String.format("%c%c", TextFragment.MARKER_ISOLATED, tagId + TextFragment.CHARBASE);
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
   * Check if the given token is an isolated Okapi tag.
   *
   * @param token
   *          the token
   * @return {@code true} if token is isolated Okapi tag
   */
  public static boolean isIsolatedTag(String token) {

    return token.charAt(0) == TextFragment.MARKER_ISOLATED;
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


  /**
   * Replace Okapi tags with human readable tags in given String array and create XML string.
   *
   * @param targetTokensWithTags
   *          string array with tokens
   * @param closing2OpeningTag
   *          map of closing tags to opening tags
   * @return XML string with appended tokens and replaced Okapi tags
   */
  public static String toXml(
      String[] targetTokensWithTags, Map<String, String> closing2OpeningTag) {

    String[] resultTokens = new String[targetTokensWithTags.length];

    int index = -1;
    for (String oneToken : targetTokensWithTags) {
      index++;
      if (isIsolatedTag(oneToken)) {
        resultTokens[index] = String.format("<iso%d/>", getTagId(oneToken));
      } else if (isOpeningTag(oneToken)) {
        resultTokens[index] = String.format("<u%d>", getTagId(oneToken));
      } else if (isClosingTag(oneToken)) {
        // use the id of the associated opening tag to get valid XML
        int openingTagId = getTagId(closing2OpeningTag.get(oneToken));
        resultTokens[index] = String.format("</u%d>", openingTagId);
      } else {
        resultTokens[index] = oneToken;
      }
    }

    return String.format(
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>%n<body>%n"
            + String.join(" ", resultTokens)
            + "%n</body>");
  }


  /**
   * Rebuild the source sentence with tags. Used for debugging
   * {@link MarianNmtConnector#moveSourceTagsToPointedTokens(Map, Map, List, int)}.
   *
   * @param sourceTokenIndex2tags
   *          map of source token index to list of associated tags
   * @param sourceTokensWithoutTags
   *          the original source token sequence without tags
   * @return list of source tokens with tags
   */
  public static List<String> rebuildSourceSentenceWithTags(
      Map<Integer, List<String>> sourceTokenIndex2tags,
      String[] sourceTokensWithoutTags) {

    List<String> tokens = new ArrayList<>();

    for (int i = 0; i < sourceTokensWithoutTags.length; i++) {
      List<String> tags = sourceTokenIndex2tags.get(i);
      if (tags == null) {
        tokens.add(sourceTokensWithoutTags[i]);
        continue;
      }
      List<String> tagsBefore = new ArrayList<>();
      List<String> tagsAfter = new ArrayList<>();
      for (String oneTag : tags) {
        if (isClosingTag(oneTag)) {
          tagsAfter.add(oneTag);
        } else {
          tagsBefore.add(0, oneTag);
        }
      }
      tokens.addAll(tagsBefore);
      tokens.add(sourceTokensWithoutTags[i]);
      tokens.addAll(tagsAfter);
    }

    return tokens;
  }
}
