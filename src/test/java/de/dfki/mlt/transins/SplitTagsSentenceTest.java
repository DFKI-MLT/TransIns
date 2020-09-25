package de.dfki.mlt.transins;

import static de.dfki.mlt.transins.TestUtils.CLOSE1;
import static de.dfki.mlt.transins.TestUtils.CLOSE2;
import static de.dfki.mlt.transins.TestUtils.ISO1;
import static de.dfki.mlt.transins.TestUtils.ISO2;
import static de.dfki.mlt.transins.TestUtils.OPEN1;
import static de.dfki.mlt.transins.TestUtils.OPEN2;
import static de.dfki.mlt.transins.TestUtils.asArray;
import static de.dfki.mlt.transins.TestUtils.asString;
import static de.dfki.mlt.transins.TestUtils.opening2ClosingTag;
import static de.dfki.mlt.transins.TestUtils.closing2OpeningTag;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Test class for {@link SplitTagsSentence}.
 *
 * @author Jörg Steffen, DFKI
 */
public class SplitTagsSentenceTest {

  /**
   * Test {@link SplitTagsSentence} constructor.
   */
  @Test
  void testSplitTagsSentenceConstructor() {

    // init variables to be re-used between tests
    String[] tokensWithTags = null;
    String[] expectedResult = null;
    SplitTagsSentence result = null;

    // one tag pair
    tokensWithTags = asArray("OPEN1 x y z CLOSE1");
    expectedResult = asArray("x y z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).containsExactly(OPEN1);
    assertThat(result.getEndOfSentenceTags()).containsExactly(CLOSE1);

    // two tag pairs
    tokensWithTags = asArray("OPEN1 OPEN2 x y z CLOSE2 CLOSE1");
    expectedResult = asArray("x y z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).containsExactly(OPEN1, OPEN2);
    assertThat(result.getEndOfSentenceTags()).containsExactly(CLOSE2, CLOSE1);

    // one tag pair, opening not at beginning
    tokensWithTags = asArray("x OPEN1 y z CLOSE1");
    expectedResult = asArray("x OPEN1 y z CLOSE1");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).isEmpty();
    assertThat(result.getEndOfSentenceTags()).isEmpty();

    // one tag pair, closing not at end
    tokensWithTags = asArray("OPEN1 x y CLOSE1 z");
    expectedResult = asArray("OPEN1 x y CLOSE1 z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).isEmpty();
    assertThat(result.getEndOfSentenceTags()).isEmpty();

    // one tag pair, opening and closing not at beginning and end
    tokensWithTags = asArray("x OPEN1 y CLOSE1 z");
    expectedResult = asArray("x OPEN1 y CLOSE1 z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).isEmpty();
    assertThat(result.getEndOfSentenceTags()).isEmpty();

    // one ISO at beginning
    tokensWithTags = asArray("ISO1 x y z");
    expectedResult = asArray("x y z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).containsExactly(ISO1);
    assertThat(result.getEndOfSentenceTags()).isEmpty();

    // one ISO not at beginning
    tokensWithTags = asArray("x ISO1 y z");
    expectedResult = asArray("x ISO1 y z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).isEmpty();
    assertThat(result.getEndOfSentenceTags()).isEmpty();

    // one ISO at end
    tokensWithTags = asArray("x y z ISO1");
    expectedResult = asArray("x y z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).isEmpty();
    assertThat(result.getEndOfSentenceTags()).containsExactly(ISO1);

    // one ISO not at beginning
    tokensWithTags = asArray("x y ISO1 z");
    expectedResult = asArray("x y ISO1 z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).isEmpty();
    assertThat(result.getEndOfSentenceTags()).isEmpty();

    // two ISOs at beginning and end
    tokensWithTags = asArray("ISO1 x y z ISO2");
    expectedResult = asArray("x y z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).containsExactly(ISO1);
    assertThat(result.getEndOfSentenceTags()).containsExactly(ISO2);

    // two ISOs at other position
    tokensWithTags = asArray("x ISO1 y ISO2 z");
    expectedResult = asArray("x ISO1 y ISO2 z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).isEmpty();
    assertThat(result.getEndOfSentenceTags()).isEmpty();

    // ISO at beginning and tag pair, ISO first
    tokensWithTags = asArray("ISO1 OPEN1 x y z CLOSE1");
    expectedResult = asArray("x y z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).containsExactly(ISO1, OPEN1);
    assertThat(result.getEndOfSentenceTags()).containsExactly(CLOSE1);

    // ISO at beginning and tag pair, ISO second
    tokensWithTags = asArray("OPEN1 ISO1 x y z CLOSE1");
    expectedResult = asArray("x y z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).containsExactly(OPEN1, ISO1);
    assertThat(result.getEndOfSentenceTags()).containsExactly(CLOSE1);

    // ISO at end and tag pair, ISO last
    tokensWithTags = asArray("OPEN1 x y z CLOSE1 ISO1");
    expectedResult = asArray("x y z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).containsExactly(OPEN1);
    assertThat(result.getEndOfSentenceTags()).containsExactly(CLOSE1, ISO1);

    // ISO at end and tag pair, ISO not last
    tokensWithTags = asArray("OPEN1 x y z ISO1 CLOSE1");
    expectedResult = asArray("x y z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).containsExactly(OPEN1);
    assertThat(result.getEndOfSentenceTags()).containsExactly(ISO1, CLOSE1);

    // ISO at end and tag pair not over whole sentence, ISO not last
    tokensWithTags = asArray("x OPEN1 y z ISO1 CLOSE1");
    expectedResult = asArray("x OPEN1 y z CLOSE1");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).isEmpty();
    assertThat(result.getEndOfSentenceTags()).containsExactly(ISO1);

    // empty tag pair at beginning
    tokensWithTags = asArray("OPEN1 CLOSE1 x y z");
    expectedResult = asArray("x y z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).containsExactly(OPEN1, CLOSE1);
    assertThat(result.getEndOfSentenceTags()).isEmpty();

    // empty tag pair at end
    tokensWithTags = asArray("x y z OPEN1 CLOSE1");
    expectedResult = asArray("x y z");
    result = testSplitTagsSentenceConstructor(tokensWithTags, expectedResult);
    assertThat(result.getBeginningOfSentenceTags()).isEmpty();
    assertThat(result.getEndOfSentenceTags()).containsExactly(OPEN1, CLOSE1);
  }


  private SplitTagsSentence testSplitTagsSentenceConstructor(
      String[] tokensWithTags, String[] expectedResult) {

    SplitTagsSentence splitTagsSentence =
        new SplitTagsSentence(tokensWithTags, opening2ClosingTag, closing2OpeningTag);
    assertThat(splitTagsSentence.getTokensWithTags())
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(expectedResult),
            asString(splitTagsSentence.getTokensWithTags())))
        .containsExactly(expectedResult);

    return splitTagsSentence;
  }
}
