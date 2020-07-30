package de.dfki.mlt.transins;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.sf.okapi.common.resource.TextFragment;

/**
 * Test class for {@link MarianNmtConnector}.
 *
 * @author Jörg Steffen, DFKI
 */
class MarianNmtConnectorTest {

  // declare tags in the format used by Okapi
  private static final String ISO =
      String.format("%c%c", TextFragment.MARKER_ISOLATED, 0 + TextFragment.CHARBASE);
  private static final String OPEN1 =
      String.format("%c%c", TextFragment.MARKER_OPENING, 1 + TextFragment.CHARBASE);
  private static final String CLOSE1 =
      String.format("%c%c", TextFragment.MARKER_CLOSING, 2 + TextFragment.CHARBASE);
  private static final String OPEN2 =
      String.format("%c%c", TextFragment.MARKER_OPENING, 3 + TextFragment.CHARBASE);
  private static final String CLOSE2 =
      String.format("%c%c", TextFragment.MARKER_CLOSING, 4 + TextFragment.CHARBASE);


  /**
   * Test {@link MarianNmtConnector#createSourceTokenIndex2Tags(String[], int)}.
   * This method is private, access for unit test achieved via reflection.
   */
  @Test
  void testCreateSourceTokenIndex2Tags()
      throws ReflectiveOperationException {

    // use reflection to make private method accessible
    String methodName = "createSourceTokenIndex2Tags";
    Method method =
        MarianNmtConnector.class.getDeclaredMethod(methodName, String[].class);
    method.setAccessible(true);

    String source =
        String.format("%s %s This %s is a %s test . %s %s", ISO, OPEN1, CLOSE1, OPEN2, CLOSE2, ISO);
    String[] sourceTokens = source.split(" ");

    @SuppressWarnings("unchecked")
    Map<Integer, List<String>> index2Tags =
        (Map<Integer, List<String>>)method.invoke(null, new Object[] { sourceTokens });

    assertThat(index2Tags).hasSize(4);
    assertThat(index2Tags.get(0)).containsExactly(ISO, OPEN1, CLOSE1);
    assertThat(index2Tags.get(3)).containsExactly(OPEN2);
    assertThat(index2Tags.get(4)).containsExactly(CLOSE2);
    assertThat(index2Tags.get(5)).containsExactly(ISO);
  }


  /**
   * Test {@link MarianNmtConnector#getTagsForSourceTokenIndex(Map, String[], int)}.
   * This method is private, access for unit test achieved via reflection.
   */
  @Test
  @SuppressWarnings("unchecked")
  void testGetTagsForSourceTokenIndex()
      throws ReflectiveOperationException {

    // use reflection to make private method accessible
    String methodName = "getTagsForSourceTokenIndex";
    Method method = MarianNmtConnector.class.getDeclaredMethod(
        methodName, int.class, Map.class, String[].class);
    method.setAccessible(true);

    String source =
        String.format("%s %s Th@@ i@@ s %s is a %s te@@ st . %s %s",
            ISO, OPEN1, CLOSE1, OPEN2, CLOSE2, ISO);
    String[] sourceTokensWithMarkup = source.split(" ");
    String[] sourceTokens = MarianNmtConnector.removeCodes(source).split(" ");
    Map<Integer, List<String>> sourceTokenIndex2tags =
        createSourceTokenIndex2tags(sourceTokensWithMarkup);

    List<String> tags = null;
    // source token indexes in the following test refer to source sentence
    // WITHOUT markup

    // non-bpe token 'is'
    tags = (List<String>)method.invoke(
        null, 3,
        new HashMap<>(sourceTokenIndex2tags),
        sourceTokens);
    assertThat(tags).isEmpty();

    // last bpe fragment 's'
    tags = (List<String>)method.invoke(
        null, 2,
        new HashMap<>(sourceTokenIndex2tags),
        sourceTokens);
    assertThat(tags).containsExactly(ISO, OPEN1, CLOSE1);

    // middle bpe fragment 'i@@'
    tags = (List<String>)method.invoke(
        null, 1,
        new HashMap<>(sourceTokenIndex2tags),
        sourceTokens);
    assertThat(tags).containsExactly(ISO, OPEN1, CLOSE1);

    // first bpe fragment 'Th@@'
    tags = (List<String>)method.invoke(
        null, 0,
        new HashMap<>(sourceTokenIndex2tags),
        sourceTokens);
    assertThat(tags).containsExactly(ISO, OPEN1, CLOSE1);

    // EOS
    tags = (List<String>)method.invoke(
        null, 8,
        new HashMap<>(sourceTokenIndex2tags),
        sourceTokens);
    assertThat(tags).containsExactly(ISO);
  }


