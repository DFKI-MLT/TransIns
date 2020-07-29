/*===========================================================================
  Copyright (C) 2009-2011 by the Okapi Framework contributors
-----------------------------------------------------------------------------
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
===========================================================================*/

package de.dfki.mlt.transins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
      String preprocessedSentence =
          this.prepostClient.process(
              super.getSourceLanguage().toString(),
              sentence,
              Mode.PREPROCESS,
              this.params.getPrePostHost(),
              this.params.getPrePostPort());
      logger.debug(String.format("preprocessed source sentence: \"%s\"", preprocessedSentence));

      // translate
      String translatorInput = removeCodes(preprocessedSentence);
      // add leading token with target language
      translatorInput = String.format("<to%s> %s", super.getTargetLanguage(), translatorInput);
      logger.debug(String.format("send to translator: \"%s\"", translatorInput));
      String translatorResponse = this.translatorClient.send(translatorInput);

      // split into translation and alignment
      String[] parts = translatorResponse.split(" \\|\\|\\| ");
      String translation = null;

      boolean hasMarkup = fragment.hasCode();
      if (parts.length == 2) {
        // if markup and alignment are available, re-insert markup
        translation = parts[0].trim();
        String rawAlignments = parts[1].trim();
        logger.debug(String.format("raw target sentence: \"%s\"", translation));
        logger.debug(String.format("raw alignment: \"%s\"", rawAlignments));
        Alignments algn = createAlignments(rawAlignments);
        // compensate for leading target language token in source sentence
        algn.shiftSourceIndexes(-1);
        if (hasMarkup) {
          // re-insert markup
          String[] sourceTokensWithMarkup = preprocessedSentence.split(" ");
          String[] targetTokens = translation.split(" ");

          // print alignment
          String[] sourceTokensWithoutMarkup = removeCodes(preprocessedSentence).split(" ");
          logger.debug(String.format("sentence alignment:%n%s", createSentenceAlignment(
              sourceTokensWithoutMarkup, targetTokens, algn)));

          String[] targetTokensWithMarkup = null;
          targetTokensWithMarkup =
              reinsertMarkup(sourceTokensWithMarkup, targetTokens, algn);

          // make sure markup is not between bpe fragments
          targetTokensWithMarkup = moveMarkupBetweenBpeFragments(targetTokensWithMarkup);

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
   * Advanced version to re-insert markup from source. Takes into account the 'direction' of
   * a tag and special handling of isolated tags at sentence beginning.
   *
   * @param sourceTokensWithMarkup
   *          list of source tokens, including the 2-character markup encoding used by
   *          Okapi
   * @param targetTokens
   *          list of target tokens (without any markup)
   * @param algn
   *          hard alignment of source and target tokens
   * @return target tokens with re-inserted markup
   */
  public static String[] reinsertMarkup(
      String[] sourceTokensWithMarkup, String[] targetTokens, Alignments algn) {

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
        List<String> sourceTags = sourceTokenIndex2tags.get(oneSourceTokenIndex);
        if (sourceTags != null) {
          for (String oneSourceTag : sourceTags) {
            if (isBackwardTag(oneSourceTag)) {
              tagsToInsertAfter.add(oneSourceTag);
            } else {
              tagsToInsertBefore.add(oneSourceTag);
            }
          }
          sourceTokenIndex2tags.remove(oneSourceTokenIndex);
        }
      }
      targetTokensWithMarkup.addAll(tagsToInsertBefore);
      targetTokensWithMarkup.add(targetToken);
      targetTokensWithMarkup.addAll(tagsToInsertAfter);
    }

    int lastTargetTokenIndex = targetTokens.length;
    List<String> lastSourceTags = sourceTokenIndex2tags.get(lastTargetTokenIndex);
    if (lastSourceTags != null) {
      targetTokensWithMarkup.addAll(lastSourceTags);
      sourceTokenIndex2tags.remove(lastTargetTokenIndex);
    }

    // add any remaining tags
    if (!sourceTokenIndex2tags.isEmpty()) {
      List<Integer> keys = new ArrayList<>(sourceTokenIndex2tags.keySet());
      Collections.sort(keys);
      for (Integer oneKey : keys) {
        targetTokensWithMarkup.addAll(sourceTokenIndex2tags.get(oneKey));
      }
    }
    String[] resultAsArray = new String[targetTokensWithMarkup.size()];
    return targetTokensWithMarkup.toArray(resultAsArray);
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
   * Create table with source and target sentence tokens with index and alignment.
   *
   * @param sourceTokens
   *          the source sentence tokens
   * @param targetTokens
   *          the target sentence tokens
   * @param algn
   *          the hard alignment
   * @return the table as string
   */
  public static String createSentenceAlignment(
      String[] sourceTokens, String[] targetTokens, Alignments algn) {

    StringBuilder result = new StringBuilder();
    result.append(String.format("%s%n", algn.toString()));

    // get max source token length
    int maxSourceTokenLength = "source:".length();
    for (String oneToken : sourceTokens) {
      if (oneToken.length() > maxSourceTokenLength) {
        maxSourceTokenLength = oneToken.length();
      }
    }
    // get max target token length
    int maxTargetTokenLength = "target:".length();
    for (String oneToken : targetTokens) {
      if (oneToken.length() > maxTargetTokenLength) {
        maxTargetTokenLength = oneToken.length();
      }
    }

    result.append(
        String.format(
            "%" + maxTargetTokenLength + "s   \t\t\t   %" + maxSourceTokenLength + "s%n",
            "TARGET:", "SOURCE:"));
    for (int i = 0; i < Math.max(targetTokens.length, sourceTokens.length); i++) {
      if (i < targetTokens.length) {
        result.append(
            String.format("%" + maxTargetTokenLength + "s %2d\t\t\t",
                targetTokens[i], i));
      } else {
        result.append(String.format("%" + (maxTargetTokenLength + 3) + "s\t\t\t", " "));
      }
      if (i < sourceTokens.length) {
        result.append(
            String.format("%2d %" + maxSourceTokenLength + "s\t\t\t%n",
                i, sourceTokens[i]));
      } else {
        result.append(String.format("%n"));
      }
    }

    return result.toString();
  }
}

