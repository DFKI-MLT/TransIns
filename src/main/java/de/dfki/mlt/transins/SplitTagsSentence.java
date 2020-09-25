package de.dfki.mlt.transins;

import static de.dfki.mlt.transins.TagUtils.isClosingTag;
import static de.dfki.mlt.transins.TagUtils.isOpeningTag;
import static de.dfki.mlt.transins.TagUtils.isTag;
import static de.dfki.mlt.transins.TagUtils.removeTags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * Sentence where the following tags have been split from sentence beginning and end:
 * <ul>
 * <li>isolated tags
 * <li>tags of tags pairs covering the whole sentence
 * </ul>
 *
 * @author Jörg Steffen, DFKI
 */
@Data
public class SplitTagsSentence {

  private String[] tokensWithTags;
  private String[] tokensWithoutTags;
  private List<String> beginningOfSentenceTags;
  private List<String> endOfSentenceTags;


  /**
   * Create a new split sentence from the given tokens with tags.
   *
   * @param tokensWithTags
   *          tokens with tags
   * @param opening2ClosingTag
   *          map of opening tags to closing tags
   * @param closing2OpeningTag
   *          map of closing tags to opening tags
   */
  public SplitTagsSentence(
      String[] tokensWithTags,
      Map<String, String> opening2ClosingTag,
      Map<String, String> closing2OpeningTag) {

    this.beginningOfSentenceTags = new ArrayList<>();
    this.endOfSentenceTags = new ArrayList<>();

    // collect tags before first token;
    // isolated tags and empty tag pairs are possible
    for (String oneToken : tokensWithTags) {
      if (!isTag(oneToken)) {
        break;
      }
      this.beginningOfSentenceTags.add(oneToken);
    }

    // collect tags after last token;
    // isolated tags and empty tag pairs are possible
    for (int i = tokensWithTags.length - 1; i >= 0; i--) {
      String oneToken = tokensWithTags[i];
      if (!isTag(oneToken)) {
        break;
      }
      this.endOfSentenceTags.add(0, oneToken);
    }

    // filter opening and closing tags of tag pairs not covering the whole sentence
    List<String> tempClosingTags = new ArrayList<>();
    for (String oneTag : new ArrayList<>(this.beginningOfSentenceTags)) {
      if (isOpeningTag(oneTag)) {
        String closingTag = opening2ClosingTag.get(oneTag);
        if (this.endOfSentenceTags.contains(closingTag)) {
          tempClosingTags.add(closingTag);
        } else if (!this.beginningOfSentenceTags.contains(closingTag)) {
          this.beginningOfSentenceTags.remove(oneTag);
        }
      }
    }
    // now tempClosingTags contains all closing tags at end of sentence for
    // which there is an opening tag at the beginning of sentence, i.e. the ones to keep
    for (String oneTag : new ArrayList<>(this.endOfSentenceTags)) {
      if (isClosingTag(oneTag)) {
        String openingTag = closing2OpeningTag.get(oneTag);
        if (tempClosingTags.contains(oneTag)
            || this.endOfSentenceTags.contains(openingTag)) {
          continue;
        }
        this.endOfSentenceTags.remove(oneTag);
      }
    }

    // remove collected beginning and end of sentence tokens;
    // it is assumed that tags occur exactly once
    List<String> filteredSentenceTokens = new ArrayList<>();
    for (String oneToken : tokensWithTags) {
      if (isTag(oneToken)) {
        if (this.beginningOfSentenceTags.contains(oneToken)
            || this.endOfSentenceTags.contains(oneToken)) {
          continue;
        }
      }
      filteredSentenceTokens.add(oneToken);
    }

    // convert array list to array
    this.tokensWithTags =
        filteredSentenceTokens.toArray(new String[filteredSentenceTokens.size()]);
  }


  /**
   * @return tokens without tags
   */
  public String[] getTokensWithoutTags() {

    if (this.tokensWithoutTags == null) {
      this.tokensWithoutTags = removeTags(this.tokensWithTags);
    }
    return this.tokensWithoutTags;
  }
}
