package de.dfki.mlt.transins;

import static de.dfki.mlt.transins.TagUtils.isBackwardTag;
import static de.dfki.mlt.transins.TagUtils.isClosingTag;
import static de.dfki.mlt.transins.TagUtils.isIsolatedTag;
import static de.dfki.mlt.transins.TagUtils.isOpeningTag;
import static de.dfki.mlt.transins.TagUtils.isTag;
import static de.dfki.mlt.transins.TagUtils.removeTags;
import static de.dfki.mlt.transins.TagUtils.asString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.okapi.common.exceptions.OkapiException;

/**
 * Provide methods to re-insert markup from source to target sentence (the translation of the source
 * sentence) using alignments.
 *
 * @author Jörg Steffen, DFKI
 */
public final class MarkupInserter {

  private static final Logger logger = LoggerFactory.getLogger(MarkupInserter.class);

  // special token to mark end of sentence of target sentence
  private static final String EOS = "end-of-target-sentence-marker";


  private MarkupInserter() {

    // private constructor to enforce noninstantiability
  }


  /**
   * Insert markup from given source sentence into its given translation using the given alignments.
   *
   * @param preprocessedSourceSentence
   *          the preprocessed source sentence with whitespace separated tokens
   * @param translation
   *          the translation of the source sentence with whitespace separated tokens
   * @param algn
   *          the alignments
   * @return the translation with re-inserted markup ready for postprocessing, i.e. with masked tags
   */
  public static String insertMarkup(
      String preprocessedSourceSentence, String translation, Alignments algn) {

    logger.debug(String.format("sentence alignments:%n%s", createSentenceAlignments(
        preprocessedSourceSentence, translation, algn)));

    String[] sourceTokensWithTags = preprocessedSourceSentence.split(" ");
    String[] targetTokensWithoutTags = translation.split(" ");
    String[] sourceTokensWithoutTags = removeTags(sourceTokensWithTags);

    // get mapping of opening tags to closing tags and vice versa
    TagMap tagMap = createTagMap(sourceTokensWithTags);

    // replace empty tag pairs
    Map<String, List<String>> isoReplacements = new HashMap<>();
    sourceTokensWithTags =
        MarkupInserter.replaceEmptyTagPairsWithIsos(sourceTokensWithTags, tagMap, isoReplacements);

    // split tags at beginning and end of source sentence
    SplitTagsSentence sourceSentence = new SplitTagsSentence(sourceTokensWithTags, tagMap);

    // assign each source token to its tags
    Map<Integer, List<String>> sourceTokenIndex2tags = createTokenIndex2Tags(sourceSentence);

    // move tags in case of no target token pointing to the associated source token
    moveSourceTagsToPointedTokens(sourceTokenIndex2tags, tagMap,
        algn.getPointedSourceTokens(), sourceTokensWithoutTags.length);

    // re-insert tags
    String[] targetTokensWithTags = reinsertTags(
        sourceSentence, sourceTokenIndex2tags, targetTokensWithoutTags, algn);
    logger.debug("target sentence with inserted tags: \"{}\"", asString(targetTokensWithTags));

    // clean up tags
    targetTokensWithTags = moveTagsFromBetweenBpeFragments(targetTokensWithTags, tagMap);
    targetTokensWithTags = undoBytePairEncoding(targetTokensWithTags);
    targetTokensWithTags = handleInvertedTags(tagMap, targetTokensWithTags);
    targetTokensWithTags = removeRedundantTags(tagMap, targetTokensWithTags);
    targetTokensWithTags = balanceTags(tagMap, targetTokensWithTags);
    targetTokensWithTags = mergeNeighborTagPairs(tagMap, targetTokensWithTags);

    // add unused tags
    List<String> unusedTags = collectUnusedTags(sourceTokensWithTags, targetTokensWithTags);
    targetTokensWithTags = addTags(targetTokensWithTags, unusedTags);

    // put back empty tag pairs
    targetTokensWithTags =
        MarkupInserter.replaceIsosWithEmptyTagPairs(targetTokensWithTags, isoReplacements);
    logger.debug("target sentence with cleaned tags: \"{}\"", asString(targetTokensWithTags));

    // prepare translation for postprocessing;
    // mask tags so that detokenizer in postprocessing works correctly
    translation = maskTags(targetTokensWithTags);

    return translation;
  }


