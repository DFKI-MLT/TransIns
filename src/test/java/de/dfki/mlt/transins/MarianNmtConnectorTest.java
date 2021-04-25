package de.dfki.mlt.transins;

import static de.dfki.mlt.transins.TestUtils.CLOSE1;
import static de.dfki.mlt.transins.TestUtils.ISO1;
import static de.dfki.mlt.transins.TestUtils.OPEN1;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Test class for {@link MarianNmtConnector}.
 *
 * @author Jörg Steffen, DFKI
 */
public class MarianNmtConnectorTest {

  /**
   * Test {@link MarkupInserter#detokenizeTags(String)}.
   */
  @Test
  void testDetokenizeTags() {

    // init variables to be re-used between tests
    String input = null;
    String expectedResult = null;

    // standard
    input = String.format("x y z %s a %s b %s c", ISO1, OPEN1, CLOSE1);
    expectedResult = String.format("x y z%sa %sb%s c", ISO1, OPEN1, CLOSE1);
    testDetokenizeTags(input, expectedResult);

    // multiple whitespaces
    input = String.format("x y z %s   a   %s  b   %s  c", ISO1, OPEN1, CLOSE1);
    expectedResult = String.format("x y z%sa   %sb%s  c", ISO1, OPEN1, CLOSE1);
    testDetokenizeTags(input, expectedResult);

    // empty tag pair
    input = String.format("x y z %s a %s %s b", ISO1, OPEN1, CLOSE1);
    expectedResult = String.format("x y z%sa %s%s b", ISO1, OPEN1, CLOSE1);
    testDetokenizeTags(input, expectedResult);
  }


  private void testDetokenizeTags(String input, String expectedResult) {

    input = MarianNmtConnector.detokenizeTags(input);
    assertThat(input)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            expectedResult, input))
        .isEqualTo(expectedResult);
  }


  /**
   * Test {@link MarianNmtConnector#maskTags(String[])} and
   * {@link MarianNmtConnector#unmaskTags(String)}.
   */
  @Test
  void testMaskAndUnmaskTags() {

    // init variables to be re-used between tests
    String unmasked = null;
    String masked = null;

    // one tag
    unmasked = "a b c " + OPEN1 + " x y z";
    masked = "a b c x" + OPEN1 + "c x y z";
    assertThat(MarianNmtConnector.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarianNmtConnector.unmaskTags(masked)).isEqualTo(unmasked);

    // tag at beginning
    unmasked = OPEN1 + " x y z";
    masked = "x" + OPEN1 + " x y z";
    assertThat(MarianNmtConnector.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarianNmtConnector.unmaskTags(masked)).isEqualTo(unmasked);

    // tag at end
    unmasked = "a b c " + OPEN1;
    masked = "a b c " + OPEN1 + "c";
    assertThat(MarianNmtConnector.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarianNmtConnector.unmaskTags(masked)).isEqualTo(unmasked);

    // two tags
    unmasked = "a b c " + ISO1 + " " + OPEN1 + " x y z";
    masked = "a b c x" + ISO1 + "c x" + OPEN1 + "c x y z";
    assertThat(MarianNmtConnector.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarianNmtConnector.unmaskTags(masked)).isEqualTo(unmasked);

    // two tags at beginning
    unmasked = ISO1 + " " + OPEN1 + " x y z";
    masked = "x" + ISO1 + " x" + OPEN1 + " x y z";
    assertThat(MarianNmtConnector.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarianNmtConnector.unmaskTags(masked)).isEqualTo(unmasked);

    // two tags at end
    unmasked = "a b c " + ISO1 + " " + OPEN1;
    masked = "a b c " + ISO1 + "c " + OPEN1 + "c";
    assertThat(MarianNmtConnector.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarianNmtConnector.unmaskTags(masked)).isEqualTo(unmasked);
  }


  /**
   * Test {@link MarianNmtConnector#convertSentencePieceToBpe(String)}.
   */
  @Test
  void testConvertSentencePieceToBpe() {

    // init variables to be re-used between tests
    String input = null;
    String expectedResult = null;

    // one in middle
    input = "_a _b c _d";
    expectedResult = String.format("a b@@ c d");
    testConvertSentencePieceToBpe(input, expectedResult);

    // two in middle
    input = "_a _b c d _e";
    expectedResult = String.format("a b@@ c@@ d e");
    testConvertSentencePieceToBpe(input, expectedResult);

    // one at end
    input = "_a _b _c d";
    expectedResult = String.format("a b c@@ d");
    testConvertSentencePieceToBpe(input, expectedResult);

    // two at end
    input = "_a _b c d";
    expectedResult = String.format("a b@@ c@@ d");
    testConvertSentencePieceToBpe(input, expectedResult);

    // two, split
    input = "_a _b c _d e _f";
    expectedResult = String.format("a b@@ c d@@ e f");
    testConvertSentencePieceToBpe(input, expectedResult);
  }


  @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
  private void testConvertSentencePieceToBpe(String input, String expectedResult) {

    input = input.replace('_', '\u2581');

    input = MarianNmtConnector.convertSentencePieceToBpe(input);
    assertThat(input)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            expectedResult, input))
        .isEqualTo(expectedResult);
  }
}