  private static Map<Integer, List<String>> createSourceTokenIndex2tags(
      String[] sourceTokensWithMarkup)
      throws ReflectiveOperationException {

    // use reflection to make private method accessible
    String methodName = "createSourceTokenIndex2Tags";
    Method method = MarianNmtConnector.class.getDeclaredMethod(methodName, String[].class);
    method.setAccessible(true);

    @SuppressWarnings("unchecked")
    Map<Integer, List<String>> sourceTokenIndex2tags =
        (Map<Integer, List<String>>)method.invoke(null, new Object[] { sourceTokensWithMarkup });

    return sourceTokenIndex2tags;
  }


  /**
   * Test
   * {@link MarianNmtConnector#reinsertMarkup(String[], String[], String[], Alignments)}
   * with soft alignments.
   */
  @Test
  void testReinsertMarkupWithSoftAlignments() {

    String source =
        String.format("%s %s This %s is a %s test . %s %s",
            ISO, OPEN1, CLOSE1, OPEN2, CLOSE2, ISO);
    String[] sourceTokensWithMarkup = source.split(" ");
    String[] sourceTokensWithoutMarkup = MarianNmtConnector.removeCodes(source).split(" ");

    // init variables to be re-used between tests
    String target = null;
    String[] targetTokens = null;
    String rawAlignments = null;
    Alignments algn = null;
    String[] targetTokensWithMarkup = null;

    // first test
    target = "Das ist ein Test .";
    targetTokens = target.split(" ");

    rawAlignments = ""
        + "1,0,0,0,0,0 " // Das
        + "0,1,0,0,0,0 " // ist
        + "0,0,1,0,0,0 " // ein
        + "0,0,0,1,0,0 " // Test
        + "0,0,0,0,1,0 " // .
        + "0,0,0,0,0,1"; // EOS
    algn = new SoftAlignments(rawAlignments);

    targetTokensWithMarkup =
        MarianNmtConnector.reinsertMarkup(
            sourceTokensWithMarkup, sourceTokensWithoutMarkup, targetTokens, algn);
    assertThat(targetTokensWithMarkup)
        // provide human-readable string
        .as(Arrays.asList(MarianNmtConnector.replaceOkapiMarkup(targetTokensWithMarkup)) + "")
        .containsExactly(ISO, OPEN1, "Das", CLOSE1, "ist", "ein", OPEN2, "Test", ".", CLOSE2, ISO);


    // second test
    target = "Test ein ist das .";
    targetTokens = target.split(" ");

    rawAlignments = ""
        + "0,0,0,1,0,0 " // Test
        + "0,0,1,0,0,0 " // ein
        + "0,1,0,0,0,0 " // ist
        + "1,0,0,0,0,0 " // das
        + "0,0,0,0,1,0 " // .
        + "0,0,0,0,0,1"; // EOS
    algn = new SoftAlignments(rawAlignments);

    targetTokensWithMarkup =
        MarianNmtConnector.reinsertMarkup(
            sourceTokensWithMarkup, sourceTokensWithoutMarkup, targetTokens, algn);
    assertThat(targetTokensWithMarkup)
        // provide human-readable string
        .as(Arrays.asList(MarianNmtConnector.replaceOkapiMarkup(targetTokensWithMarkup)) + "")
        .containsExactly(ISO, OPEN2, "Test", "ein", "ist", OPEN1, "das", CLOSE1, ".", CLOSE2, ISO);
  }


