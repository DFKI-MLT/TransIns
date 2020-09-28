package de.dfki.mlt.transins;

import static de.dfki.mlt.transins.TagUtils.createClosingTag;
import static de.dfki.mlt.transins.TagUtils.createIsolatedTag;
import static de.dfki.mlt.transins.TagUtils.createOpeningTag;

/**
 * Provide utility methods for testing.
 *
 * @author Jörg Steffen, DFKI
 */
final class TestUtils {

  // declare tags in the format used by Okapi
  static final String OPEN1 = createOpeningTag(0);
  static final String CLOSE1 = createClosingTag(1);
  static final String OPEN2 = createOpeningTag(2);
  static final String CLOSE2 = createClosingTag(3);
  static final String OPEN3 = createOpeningTag(4);
  static final String CLOSE3 = createClosingTag(5);
  static final String ISO1 = createIsolatedTag(6);
  static final String ISO2 = createIsolatedTag(7);

  // bidirectional map from opening tags to closing tags
  static final TagMap tagMap = new TagMap();

  static {
    tagMap.put(OPEN1, CLOSE1);
    tagMap.put(OPEN2, CLOSE2);
    tagMap.put(OPEN3, CLOSE3);
  }


  private TestUtils() {

    // private constructor to enforce noninstantiability
  }


  /**
   * Utility method to split string into tokens and replace readable tags with Okapi tags.
   *
   * @param input
   *          the string to split
   * @return tokens with Okapi tags
   */
  public static String[] asArray(String input) {

    String[] tokens = input.split(" ");
    for (int i = 0; i < tokens.length; i++) {
      String oneToken = tokens[i];
      switch (oneToken) {
        case "ISO1":
          oneToken = ISO1;
          break;
        case "ISO2":
          oneToken = ISO2;
          break;
        case "OPEN1":
          oneToken = OPEN1;
          break;
        case "CLOSE1":
          oneToken = CLOSE1;
          break;
        case "OPEN2":
          oneToken = OPEN2;
          break;
        case "CLOSE2":
          oneToken = CLOSE2;
          break;
        case "OPEN3":
          oneToken = OPEN3;
          break;
        case "CLOSE3":
          oneToken = CLOSE3;
          break;
        default:
          // do nothing
      }
      tokens[i] = oneToken;
    }

    return tokens;
  }


  /**
   * Utility method to merge tokens and replace Okapi tag with readable tags.
   *
   * @param tokens
   *          the tokens to merge
   * @return string with readable tags
   */
  public static String asString(String[] tokens) {

    StringBuilder result = new StringBuilder();
    for (String oneToken : tokens) {
      if (oneToken.equals(ISO1)) {
        result.append("ISO1 ");
      } else if (oneToken.equals(ISO2)) {
        result.append("ISO2 ");
      } else if (oneToken.equals(OPEN1)) {
        result.append("OPEN1 ");
      } else if (oneToken.equals(CLOSE1)) {
        result.append("CLOSE1 ");
      } else if (oneToken.equals(OPEN2)) {
        result.append("OPEN2 ");
      } else if (oneToken.equals(CLOSE2)) {
        result.append("CLOSE2 ");
      } else if (oneToken.equals(OPEN3)) {
        result.append("OPEN3 ");
      } else if (oneToken.equals(CLOSE3)) {
        result.append("CLOSE3 ");
      } else {
        result.append(oneToken + " ");
      }
    }

    return result.toString().strip();
  }
}