  /**
   * Insert markup from given source sentence into its given translation using the given alignments.
   * This method uses a complete token-index-2-tags mapping that assigns each token ALL tags that
   * apply to it.
   *
   * @param preprocessedSourceSentence
   *          the preprocessed source sentence with whitespace separated tokens
   * @param translation
   *          the translation of the source sentence with whitespace separated tokens
   * @param algn
   *          the alignments
   * @return the translation with re-inserted markup ready for postprocessing, i.e. with masked tags
   */
  public static String insertMarkupComplete(
      String preprocessedSourceSentence, String translation, Alignments algn) {

    logger.debug(String.format("sentence alignments:%n%s", createSentenceAlignments(
        preprocessedSourceSentence, translation, algn)));

    String[] sourceTokensWithTags = preprocessedSourceSentence.split(" ");
    String[] targetTokensWithoutTags = translation.split(" ");
    String[] sourceTokensWithoutTags = removeTags(sourceTokensWithTags);

    // get mapping of opening tags to closing tags and vice versa
    TagMap tagMap = createTagMap(sourceTokensWithTags);

    // replace empty tag pairs
    Map<String, List<String>> isoReplacements = new HashMap<>();
    sourceTokensWithTags =
        MarkupInserter.replaceEmptyTagPairsWithIsos(sourceTokensWithTags, tagMap, isoReplacements);

    // split tags at beginning and end of source sentence
    SplitTagsSentence sourceSentence = new SplitTagsSentence(sourceTokensWithTags, tagMap);

    // assign each source token to its tags
    Map<Integer, List<String>> sourceTokenIndex2tags =
        createTokenIndex2TagsComplete(sourceSentence, tagMap);

    // move isolated tags in case of no target token pointing to the associated source token
    moveIsoTagsToPointedTokens(
        sourceTokenIndex2tags, algn.getPointedSourceTokens(), sourceTokensWithoutTags.length);

    // re-insert tags
    String[] targetTokensWithTags = reinsertTagsComplete(
        sourceSentence, sourceTokenIndex2tags, targetTokensWithoutTags, algn);
    logger.debug("target sentence with inserted tags: \"{}\"", asString(targetTokensWithTags));

    // clean up tags
    targetTokensWithTags = moveTagsFromBetweenBpeFragments(targetTokensWithTags, tagMap);
    targetTokensWithTags = undoBytePairEncoding(targetTokensWithTags);
    targetTokensWithTags = mergeNeighborTagPairs(tagMap, targetTokensWithTags);

    // add unused tags
    List<String> unusedTags = collectUnusedTags(sourceTokensWithTags, targetTokensWithTags);
    targetTokensWithTags = addTags(targetTokensWithTags, unusedTags);

    // put back empty tag pairs
    targetTokensWithTags =
        MarkupInserter.replaceIsosWithEmptyTagPairs(targetTokensWithTags, isoReplacements);
    logger.debug("target sentence with cleaned tags: \"{}\"", asString(targetTokensWithTags));

    // prepare translation for postprocessing;
    // mask tags so that detokenizer in postprocessing works correctly
    translation = maskTags(targetTokensWithTags);

    return translation;
  }


  /**
   * Create bidirectional map of opening tags to closing tags from the given tokens with tags.
   * It is assumed that the tags are balanced.
   *
   * @param tokensWithTags
   *          the tokens with tags
   * @return bidirectional map of opening tags to closing tags
   */
  static TagMap createTagMap(String[] tokensWithTags) {

    TagMap tagMap = new TagMap();

    Stack<String> openingTagsStack = new Stack<>();

    for (String oneToken : tokensWithTags) {
      if (isOpeningTag(oneToken)) {
        openingTagsStack.push(oneToken);
      } else if (isClosingTag(oneToken)) {
        String openingTag = openingTagsStack.pop();
        tagMap.put(openingTag, oneToken);
      }
    }

    return tagMap;
  }


  /**
   * Replace empty tag pairs with newly created isolated tags.
   *
   * @param tokensWithTags
   *          the tokens with tags
   * @param isoReplacements
   *          map where to add mapping of newly created isolated tags
   *          to the original empty tag pairs
   * @return the tokens with replaced empty tag pairs
   */
  static String[] replaceEmptyTagPairsWithIsos(
      String[] tokensWithTags, TagMap tagMap, Map<String, List<String>> isoReplacements) {

    // get maximum id of tags, so that we can safely created new tags using higher ids
    int maxId = Integer.MIN_VALUE;
    for (String oneToken : tokensWithTags) {
      if (isTag(oneToken)) {
        int id = TagUtils.getTagId(oneToken);
        if (id > maxId) {
          maxId = id;
        }
      }
    }

    // replace empty tag pairs
    List<String> resultTokens = new ArrayList<>();
    int currentId = maxId + 1;
    for (int i = 0; i < tokensWithTags.length; i++) {
      String oneToken = tokensWithTags[i];
      if (!isOpeningTag(oneToken)) {
        resultTokens.add(oneToken);
      } else {
        String matchClosingTag = tagMap.getClosingTag(oneToken);
        // collect everything between the opening and the closing tag
        List<String> covered = new ArrayList<>();
        covered.add(oneToken);
        for (int j = i + 1; j < tokensWithTags.length; j++) {
          String nextToken = tokensWithTags[j];
          if (!isTag(nextToken)) {
            covered.clear();
            break;
          } else {
            covered.add(nextToken);
            if (nextToken.equals(matchClosingTag)) {
              i = j;
              break;
            }
          }
        }
        if (covered.isEmpty()) {
          resultTokens.add(oneToken);
        } else {
          String newIso = TagUtils.createIsolatedTag(currentId);
          currentId++;
          resultTokens.add(newIso);
          isoReplacements.put(newIso, new ArrayList<>(covered));
          covered.clear();
        }
      }
    }

    // convert array list to array and return it
    return resultTokens.toArray(new String[resultTokens.size()]);
  }