  /**
   * Test
   * {@link MarianNmtConnector#reinsertMarkup(String[], String[], String[], Alignments)}
   * with hard alignments.
   */
  @Test
  void testReinsertMarkupWithHardAlignments() {

    String source =
        String.format("%s %s This %s is a %s test . %s %s", ISO, OPEN1, CLOSE1, OPEN2, CLOSE2, ISO);
    String[] sourceTokensWithMarkup = source.split(" ");
    String[] sourceTokensWithoutMarkup = MarianNmtConnector.removeCodes(source).split(" ");

    // init variables to be re-used between tests
    String target = null;
    String[] targetTokens = null;
    String rawAlignments = null;
    Alignments algn = null;
    String[] targetTokensWithMarkup = null;

    // first test
    target = "Das ist ein Test .";
    targetTokens = target.split(" ");

    rawAlignments = "0-0 1-1 2-2 3-3 4-4 5-5";
    algn = new HardAlignments(rawAlignments);

    targetTokensWithMarkup =
        MarianNmtConnector.reinsertMarkup(
            sourceTokensWithMarkup, sourceTokensWithoutMarkup, targetTokens, algn);

    assertThat(targetTokensWithMarkup)
        // provide human-readable string
        .as(Arrays.asList(MarianNmtConnector.replaceOkapiMarkup(targetTokensWithMarkup)) + "")
        .containsExactly(
            ISO, OPEN1, "Das", CLOSE1, "ist", "ein", OPEN2, "Test", ".", CLOSE2, ISO);

    // second test
    target = "Test ein ist das .";
    //        This is  a   Test .
    targetTokens = target.split(" ");

    rawAlignments = "0-3 1-2 2-1 3-0 4-4 5-5";
    algn = new HardAlignments(rawAlignments);

    targetTokensWithMarkup =
        MarianNmtConnector.reinsertMarkup(
            sourceTokensWithMarkup, sourceTokensWithoutMarkup, targetTokens, algn);

    assertThat(targetTokensWithMarkup)
        // provide human-readable string
        .as(Arrays.asList(MarianNmtConnector.replaceOkapiMarkup(targetTokensWithMarkup)) + "")
        .containsExactly(ISO, OPEN2, "Test", "ein", "ist", OPEN1, "das", CLOSE1, ".", CLOSE2, ISO);
  }


  /**
   * Test {@link MarianNmtConnector#detokenizeMarkup(String)}.
   */
  @Test
  void testDetokenizeMarkup() {

    String input = String.format("x y z %s a %s b %s c", ISO, OPEN1, CLOSE1);
    String detokenizedInput = String.format("x y z %sa %sb%s c", ISO, OPEN1, CLOSE1);
    assertThat(MarianNmtConnector.detokenizeMarkup(input)).isEqualTo(detokenizedInput);

    String input2 = String.format("x y z %s   a   %s  b   %s  c", ISO, OPEN1, CLOSE1);
    String detokenizedInput2 = String.format("x y z %sa   %sb%s  c", ISO, OPEN1, CLOSE1);
    assertThat(MarianNmtConnector.detokenizeMarkup(input2)).isEqualTo(detokenizedInput2);
  }


