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
}
