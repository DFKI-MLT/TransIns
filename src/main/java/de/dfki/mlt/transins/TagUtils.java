package de.dfki.mlt.transins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.okapi.common.resource.Code;
import net.sf.okapi.common.resource.TextFragment;

/**
 * Provide utility methods to work with tags.
 *
 * @author Jörg Steffen, DFKI
 */
public final class TagUtils {

  private static final Logger logger = LoggerFactory.getLogger(TagUtils.class);


  private TagUtils() {

    // private constructor to enforce noninstantiability
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
   * Check if given token is an Okapi forward tag.
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
   * Return the id of the given tag.
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
   * Remove Okapi codes from the preprocessed sentence with whitespace separated tokens.
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
    return sb.toString().strip();
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
   * Replace Okapi tags with human readable tags in given String array and create XML string.
   *
   * @param targetTokensWithTags
   *          string array with tokens
   * @param tagMap
   *          bidirectional map of opening tags to closing tags
   * @return XML string with appended tokens and replaced Okapi tags
   */
  public static String asXml(
      String[] targetTokensWithTags, TagMap tagMap) {

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
        int openingTagId = getTagId(tagMap.getOpeningTag(oneToken));
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
   * Replace Okapi tags with human readable tags in given String array and create string.
   *
   * @param targetTokensWithTags
   *          string array with tokens
   * @return string with appended tokens and replaced Okapi tags
   */
  public static String asString(String[] targetTokensWithTags) {

    return asString(targetTokensWithTags, Collections.emptyList());
  }


  /**
   * Replace Okapi tags with human readable tags in given String array and create string.
   *
   * @param targetTokensWithTags
   *          string array with tokens
   * @param codes
   *          list of Okapi codes from the fragment associated with the given text
   * @return string with appended tokens and replaced Okapi tags
   */
  public static String asString(String[] targetTokensWithTags, List<Code> codes) {

    return asString(String.join(" ", targetTokensWithTags).strip(), codes);
  }


  /**
   * Replace Okapi tags with human readable tags in given text.
   *
   * @param text
   *          text with tags
   * @return string with replaced Okapi tags
   */
  public static String asString(String text) {

    return asString(text, Collections.emptyList());
  }


  /**
   * Replace Okapi tags with human readable tags in given text.
   *
   * @param text
   *          text with tags
   * @param codes
   *          list of Okapi codes from the fragment associated with the given text
   * @return string with replaced Okapi tags
   */
  public static String asString(String text, List<Code> codes) {

    Code code;
    int index;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      switch (text.charAt(i)) {
        case TextFragment.MARKER_OPENING:
          index = TextFragment.toIndex(text.charAt(++i));
          if (index >= 0 && index < codes.size()) {
            code = codes.get(index);
            sb.append(String.format("<u id='%d'>", code.getId()));
          } else {
            // just use the index as simple id
            sb.append(String.format("<u sid='%d'>", index));
          }
          break;
        case TextFragment.MARKER_CLOSING:
          i++;
          sb.append("</u>");
          break;
        case TextFragment.MARKER_ISOLATED:
          index = TextFragment.toIndex(text.charAt(++i));
          if (index >= 0 && index < codes.size()) {
            code = codes.get(index);
            switch (code.getTagType()) {
              case OPENING:
                sb.append(String.format("<br id='b%d'/>", code.getId()));
                break;
              case CLOSING:
                sb.append(String.format("<br id='e%d'/>", code.getId()));
                break;
              case PLACEHOLDER:
                sb.append(String.format("<br id='p%d'/>", code.getId()));
                break;
              default:
                logger.warn("unsupported tag type \"{}\"", code.getTagType());
            }
          } else {
            // just use the index as simple id
            sb.append(String.format("<br sid='%d'/>", index));
          }
          break;
        default:
          sb.append(text.charAt(i));
      }
    }
    return sb.toString();
  }
}