  /**
   * Test {@link MarianNmtConnector#moveMarkupBetweenBpeFragments(String[])}.
   */
  @Test
  void testMoveMarkupBetweenBpeFragments() {

    assertThat(MarianNmtConnector.moveMarkupBetweenBpeFragments(
        new String[] { "a", "b", "c@@", OPEN1, "x", "y", "z" }))
            .containsExactly("a", "b", OPEN1, "c@@", "x", "y", "z");

    assertThat(MarianNmtConnector.moveMarkupBetweenBpeFragments(
        new String[] { "a", "b@@", "c@@", OPEN1, "x", "y", "z" }))
            .containsExactly("a", OPEN1, "b@@", "c@@", "x", "y", "z");

    assertThat(MarianNmtConnector.moveMarkupBetweenBpeFragments(
        new String[] { "a@@", "b@@", "c@@", OPEN1, "x", "y", "z" }))
            .containsExactly(OPEN1, "a@@", "b@@", "c@@", "x", "y", "z");

    assertThat(MarianNmtConnector.moveMarkupBetweenBpeFragments(
        new String[] { "a@@", "b", "c@@", OPEN1, "x", "y", "z" }))
            .containsExactly("a@@", "b", OPEN1, "c@@", "x", "y", "z");

    assertThat(MarianNmtConnector.moveMarkupBetweenBpeFragments(
        new String[] { "a", "b", "c@@", CLOSE1, "x", "y", "z" }))
            .containsExactly("a", "b", "c@@", "x", CLOSE1, "y", "z");

    assertThat(MarianNmtConnector.moveMarkupBetweenBpeFragments(
        new String[] { "a", "b", "c@@", CLOSE1, "x", "y", "z" }))
            .containsExactly("a", "b", "c@@", "x", CLOSE1, "y", "z");

    assertThat(MarianNmtConnector.moveMarkupBetweenBpeFragments(
        new String[] { "a", "b", "c@@", CLOSE1, "x@@", "y", "z" }))
            .containsExactly("a", "b", "c@@", "x@@", "y", CLOSE1, "z");

    assertThat(MarianNmtConnector.moveMarkupBetweenBpeFragments(
        new String[] { "a", "b", "c@@", CLOSE1, "x@@", "y@@", "z" }))
            .containsExactly("a", "b", "c@@", "x@@", "y@@", "z", CLOSE1);

    assertThat(MarianNmtConnector.moveMarkupBetweenBpeFragments(
        new String[] { "a", "b", "c@@", CLOSE1, "x", "y@@", "z" }))
            .containsExactly("a", "b", "c@@", "x", CLOSE1, "y@@", "z");

    assertThat(MarianNmtConnector.moveMarkupBetweenBpeFragments(
        new String[] { "a", "b", "c@@", OPEN1, CLOSE1, "x", "y@@", "z" }))
            .containsExactly("a", "b", OPEN1, "c@@", "x", CLOSE1, "y@@", "z");

    assertThat(MarianNmtConnector.moveMarkupBetweenBpeFragments(
        new String[] { "a", "b", "c@@", CLOSE1, OPEN1, "x", "y@@", "z" }))
            .containsExactly("a", "b", OPEN1, "c@@", "x", CLOSE1, "y@@", "z");
  }


  /**
   * Test {@link MarianNmtConnector#maskMarkup(String[])}.
   */
  @Test
  void testMaskUnmaskMarkup() {

    String unmasked1 = "a b c " + OPEN1 + " x y z";
    String masked1 = "a b c x" + OPEN1 + "c x y z";
    assertThat(MarianNmtConnector.maskMarkup(unmasked1.split(" "))).isEqualTo(masked1);
    assertThat(MarianNmtConnector.unmaskMarkup(masked1)).isEqualTo(unmasked1);

    String unmasked2 = OPEN1 + " x y z";
    String masked2 = "x" + OPEN1 + " x y z";
    assertThat(MarianNmtConnector.maskMarkup(unmasked2.split(" "))).isEqualTo(masked2);
    assertThat(MarianNmtConnector.unmaskMarkup(masked2)).isEqualTo(unmasked2);

    String unmasked3 = "a b c " + OPEN1;
    String masked3 = "a b c " + OPEN1 + "c";
    assertThat(MarianNmtConnector.maskMarkup(unmasked3.split(" "))).isEqualTo(masked3);
    assertThat(MarianNmtConnector.unmaskMarkup(masked3)).isEqualTo(unmasked3);

    String unmasked4 = "a b c " + ISO + " " + OPEN1 + " x y z";
    String masked4 = "a b c x" + ISO + "c x" + OPEN1 + "c x y z";
    assertThat(MarianNmtConnector.maskMarkup(unmasked4.split(" "))).isEqualTo(masked4);
    assertThat(MarianNmtConnector.unmaskMarkup(masked4)).isEqualTo(unmasked4);

    String unmasked5 = ISO + " " + OPEN1 + " x y z";
    String masked5 = "x" + ISO + " x" + OPEN1 + " x y z";
    assertThat(MarianNmtConnector.maskMarkup(unmasked5.split(" "))).isEqualTo(masked5);
    assertThat(MarianNmtConnector.unmaskMarkup(masked5)).isEqualTo(unmasked5);

    String unmasked6 = "a b c " + ISO + " " + OPEN1;
    String masked6 = "a b c " + ISO + "c " + OPEN1 + "c";
    assertThat(MarianNmtConnector.maskMarkup(unmasked6.split(" "))).isEqualTo(masked6);
    assertThat(MarianNmtConnector.unmaskMarkup(masked6)).isEqualTo(unmasked6);
  }
}
