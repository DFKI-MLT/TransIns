package de.dfki.mlt.transins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

/**
 * Test class for {@link SoftAlignments}.
 *
 * @author Jörg Steffen, DFKI
 */
class SoftAlignmentsTest {

  /**
   * Test {@link SoftAlignments} constructor.
   */
  @Test
  void testConstructor() {

    String rawAlignments = null;
    SoftAlignments algn = null;

    // empty
    rawAlignments = "";
    algn = new SoftAlignments(rawAlignments);
    assertThat(algn.getAlignmentScores()).isNull();

    // wrong format
    assertThatExceptionOfType(NumberFormatException.class).isThrownBy(() -> {
      new SoftAlignments("abcd");
    });

    // single line
    rawAlignments = "0.1,0.2,0.3";
    algn = new SoftAlignments(rawAlignments);
    assertThat(algn.getAlignmentScores()).hasSize(1);
    assertThat(algn.getAlignmentScores()[0]).hasSize(3);

    // multiple lines
    rawAlignments = "0.1,0.2,0.3 0.6,0.4,0.5";
    algn = new SoftAlignments(rawAlignments);
    assertThat(algn.getAlignmentScores()).hasSize(2);
    assertThat(algn.getAlignmentScores()[0]).hasSize(3);
    assertThat(algn.getAlignmentScores()[1]).hasSize(3);
  }


  /**
   * Test {@link SoftAlignments#getBestSourceTokenIndex(int)} and
   * {@link SoftAlignments#getBestSourceTokenIndex(int, double)}.
   */
  @Test
  void testGetBestSourceToken() {

    String rawAlignments = "0.1,0.2,0.3 0.6,0.4,0.5";
    SoftAlignments algn = new SoftAlignments(rawAlignments);

    assertThat(algn.getBestSourceTokenIndex(0, 0.0)).isEqualTo(2);
    assertThat(algn.getBestSourceTokenIndex(1, 0.0)).isEqualTo(0);
    assertThat(algn.getBestSourceTokenIndex(0, 0.5)).isEqualTo(-1);
  }


  /**
   * Test {@link SoftAlignments#getSourceTokenIndexes(int, double)}.
   */
  @Test
  void testGetSourceTokens() {

    String rawAlignments = "0.1,0.2,0.3 0.6,0.4,0.5";
    SoftAlignments algn = new SoftAlignments(rawAlignments);
    assertThat(algn.getSourceTokenIndexes(0, 0.0)).containsSequence(0, 1, 2);
    assertThat(algn.getSourceTokenIndexes(1, 0.0)).containsSequence(0, 1, 2);
    assertThat(algn.getSourceTokenIndexes(0, 0.5)).isEmpty();
    assertThat(algn.getSourceTokenIndexes(0, 0.3)).containsExactly(2);
    assertThat(algn.getSourceTokenIndexes(0, 0.2)).containsExactly(1, 2);
  }


  /**
   * Test {@link HardAlignments#getPointedSourceTokens()}.
   */
  @Test
  void testGetPointedSourceTokens() {

    String rawAlignments = null;
    SoftAlignments algn = null;

    // multiple targets to one source token
    rawAlignments = "1.0,0.0,0.0 1.0,0.0,0.0 1.0,0.0,0.0";
    algn = new SoftAlignments(rawAlignments);
    assertThat(algn.getPointedSourceTokens()).containsExactly(0);

    // one-to-one mapping
    rawAlignments = "0.3,0.2,0.1 0.4,0.6,0.5 0.7,0.8,0.9";
    algn = new SoftAlignments(rawAlignments);
    assertThat(algn.getPointedSourceTokens()).containsExactly(0, 1, 2);

    // one-to-one with gaps
    rawAlignments = "0.3,0.2,0.1 0.6,0.4,0.5 0.7,0.8,0.9";
    algn = new SoftAlignments(rawAlignments);
    assertThat(algn.getPointedSourceTokens()).containsExactly(0, 2);
  }


  /**
   * Test {@link SoftAlignments#toHardAlignments(double)}.
   */
  @Test
  void testToHardAlignments() {

    String rawAlignments = "0.1,0.2,0.3 0.6,0.4,0.5";
    SoftAlignments algn = new SoftAlignments(rawAlignments);

    String hardAlgn = null;

    hardAlgn = algn.toHardAlignments(0.1);
    assertThat(hardAlgn).isEqualTo("0-0 0-1 1-0 1-1 2-0 2-1");

    hardAlgn = algn.toHardAlignments(0.2);
    assertThat(hardAlgn).isEqualTo("0-1 1-0 1-1 2-0 2-1");

    hardAlgn = algn.toHardAlignments(0.3);
    assertThat(hardAlgn).isEqualTo("0-1 1-1 2-0 2-1");

    hardAlgn = algn.toHardAlignments(0.4);
    assertThat(hardAlgn).isEqualTo("0-1 1-1 2-1");
  }


  /**
   * Test {@link SoftAlignments#toBestAlignments()}.
   */
  @Test
  void testToBestAlignments() {

    String rawAlignments = "0.1,0.2,0.3 0.6,0.4,0.5";
    SoftAlignments algn = new SoftAlignments(rawAlignments);
    assertThat(algn.toBestAlignments()).isEqualTo("0-2 1-0");
  }


  /**
   * Test {@link SoftAlignments#shiftSourceIndexes(int)}.
   */
  @Test
  void testShiftSourceIndexes() {

    String rawAlignments = "0.1,0.2,0.3 0.6,0.4,0.5";
    SoftAlignments algn = new SoftAlignments(rawAlignments);
    algn.shiftSourceIndexes(0);
    assertThat(algn.toHardAlignments(0.4)).isEqualTo("0-1 1-1 2-1");
    algn.shiftSourceIndexes(1);
    assertThat(algn.toHardAlignments(0.4)).isEqualTo("1-1 2-1 3-1");
    algn.shiftSourceIndexes(-1);
    assertThat(algn.toHardAlignments(0.4)).isEqualTo("0-1 1-1 2-1");
    algn.shiftSourceIndexes(-1);
    assertThat(algn.toHardAlignments(0.4)).isEqualTo("0-1 1-1");
  }


  /**
   * Test {@link SoftAlignments#shiftTargetIndexes(int)}.
   */
  @Test
  void testShiftTargetIndexes() {

    String rawAlignments = "0.1,0.2,0.3 0.6,0.4,0.5";
    SoftAlignments algn = new SoftAlignments(rawAlignments);
    algn.shiftTargetIndexes(0);
    assertThat(algn.toHardAlignments(0.4)).isEqualTo("0-1 1-1 2-1");
    algn.shiftTargetIndexes(1);
    assertThat(algn.toHardAlignments(0.4)).isEqualTo("0-2 1-2 2-2");
    algn.shiftTargetIndexes(-1);
    assertThat(algn.toHardAlignments(0.4)).isEqualTo("0-1 1-1 2-1");
    algn.shiftTargetIndexes(-2);
    assertThat(algn.toHardAlignments(0.4)).isEmpty();
  }
}
