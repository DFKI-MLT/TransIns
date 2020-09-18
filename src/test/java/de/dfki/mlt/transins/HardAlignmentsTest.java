package de.dfki.mlt.transins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

/**
 * Test class for {@link HardAlignments}.
 *
 * @author Jörg Steffen, DFKI
 */
class HardAlignmentsTest {

  @Test
  void testConstructor() {

    String rawAlignments = null;
    HardAlignments algn = null;

    // empty
    rawAlignments = "";
    algn = new HardAlignments(rawAlignments);
    assertThat(algn.getTarget2SourcesMapping()).isNull();

    // wrong format
    assertThatExceptionOfType(NumberFormatException.class).isThrownBy(() -> {
      new HardAlignments("ab-cd");
    });

    // standard
    rawAlignments = "1-1 2-2 3-3";
    algn = new HardAlignments(rawAlignments);
    assertThat(algn.getTarget2SourcesMapping()).hasSize(3);

    // one target target to multiple source tokens
    rawAlignments = "1-1 2-1 3-1";
    algn = new HardAlignments(rawAlignments);
    assertThat(algn.getTarget2SourcesMapping()).hasSize(1);
    assertThat(algn.getTarget2SourcesMapping().get(1)).hasSize(3);
    assertThat(algn.getTarget2SourcesMapping().get(1)).containsExactly(1, 2, 3);

    // one target target to multiple source tokens, inverse order
    rawAlignments = "3-1 2-1 1-1";
    algn = new HardAlignments(rawAlignments);
    assertThat(algn.getTarget2SourcesMapping()).hasSize(1);
    assertThat(algn.getTarget2SourcesMapping().get(1)).hasSize(3);
    assertThat(algn.getTarget2SourcesMapping().get(1)).containsExactly(1, 2, 3);
  }


  /**
   * Test {@link HardAlignments#getSourceTokenIndexes(int)}.
   */
  @Test
  void testGetSourceTokenIndexes() {

    String rawAlignments = null;
    HardAlignments algn = null;

    // one target target to multiple source tokens
    rawAlignments = "1-1 2-1 3-1";
    algn = new HardAlignments(rawAlignments);
    assertThat(algn.getSourceTokenIndexes(1)).containsExactly(1, 2, 3);

    // one target target to multiple source tokens, inverse order
    rawAlignments = "3-1 2-1 1-1";
    algn = new HardAlignments(rawAlignments);
    assertThat(algn.getSourceTokenIndexes(1)).containsExactly(1, 2, 3);

    // no mapping
    rawAlignments = "1-2 2-2 3-3";
    algn = new HardAlignments(rawAlignments);
    assertThat(algn.getSourceTokenIndexes(1)).isEmpty();
    assertThat(algn.getSourceTokenIndexes(4)).isEmpty();
  }


  /**
   * Test {@link HardAlignments#getPointedSourceTokens()}.
   */
  @Test
  void testGetPointedSourceTokens() {

    String rawAlignments = null;
    HardAlignments algn = null;

    // one target target to multiple source tokens
    rawAlignments = "1-1 2-1 3-1";
    algn = new HardAlignments(rawAlignments);
    assertThat(algn.getPointedSourceTokens()).containsExactly(1, 2, 3);

    // multiple targets to one source token
    rawAlignments = "1-1 1-2 1-3";
    algn = new HardAlignments(rawAlignments);
    assertThat(algn.getPointedSourceTokens()).containsExactly(1);

    // one-to-one mapping
    rawAlignments = "1-1 2-2 3-3";
    algn = new HardAlignments(rawAlignments);
    assertThat(algn.getPointedSourceTokens()).containsExactly(1, 2, 3);

    // one-to-one with gaps
    rawAlignments = "1-1 1-2 3-3";
    algn = new HardAlignments(rawAlignments);
    assertThat(algn.getPointedSourceTokens()).containsExactly(1, 3);
  }


  /**
   * Test {@link HardAlignments#toString()} and {@link HardAlignments#toStringAsProvidedByMarian()}.
   */
  @Test
  void testToString() {

    String rawAlignments = "1-1 2-1 3-1";
    HardAlignments algn = new HardAlignments(rawAlignments);
    assertThat(algn.toStringAsProvidedByMarian()).isEqualTo("1-1 2-1 3-1");
    assertThat(algn.toString()).isEqualTo("1-1 1-2 1-3");
  }


  /**
   * Test {@link HardAlignments#shiftSourceIndexes(int)}.
   */
  @Test
  void testShiftSourceIndexes() {

    String rawAlignments = "1-1 2-2 3-3";
    HardAlignments algn = new HardAlignments(rawAlignments);
    algn.shiftSourceIndexes(0);
    assertThat(algn.toString()).isEqualTo("1-1 2-2 3-3");
    algn.shiftSourceIndexes(-1);
    assertThat(algn.toString()).isEqualTo("1-0 2-1 3-2");
    algn.shiftSourceIndexes(2);
    assertThat(algn.toString()).isEqualTo("1-2 2-3 3-4");
    algn.shiftSourceIndexes(-3);
    assertThat(algn.toString()).isEqualTo("2-0 3-1");
  }


  /**
   * Test {@link HardAlignments#shiftTargetIndexes(int)}.
   */
  @Test
  void testShiftTargetIndexes() {

    String rawAlignments = "1-1 2-2 3-3";
    HardAlignments algn = new HardAlignments(rawAlignments);
    algn.shiftTargetIndexes(0);
    assertThat(algn.toString()).isEqualTo("1-1 2-2 3-3");
    algn.shiftTargetIndexes(-1);
    assertThat(algn.toString()).isEqualTo("0-1 1-2 2-3");
    algn.shiftTargetIndexes(2);
    assertThat(algn.toString()).isEqualTo("2-1 3-2 4-3");
    algn.shiftTargetIndexes(-3);
    assertThat(algn.toString()).isEqualTo("0-2 1-3");
  }
}