  /**
   * Replace isolated tags with empty tag pairs, undoing the replacement of
   * {@link #replaceIsosWithEmptyTagPairs(String[], Map)}.
   *
   * @param tokensWithTags
   *          the tokens with tags
   * @param isoReplacements
   *          map where to add mapping of newly created isolated tags
   *          to the original empty tag pairs
   * @return the tokens with replaced isolated tags
   */
  static String[] replaceIsosWithEmptyTagPairs(
      String[] tokensWithTags, Map<String, List<String>> isoReplacements) {

    List<String> resultTokens = new ArrayList<>();

    for (String oneToken : tokensWithTags) {
      if (isIsolatedTag(oneToken)) {
        List<String> replacements = isoReplacements.get(oneToken);
        if (replacements != null) {
          resultTokens.addAll(replacements);
          continue;
        }
      }
      resultTokens.add(oneToken);
    }

    // convert array list to array and return it
    return resultTokens.toArray(new String[resultTokens.size()]);
  }


  /**
   * Create a map from token indexes to tags. Take into account the 'direction' of a tag,
   * i.e. isolated and opening tags are assigned to the <b>next</b> non-tag token,
   * while a closing tag is assigned to the <b>previous</b> non-tag token.
   *
   * @param sourceSentence
   *          source sentence with split beginning and end tags
   * @return map from token index to associated tags
   */
  static Map<Integer, List<String>> createTokenIndex2Tags(SplitTagsSentence sourceSentence) {

    Map<Integer, List<String>> index2tags = new LinkedHashMap<>();

    int offset = 0;
    String[] tokensWithTags = sourceSentence.getTokensWithTags();
    for (int i = 0; i < tokensWithTags.length; i++) {
      String currentToken = tokensWithTags[i];
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
        currentTags.add(tokensWithTags[i]);
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
   * <li>if there is no pointing token after isolated tag, remove it
   * <li>move opening tags forwards until pointed token
   * <li>move closing tags backwards until pointed token
   * <li>move isolated tags forward until pointed token
   * </ul>
   * The provided map {@code sourceTokenIndex2tags} is adapted accordingly.
   *
   * @param sourceTokenIndex2Tags
   *          map of source token indexes to associated tags
   * @param tagMap
   *          bidirectional map of opening tags to closing tags
   * @param pointedSourceTokens
   *          all source token indexes for which there is at least one target token pointing at them
   *          in the alignments
   * @param sourceTokensLength
   *          the number of source tokens
   * @return list of tags that cannot be assigned to a pointed token;
   *         contains all tag pairs with no pointed token in between and isolated tags with
   *         no following pointed token
   */
  static List<String> moveSourceTagsToPointedTokens(
      Map<Integer, List<String>> sourceTokenIndex2Tags, TagMap tagMap,
      List<Integer> pointedSourceTokens, int sourceTokensLength) {

    // for each closing tag of non-pointed source tokens, check if there is
    // a pointed source on the way to the corresponding opening tag;
    // if not remove the tag pair
    List<String> unusedTags = new ArrayList<>();
    for (var oneEntry : new HashSet<>(sourceTokenIndex2Tags.entrySet())) {
      int sourceTokenIndex = oneEntry.getKey();
      if (pointedSourceTokens.contains(sourceTokenIndex)) {
        continue;
      }

      List<String> tags = oneEntry.getValue();
      for (String oneTag : new ArrayList<>(tags)) {
        if (isClosingTag(oneTag)) {
          // find corresponding opening tag in front of it
          String openingTag = tagMap.getOpeningTag(oneTag);
          int openingTagSourceTokenIndex = -1;
          List<String> previousTags = null;
          for (int i = sourceTokenIndex; i >= 0; i--) {
            previousTags = sourceTokenIndex2Tags.get(i);
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
              sourceTokenIndex2Tags.remove(sourceTokenIndex);
            }
            if (previousTags != null) {
              // just check for non-null to suppress warning
              previousTags.remove(openingTag);
              if (previousTags.isEmpty()) {
                sourceTokenIndex2Tags.remove(openingTagSourceTokenIndex);
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
    // now move opening and isolated tags to the following pointed token and
    // closing tags to the preceding pointed token
    for (var oneEntry : new HashSet<>(sourceTokenIndex2Tags.entrySet())) {
      int sourceTokenIndex = oneEntry.getKey();
      if (pointedSourceTokens.contains(sourceTokenIndex)) {
        continue;
      }

      List<String> tags = oneEntry.getValue();
      for (String oneTag : new ArrayList<>(tags)) {
        if (isOpeningTag(oneTag) || isIsolatedTag(oneTag)) {
          boolean pointedSourceTokenFound = false;
          for (int i = sourceTokenIndex + 1; i < sourceTokensLength; i++) {
            if (pointedSourceTokens.contains(i)) {
              List<String> pointedSourceTokenTags = sourceTokenIndex2Tags.get(i);
              if (pointedSourceTokenTags == null) {
                pointedSourceTokenTags = new ArrayList<>();
                sourceTokenIndex2Tags.put(i, pointedSourceTokenTags);
              }
              pointedSourceTokenTags.add(0, oneTag);
              pointedSourceTokenFound = true;
              break;
            }
          }
          tags.remove(oneTag);
          if (tags.isEmpty()) {
            sourceTokenIndex2Tags.remove(sourceTokenIndex);
          }
          if (!pointedSourceTokenFound) {
            // this can only happen for isolated tags that have no following pointed token;
            // add these to unused tags
            unusedTags.add(oneTag);
          }
        } else if (isClosingTag(oneTag)) {
          for (int i = sourceTokenIndex - 1; i >= 0; i--) {
            if (pointedSourceTokens.contains(i)) {
              List<String> pointedSourceTokenTags = sourceTokenIndex2Tags.get(i);
              if (pointedSourceTokenTags == null) {
                pointedSourceTokenTags = new ArrayList<>();
                sourceTokenIndex2Tags.put(i, pointedSourceTokenTags);
              }
              pointedSourceTokenTags.add(oneTag);
              tags.remove(oneTag);
              if (tags.isEmpty()) {
                sourceTokenIndex2Tags.remove(sourceTokenIndex);
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
   * @return list of tags associated with the index
   */
  static List<String> getTagsForSourceTokenIndex(
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
   * Re-insert tags from source sentence to target sentence using alignments.
   *
   * @param sourceSentence
   *          source tokens together with beginning and end of sentence tokens
   * @param sourceTokenIndex2tags
   *          map of source token indexes to associated tags, as created with
   *          {@link #createTokenIndex2Tags(String[])}
   * @param targetTokensWithoutTags
   *          target tokens without tags
   * @param algn
   *          alignments of source and target tokens
   * @return target tokens with re-inserted tags
   */
  static String[] reinsertTags(
      SplitTagsSentence sourceSentence,
      Map<Integer, List<String>> sourceTokenIndex2tags,
      String[] targetTokensWithoutTags,
      Alignments algn) {

    // add explicit end-of-sentence marker to target sentence
    targetTokensWithoutTags =
        Arrays.copyOfRange(targetTokensWithoutTags, 0, targetTokensWithoutTags.length + 1);
    targetTokensWithoutTags[targetTokensWithoutTags.length - 1] = EOS;

    List<String> targetTokensWithTags = new ArrayList<>();

    // add tags from source sentence beginning
    targetTokensWithTags.addAll(sourceSentence.getBeginningOfSentenceTags());

    // move isolated and copy non-isolated tags from source to target
    Set<String> usedIsolatedTags = new HashSet<>();
    for (int targetTokenIndex = 0; targetTokenIndex < targetTokensWithoutTags.length;
        targetTokenIndex++) {

      String targetToken = targetTokensWithoutTags[targetTokenIndex];

      List<String> tagsToInsertBefore = new ArrayList<>();
      List<String> tagsToInsertAfter = new ArrayList<>();

      List<Integer> sourceTokenIndexes = algn.getSourceTokenIndexes(targetTokenIndex);
      for (int oneSourceTokenIndex : sourceTokenIndexes) {
        List<String> sourceTags = getTagsForSourceTokenIndex(
            oneSourceTokenIndex, sourceTokenIndex2tags, sourceSentence.getTokensWithoutTags());
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

    // add tags from source sentence end
    targetTokensWithTags.addAll(sourceSentence.getEndOfSentenceTags());

    // convert array list to array and return it
    return targetTokensWithTags.toArray(new String[targetTokensWithTags.size()]);
  }


  /**
   * When doing byte pair encoding, tags can end up between bpe fragments. Collect all tags for
   * each bpe fragment sequence and position them around the sequence in a valid way.
   *
   * @param tokens
   *          the tokens
   * @param tagMap
   *          bidirectional map of opening tags to closing tags
   * @return the tokens without any tags between bpe fragments
   */
  static String[] moveTagsFromBetweenBpeFragments(String[] tokens, TagMap tagMap) {

    ArrayList<String> tokenList = new ArrayList<>();

    int fragSeqStart = -1;
    int fragSeqEnd = -1;
    for (int i = 0; i < tokens.length; i++) {
      String oneToken = tokens[i];
      if (!isBpeFragement(oneToken)) {
        tokenList.add(oneToken);
      } else {
        fragSeqStart = i;
        // add all opening and isolated tags in front to range
        for (int j = i - 1; j >= 0; j--) {
          String prevToken = tokens[j];
          if (isIsolatedTag(prevToken) || isOpeningTag(prevToken)) {
            fragSeqStart = j;
            // this tag has already been added to tokenList, so remove it
            tokenList.remove(tokenList.size() - 1);
          } else {
            break;
          }
        }
        // find last bpe fragment and add all closing tags after it to range
        for (int j = i + 1; j < tokens.length; j++) {
          String nextToken = tokens[j];
          if (isTag(nextToken)) {
            continue;
          }
          if (!isBpeFragement(nextToken)) {
            // last bpe fragment found
            fragSeqEnd = j;
            for (int k = j + 1; k < tokens.length; k++) {
              nextToken = tokens[k];
              if (isClosingTag(nextToken)) {
                fragSeqEnd = k;
              } else {
                break;
              }
            }
            break;
          }
        }

        // collect tags and bpe fragments from range
        List<String> frags = new ArrayList<>();
        List<String> tagsToInsertBefore = new ArrayList<>();
        List<String> tagsToInsertAfter = new ArrayList<>();
        for (int j = fragSeqStart; j < fragSeqEnd + 1; j++) {
          oneToken = tokens[j];
          if (isOpeningTag(oneToken) || isIsolatedTag(oneToken)) {
            if (!tagsToInsertBefore.contains(oneToken)) {
              tagsToInsertBefore.add(oneToken);
            }
          } else if (isClosingTag(oneToken)) {
            if (!tagsToInsertAfter.contains(oneToken)) {
              tagsToInsertAfter.add(oneToken);
            }
          } else {
            frags.add(oneToken);
          }
        }

        // add tokens to list in a valid order
        tokenList.addAll(tagsToInsertBefore);
        tokenList.addAll(frags);


        List<String> reversedTagsToInsertBefore = new ArrayList<>(tagsToInsertBefore);
        Collections.reverse(reversedTagsToInsertBefore);
        for (String oneTag : reversedTagsToInsertBefore) {
          if (isIsolatedTag(oneTag)) {
            continue;
          }
          String matchClosingTag = tagMap.getClosingTag(oneTag);
          if (tagsToInsertAfter.contains(matchClosingTag)) {
            tokenList.add(matchClosingTag);
            tagsToInsertAfter.remove(matchClosingTag);
          }
        }
        // add remaining closing tags
        tokenList.addAll(tagsToInsertAfter);
        i = fragSeqEnd;
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
  static String[] undoBytePairEncoding(String[] tokens) {

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
   * @param tagMap
   *          bidirectional map of opening tags to closing tags
   * @param targetTokensWithTags
   *          target sentence tokens with tags
   * @return target sentence tokens with handled inverted tags
   */
  static String[] handleInvertedTags(TagMap tagMap, String[] targetTokensWithTags) {

    List<String> tokenList = new ArrayList<>(Arrays.asList(targetTokensWithTags));

    for (var oneEntry : tagMap.entrySet()) {

      String openingTag = oneEntry.getKey();
      String closingTag = oneEntry.getValue();

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
   * @param tagMap
   *          bidirectional map of opening tags to closing tags
   * @param targetTokensWithTags
   *          target sentence tokens with tags
   * @return target sentence tokens with removed redundant tags
   */
  static String[] removeRedundantTags(TagMap tagMap, String[] targetTokensWithTags) {

    List<String> tokenList = new ArrayList<>(Arrays.asList(targetTokensWithTags));

    for (var oneEntry : tagMap.entrySet()) {

      String openingTag = oneEntry.getKey();
      String closingTag = oneEntry.getValue();

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
   * @param tagMap
   *          bidirectional map of opening tags to closing tags
   * @param targetTokensWithTags
   *          target sentence tokens with tags, potentially unbalanced
   * @return target sentence tokens with balanced tags
   */
  static String[] balanceTags(TagMap tagMap, String[] targetTokensWithTags) {

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
              sortOpeningTags(openingStartIndex, i, targetTokensWithTags, tagMap);
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
              sortClosingTags(closingStartIndex, i, targetTokensWithTags, tagMap);
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
              tagMap);
    }

    // fix overlapping tag ranges
    List<String> tokenList = new ArrayList<>(Arrays.asList(targetTokensWithTags));
    Stack<String> openingTags = new Stack<>();

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
        String matchingOpeningTag = tagMap.getOpeningTag(oneToken);
        Stack<String> tempStack = new Stack<>();
        while (!openingTags.peek().equals(matchingOpeningTag)) {
          tempStack.push(openingTags.pop());
        }
        for (int j = tempStack.size() - 1; j >= 0; j--) {
          tokenList.add(i, tagMap.getClosingTag(tempStack.get(j)));
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
   * @param tagMap
   *          bidirectional map of opening tags to closing tags
   * @return target sentence tokens with sorted opening tags
   */
  static String[] sortOpeningTags(
      int startIndex, int endIndex, String[] targetTokensWithTags, TagMap tagMap) {

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
        String matchingOpeningTag = tagMap.getOpeningTag(oneToken);
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
   * @param tagMap
   *          bidirectional map of opening tags to closing tags
   * @return target sentence tokens with sorted closing tags
   */
  static String[] sortClosingTags(
      int startIndex, int endIndex, String[] targetTokensWithTags, TagMap tagMap) {

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
    for (int i = startIndex - 1; i >= 0; i--) {
      String oneToken = targetTokensWithTags[i];
      if (isOpeningTag(oneToken)) {
        String matchingClosingTag = tagMap.getClosingTag(oneToken);
        if (closingTags.contains(matchingClosingTag)) {
          sortedClosingTags.add(matchingClosingTag);
          closingTags.remove(matchingClosingTag);
          if (closingTags.isEmpty()) {
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
   * x <b> <it> y </it> </b> <b> <it> z a b </it> </b>
   * }
   * </pre>
   * is changed into
   * <pre>
   * {@code
   * x <b> <it> y z a b </it> </b>
   * }
   * </pre>
   *
   * @param tagMap
   *          bidirectional map of opening tags to closing tags
   * @param targetTokensWithTags
   *          target sentence tokens with tags
   * @return target sentence tokens with merged neighbor tags
   */
  static String[] mergeNeighborTagPairs(TagMap tagMap, String[] targetTokensWithTags) {

    List<String> tokenList = new ArrayList<>(Arrays.asList(targetTokensWithTags));

    boolean tagsRemoved;
    do {
      tagsRemoved = false;
      for (var oneEntry : tagMap.entrySet()) {
        String openingTag = oneEntry.getKey();
        String closingTag = oneEntry.getValue();

        for (int i = 1; i < tokenList.size(); i++) {
          String prevToken = tokenList.get(i - 1);
          String oneToken = tokenList.get(i);

          if (oneToken.equals(openingTag) && prevToken.equals(closingTag)) {
            tokenList.remove(i);
            tokenList.remove(i - 1);
            i = i - 2;
            tagsRemoved = true;
          }
        }
      }
    } while (tagsRemoved);

    String[] resultAsArray = new String[tokenList.size()];
    return tokenList.toArray(resultAsArray);
  }


  /**
   * Collect all tags that appear in the source sentence, but not in the target sentence.
   * We assume that the tags in the source sentence are balanced.
   *
   * @param sourceTokensWithTags
   *          source tokens with tags
   * @param targetTokensWithTags
   *          target tokens with tags
   * @return unused tags
   */
  static List<String> collectUnusedTags(
      String[] sourceTokensWithTags, String[] targetTokensWithTags) {

    List<String> unusedTags = new ArrayList<>();
    Set<String> targetTags = new HashSet<>();
    for (String oneToken : targetTokensWithTags) {
      if (isTag(oneToken)) {
        targetTags.add(oneToken);
      }
    }
    for (String oneToken : sourceTokensWithTags) {
      if (isTag(oneToken) && !targetTags.contains(oneToken)) {
        unusedTags.add(oneToken);
      }
    }
    return unusedTags;
  }


  /**
   * Add given tags at end of tag array
   *
   * @param tags
   *          tag array
   * @param tagsToAdd
   *          tags to add
   * @return tag array with tags added
   */
  static String[] addTags(String[] tags, List<String> tagsToAdd) {

    List<String> tokenList = new ArrayList<>(Arrays.asList(tags));

    for (String oneTag : tagsToAdd) {
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
   * Undo the tag masking of {@link #maskTags(String[])}.
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
   * left/right of the tags, depending on the type of tag. Opening and isolated tags have all
   * spaces to their right removed, closing tags have all spaces to their left removed.
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
  static boolean isBpeFragement(String token) {

    return token.endsWith("@@");
  }


  /**
   * Create table with source and target sentence tokens with index and alignments.
   *
   * @param preprocessedSourceSentence
   *          the preprocessed source sentence with whitespace separated tokens
   * @param translation
   *          the translation of the source sentence with whitespace separated tokens
   * @param algn
   *          the hard alignments
   * @return the table as string
   */
  static String createSentenceAlignments(
      String preprocessedSourceSentence, String translation, Alignments algn) {

    String[] sourceTokensWithoutTags = removeTags(preprocessedSourceSentence).split(" ");
    String[] targetTokensWithoutTags = translation.split(" ");

    StringBuilder result = new StringBuilder();
    result.append(String.format("%s%n", algn.toString()));

    // get max source token length
    int maxSourceTokenLength = "source:".length();
    for (String oneToken : sourceTokensWithoutTags) {
      if (oneToken.length() > maxSourceTokenLength) {
        maxSourceTokenLength = oneToken.length();
      }
    }
    // get max target token length
    int maxTargetTokenLength = "target:".length();
    for (String oneToken : targetTokensWithoutTags) {
      if (oneToken.length() > maxTargetTokenLength) {
        maxTargetTokenLength = oneToken.length();
      }
    }

    result.append(
        String.format(
            "%" + maxTargetTokenLength + "s   \t\t\t   %" + maxSourceTokenLength + "s%n",
            "TARGET:", "SOURCE:"));
    for (int i = 0;
        i < Math.max(targetTokensWithoutTags.length, sourceTokensWithoutTags.length);
        i++) {
      if (i < targetTokensWithoutTags.length) {
        result.append(
            String.format("%" + maxTargetTokenLength + "s %2d\t\t\t",
                targetTokensWithoutTags[i], i));
      } else {
        result.append(String.format("%" + (maxTargetTokenLength + 3) + "s\t\t\t", " "));
      }
      if (i < sourceTokensWithoutTags.length) {
        result.append(
            String.format("%2d %" + maxSourceTokenLength + "s\t\t\t%n",
                i, sourceTokensWithoutTags[i]));
      } else {
        result.append(String.format("%n"));
      }
    }

    return result.toString();
  }


  /**
   * Create a map from token indexes to tags. Take into account the 'direction' of a tag,
   * i.e. isolated and opening tags are assigned to the <b>next</b> token,
   * while a closing tag is assigned to the <b>previous</b> token.<br/>
   * This method assigns to each token index <b>all</b> tags that apply to that token.<br/>
   * Example:
   *
   * <pre>
   * {@code
   * x <it> y <b> z </b> a </it> b
   * }
   * </pre>
   * is interpreted as
   * <pre>
   * {@code
   * x <it> y </it> <it> <b> z </b> </it> <it> a </it> b
   * }
   * </pre>
   *
   * @param sourceSentence
   *          source sentence with split beginning and end tags
   * @param tagMap
   *          bidirectional map of opening tags to closing tags
   * @return map from token index to associated tags
   */
  static Map<Integer, List<String>> createTokenIndex2TagsComplete(
      SplitTagsSentence sourceSentence, TagMap tagMap) {

    String[] tokensWithTags = sourceSentence.getTokensWithTags();

    Map<Integer, List<String>> index2tags = new LinkedHashMap<>();
    Stack<String> currentTagStack = new Stack<>();
    int offset = 0;

    for (int i = 0; i < tokensWithTags.length; i++) {
      String currentToken = tokensWithTags[i];
      int currentIndex = i - offset;
      if (isTag(currentToken)) {
        // shift offset
        offset = offset + 1;
        if (isOpeningTag(currentToken) || isIsolatedTag(currentToken)) {
          currentTagStack.push(currentToken);
        } else if (isClosingTag(currentToken)) {
          String matchingOpeningTag = tagMap.getOpeningTag(currentToken);
          currentTagStack.remove(matchingOpeningTag);
        }
      } else {
        // current token is not a tag
        if (!currentTagStack.isEmpty()) {
          // get currently assigned tags for token
          List<String> currentTags = index2tags.get(currentIndex);
          if (currentTags == null) {
            currentTags = new ArrayList<>();
            index2tags.put(currentIndex, currentTags);
          }
          // assign all currently open tags to the token
          currentTags.addAll(currentTagStack);
          // assign closing tags for the currently open tags
          List<String> reversedOpeningTags = new ArrayList<>(currentTagStack);
          Collections.reverse(reversedOpeningTags);
          for (String oneTag : reversedOpeningTags) {
            if (isIsolatedTag(oneTag)) {
              // isolated tags are added exactly once at the next following non-tag token
              currentTagStack.remove(oneTag);
            } else {
              currentTags.add(tagMap.getClosingTag(oneTag));
            }
          }
        }
      }
    }

    return index2tags;
  }


  /**
   * Make sure that all isolated tags are assigned to tokens that are actually 'pointed' to
   * by at least one target token by moving isolated tags forward until pointed token.
   * Remove isolated tag if there is no following pointed token.
   * The provided map {@code sourceTokenIndex2tags} is adapted accordingly.
   *
   * @param sourceTokenIndex2Tags
   *          map of source token indexes to associated tags
   * @param pointedSourceTokens
   *          all source token indexes for which there is at least one target token pointing at them
   *          in the alignments
   * @param sourceTokensLength
   *          the number of source tokens
   * @return list of tags that cannot be assigned to a pointed token;
   *         contains isolated tags with no following pointed token
   */
  static List<String> moveIsoTagsToPointedTokens(
      Map<Integer, List<String>> sourceTokenIndex2Tags,
      List<Integer> pointedSourceTokens,
      int sourceTokensLength) {

    List<String> unusedTags = new ArrayList<>();
    for (var oneEntry : new HashSet<>(sourceTokenIndex2Tags.entrySet())) {
      int sourceTokenIndex = oneEntry.getKey();
      if (pointedSourceTokens.contains(sourceTokenIndex)) {
        continue;
      }

      List<String> tags = oneEntry.getValue();
      for (String oneTag : new ArrayList<>(tags)) {
        if (isIsolatedTag(oneTag)) {
          boolean pointedSourceTokenFound = false;
          for (int i = sourceTokenIndex + 1; i < sourceTokensLength; i++) {
            if (pointedSourceTokens.contains(i)) {
              List<String> pointedSourceTokenTags = sourceTokenIndex2Tags.get(i);
              if (pointedSourceTokenTags == null) {
                pointedSourceTokenTags = new ArrayList<>();
                sourceTokenIndex2Tags.put(i, pointedSourceTokenTags);
              }
              pointedSourceTokenTags.add(0, oneTag);
              pointedSourceTokenFound = true;
              break;
            }
          }
          tags.remove(oneTag);
          if (tags.isEmpty()) {
            sourceTokenIndex2Tags.remove(sourceTokenIndex);
          }
          if (!pointedSourceTokenFound) {
            unusedTags.add(oneTag);
          }
        }
      }
    }

    return unusedTags;
  }


  /**
   * Re-insert tags from source sentence to target sentence using alignments using the
   * source tags mapping containing <b>all</b> tags that apply to a token.
   *
   * @param sourceSentence
   *          source tokens together with beginning and end of sentence tokens
   * @param sourceTokenIndex2tags
   *          map of source token indexes to associated tags, as created with
   *          {@link #createTokenIndex2TagsComplete(String[], Map)}
   * @param targetTokensWithoutTags
   *          target tokens without tags
   * @param algn
   *          alignments of source and target tokens
   * @return target tokens with re-inserted tags
   */
  static String[] reinsertTagsComplete(
      SplitTagsSentence sourceSentence,
      Map<Integer, List<String>> sourceTokenIndex2tags,
      String[] targetTokensWithoutTags,
      Alignments algn) {

    // add explicit end-of-sentence marker to target sentence
    targetTokensWithoutTags =
        Arrays.copyOfRange(targetTokensWithoutTags, 0, targetTokensWithoutTags.length + 1);
    targetTokensWithoutTags[targetTokensWithoutTags.length - 1] = EOS;

    List<String> targetTokensWithTags = new ArrayList<>();

    // add tags from source sentence beginning
    targetTokensWithTags.addAll(sourceSentence.getBeginningOfSentenceTags());

    // in order to avoid isolated tags appearing more than once in the target sentence,
    // keep track of them
    Set<String> usedIsolatedTags = new HashSet<>();
    for (int targetTokenIndex = 0; targetTokenIndex < targetTokensWithoutTags.length;
        targetTokenIndex++) {

      String targetToken = targetTokensWithoutTags[targetTokenIndex];

      List<String> tagsToInsertBefore = new ArrayList<>();
      List<String> tagsToInsertAfter = new ArrayList<>();

      List<Integer> sourceTokenIndexes = algn.getSourceTokenIndexes(targetTokenIndex);

      for (int oneSourceTokenIndex : sourceTokenIndexes) {
        List<String> sourceTags = sourceTokenIndex2tags.get(oneSourceTokenIndex);
        if (sourceTags == null) {
          continue;
        }
        for (String oneSourceTag : sourceTags) {
          if (isBackwardTag(oneSourceTag)) {
            if (!tagsToInsertAfter.contains(oneSourceTag)) {
              tagsToInsertAfter.add(oneSourceTag);
            }
          } else {
            if (isIsolatedTag(oneSourceTag)) {
              if (usedIsolatedTags.contains(oneSourceTag)) {
                continue;
              }
              usedIsolatedTags.add(oneSourceTag);
            }
            if (!tagsToInsertBefore.contains(oneSourceTag)) {
              tagsToInsertBefore.add(oneSourceTag);
            }
          }
        }
      }
      if (targetToken.equals(EOS)) {
        // tag pairs don't apply to EOS, so only pickup isolated tags;
        // also, EOS token is skipped
        for (String oneTag : tagsToInsertBefore) {
          if (isIsolatedTag(oneTag)) {
            targetTokensWithTags.add(oneTag);
          }
        }
        for (String oneTag : tagsToInsertAfter) {
          if (isIsolatedTag(oneTag)) {
            targetTokensWithTags.add(oneTag);
          }
        }
      } else {
        targetTokensWithTags.addAll(tagsToInsertBefore);
        targetTokensWithTags.add(targetToken);
        targetTokensWithTags.addAll(tagsToInsertAfter);
      }
    }

    // add tags from source sentence end
    targetTokensWithTags.addAll(sourceSentence.getEndOfSentenceTags());

    // convert array list to array and return it
    return targetTokensWithTags.toArray(new String[targetTokensWithTags.size()]);
  }
}
