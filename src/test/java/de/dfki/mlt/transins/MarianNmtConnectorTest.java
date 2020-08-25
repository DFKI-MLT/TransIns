package de.dfki.mlt.transins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Test class for {@link MarianNmtConnector}.
 *
 * @author Jörg Steffen, DFKI
 */
class MarianNmtConnectorTest {

  // declare tags in the format used by Okapi
  private static final String ISO = MarianNmtConnector.createIsolatedTag(0);
  private static final String OPEN1 = MarianNmtConnector.createOpeningTag(1);
  private static final String CLOSE1 = MarianNmtConnector.createClosingTag(2);
  private static final String OPEN2 = MarianNmtConnector.createOpeningTag(3);
  private static final String CLOSE2 = MarianNmtConnector.createClosingTag(4);


  /**
   * Test {@link MarianNmtConnector#createTagIdMap(String)}.
   */
  @Test
  void testCreateTagIdMap() {

    // init variables to be re-used between tests
    String[] sourceTokensWithTags = null;
    Map<Integer, Integer> closing2OpeningTag = null;

    // first test
    sourceTokensWithTags =
        String.format("%s %s This %s is a %s test . %s %s", ISO, OPEN1, CLOSE1, OPEN2, CLOSE2, ISO)
            .split(" ");
    closing2OpeningTag = MarianNmtConnector.createTagIdMap(sourceTokensWithTags);
    assertThat(closing2OpeningTag).contains(entry(2, 1), entry(4, 3));

    // second test
    sourceTokensWithTags =
        String.format("%s %s This %s is a %s test . %s %s", ISO, OPEN1, OPEN2, CLOSE2, CLOSE1, ISO)
            .split(" ");
    closing2OpeningTag = MarianNmtConnector.createTagIdMap(sourceTokensWithTags);
    assertThat(closing2OpeningTag).contains(entry(2, 1), entry(4, 3));
  }


  /**
   * Test {@link MarianNmtConnector#createSourceTokenIndex2Tags(String[], int)}.
   */
  @Test
  void testCreateSourceTokenIndex2Tags() {

    String source =
        String.format("%s %s This %s is a %s test . %s %s", ISO, OPEN1, CLOSE1, OPEN2, CLOSE2, ISO);
    String[] sourceTokens = source.split(" ");

    Map<Integer, List<String>> index2Tags =
        MarianNmtConnector.createSourceTokenIndex2Tags(sourceTokens);

    assertThat(index2Tags).hasSize(4);
    assertThat(index2Tags.get(0)).containsExactly(ISO, OPEN1, CLOSE1);
    assertThat(index2Tags.get(3)).containsExactly(OPEN2);
    assertThat(index2Tags.get(4)).containsExactly(CLOSE2);
    assertThat(index2Tags.get(5)).containsExactly(ISO);
  }


  /**
   * Test {@link MarianNmtConnector#moveSourceTagsToPointedTokens(Map, Map, List, int)}.
   */
  @Test
  void testMoveSourceTagsToPointedTokens()
      throws ReflectiveOperationException {

    // init variables to be re-used between tests
    String source = null;
    String[] sourceTokensWithTags = null;
    String[] sourceTokens = null;
    Map<Integer, List<String>> sourceTokenIndex2tags = null;
    Map<Integer, Integer> closing2OpeningTagId = null;
    List<Integer> pointedSourceTokens = null;

    // first test
    source = String.format("%s %s %s This %s is a %s test . %s",
        ISO, OPEN1, OPEN2, CLOSE2, CLOSE1, ISO);
    pointedSourceTokens = Arrays.asList(new Integer[] { 1, 2 });
    sourceTokensWithTags = source.split(" ");
    sourceTokenIndex2tags = createSourceTokenIndex2tags(sourceTokensWithTags);
    sourceTokens = MarianNmtConnector.removeTags(source).split(" ");
    closing2OpeningTagId = MarianNmtConnector.createTagIdMap(sourceTokensWithTags);

    MarianNmtConnector.moveSourceTagsToPointedTokens(
        sourceTokenIndex2tags, closing2OpeningTagId, pointedSourceTokens, sourceTokens.length);

    assertThat(sourceTokenIndex2tags).hasSize(4);
    assertThat(sourceTokenIndex2tags.get(0)).containsExactly(ISO);
    assertThat(sourceTokenIndex2tags.get(1)).containsExactly(OPEN1);
    assertThat(sourceTokenIndex2tags.get(2)).containsExactly(CLOSE1);
    assertThat(sourceTokenIndex2tags.get(5)).containsExactly(OPEN2, ISO, CLOSE2);

    // second test
    source = String.format("%s %s %s This %s is a %s test . %s",
        ISO, OPEN1, OPEN2, CLOSE2, CLOSE1, ISO);
    pointedSourceTokens = Arrays.asList(new Integer[] { 0, 1, 2 });
    sourceTokensWithTags = source.split(" ");
    sourceTokenIndex2tags = createSourceTokenIndex2tags(sourceTokensWithTags);
    sourceTokens = MarianNmtConnector.removeTags(source).split(" ");
    closing2OpeningTagId = MarianNmtConnector.createTagIdMap(sourceTokensWithTags);

    MarianNmtConnector.moveSourceTagsToPointedTokens(
        sourceTokenIndex2tags, closing2OpeningTagId, pointedSourceTokens, sourceTokens.length);

    assertThat(sourceTokenIndex2tags).hasSize(3);
    assertThat(sourceTokenIndex2tags.get(0)).containsExactly(ISO, OPEN1, OPEN2, CLOSE2);
    assertThat(sourceTokenIndex2tags.get(2)).containsExactly(CLOSE1);
    assertThat(sourceTokenIndex2tags.get(5)).containsExactly(ISO);

    // third test
    source = String.format("%s %s %s x %s y z a %s b c", ISO, OPEN1, OPEN2, CLOSE2, CLOSE1);
    pointedSourceTokens = Arrays.asList(new Integer[] { 1, 2 });
    sourceTokensWithTags = source.split(" ");
    sourceTokenIndex2tags = createSourceTokenIndex2tags(sourceTokensWithTags);
    sourceTokens = MarianNmtConnector.removeTags(source).split(" ");
    closing2OpeningTagId = MarianNmtConnector.createTagIdMap(sourceTokensWithTags);

    MarianNmtConnector.moveSourceTagsToPointedTokens(
        sourceTokenIndex2tags, closing2OpeningTagId, pointedSourceTokens, sourceTokens.length);

    assertThat(sourceTokenIndex2tags).hasSize(4);
    assertThat(sourceTokenIndex2tags.get(0)).containsExactly(ISO);
    assertThat(sourceTokenIndex2tags.get(1)).containsExactly(OPEN1);
    assertThat(sourceTokenIndex2tags.get(2)).containsExactly(CLOSE1);
    assertThat(sourceTokenIndex2tags.get(6)).containsExactly(OPEN2, CLOSE2);
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
    String[] sourceTokensWithTags = source.split(" ");
    String[] sourceTokens = MarianNmtConnector.removeTags(source).split(" ");
    Map<Integer, List<String>> sourceTokenIndex2tags =
        createSourceTokenIndex2tags(sourceTokensWithTags);

    List<String> tags = null;
    // source token indexes in the following test refer to source sentence
    // WITHOUT tags

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


  /**
   * Test
   * {@link MarianNmtConnector#reinsertTags(String[], String[], String[], Alignments)}
   * with soft alignments.
   */
  @Test
  void testReinsertTagsWithSoftAlignments() {

    String source =
        String.format("%s %s This %s is a %s test . %s %s",
            ISO, OPEN1, CLOSE1, OPEN2, CLOSE2, ISO);
    String[] sourceTokensWithTags = source.split(" ");
    String[] sourceTokensWithoutTags = MarianNmtConnector.removeTags(source).split(" ");

    // init variables to be re-used between tests
    String target = null;
    String[] targetTokensWithoutTags = null;
    String rawAlignments = null;
    Alignments algn = null;
    Map<Integer, List<String>> sourceTokenIndex2tags = null;
    String[] targetTokensWithTags = null;

    // first test
    target = "Das ist ein Test .";
    targetTokensWithoutTags = target.split(" ");

    rawAlignments = ""
        + "1,0,0,0,0,0 " // Das
        + "0,1,0,0,0,0 " // ist
        + "0,0,1,0,0,0 " // ein
        + "0,0,0,1,0,0 " // Test
        + "0,0,0,0,1,0 " // .
        + "0,0,0,0,0,1"; // EOS
    algn = new SoftAlignments(rawAlignments);

    sourceTokenIndex2tags = MarianNmtConnector.createSourceTokenIndex2Tags(sourceTokensWithTags);

    targetTokensWithTags =
        MarianNmtConnector.reinsertTags(
            sourceTokensWithoutTags, targetTokensWithoutTags, algn, sourceTokenIndex2tags);
    assertThat(targetTokensWithTags)
        // provide human-readable string
        .as(Arrays.asList(MarianNmtConnector.replaceOkapiTags(targetTokensWithTags)) + "")
        .containsExactly(ISO, OPEN1, "Das", CLOSE1, "ist", "ein", OPEN2, "Test", ".", CLOSE2, ISO);


    // second test
    target = "Test ein ist das .";
    targetTokensWithoutTags = target.split(" ");

    rawAlignments = ""
        + "0,0,0,1,0,0 " // Test
        + "0,0,1,0,0,0 " // ein
        + "0,1,0,0,0,0 " // ist
        + "1,0,0,0,0,0 " // das
        + "0,0,0,0,1,0 " // .
        + "0,0,0,0,0,1"; // EOS
    algn = new SoftAlignments(rawAlignments);

    sourceTokenIndex2tags = MarianNmtConnector.createSourceTokenIndex2Tags(sourceTokensWithTags);

    targetTokensWithTags =
        MarianNmtConnector.reinsertTags(
            sourceTokensWithoutTags, targetTokensWithoutTags, algn, sourceTokenIndex2tags);
    assertThat(targetTokensWithTags)
        // provide human-readable string
        .as(Arrays.asList(MarianNmtConnector.replaceOkapiTags(targetTokensWithTags)) + "")
        .containsExactly(ISO, OPEN2, "Test", "ein", "ist", OPEN1, "das", CLOSE1, ".", CLOSE2, ISO);
  }


  /**
   * Test
   * {@link MarianNmtConnector#reinsertTags(String[], String[], String[], Alignments)}
   * with hard alignments.
   */
  @Test
  void testReinsertTagsWithHardAlignments() {

    String source =
        String.format("%s %s This %s is a %s test . %s %s", ISO, OPEN1, CLOSE1, OPEN2, CLOSE2, ISO);
    String[] sourceTokensWithTags = source.split(" ");
    String[] sourceTokensWithoutTags = MarianNmtConnector.removeTags(source).split(" ");

    // init variables to be re-used between tests
    String target = null;
    String[] targetTokensWithoutTags = null;
    String rawAlignments = null;
    Alignments algn = null;
    Map<Integer, List<String>> sourceTokenIndex2tags = null;
    String[] targetTokensWithTags = null;

    // first test
    target = "Das ist ein Test .";
    targetTokensWithoutTags = target.split(" ");

    rawAlignments = "0-0 1-1 2-2 3-3 4-4 5-5";
    algn = new HardAlignments(rawAlignments);

    sourceTokenIndex2tags = MarianNmtConnector.createSourceTokenIndex2Tags(sourceTokensWithTags);

    targetTokensWithTags =
        MarianNmtConnector.reinsertTags(
            sourceTokensWithoutTags, targetTokensWithoutTags, algn, sourceTokenIndex2tags);

    assertThat(targetTokensWithTags)
        // provide human-readable string
        .as(Arrays.asList(MarianNmtConnector.replaceOkapiTags(targetTokensWithTags)) + "")
        .containsExactly(
            ISO, OPEN1, "Das", CLOSE1, "ist", "ein", OPEN2, "Test", ".", CLOSE2, ISO);

    // second test
    target = "Test ein ist das .";
    //        This is  a   Test .
    targetTokensWithoutTags = target.split(" ");

    rawAlignments = "0-3 1-2 2-1 3-0 4-4 5-5";
    algn = new HardAlignments(rawAlignments);

    sourceTokenIndex2tags = MarianNmtConnector.createSourceTokenIndex2Tags(sourceTokensWithTags);

    targetTokensWithTags =
        MarianNmtConnector.reinsertTags(
            sourceTokensWithTags, targetTokensWithoutTags, algn, sourceTokenIndex2tags);

    assertThat(targetTokensWithTags)
        // provide human-readable string
        .as(Arrays.asList(MarianNmtConnector.replaceOkapiTags(targetTokensWithTags)) + "")
        .containsExactly(ISO, OPEN2, "Test", "ein", "ist", OPEN1, "das", CLOSE1, ".", CLOSE2, ISO);
  }


  /**
   * Test {@link MarianNmtConnector#detokenizeTags(String)}.
   */
  @Test
  void testDetokenizeTags() {

    // init variables to be re-used between tests
    String input = null;
    String detokenizedInput = null;

    // first test
    input = String.format("x y z %s a %s b %s c", ISO, OPEN1, CLOSE1);
    detokenizedInput = String.format("x y z %sa %sb%s c", ISO, OPEN1, CLOSE1);
    assertThat(MarianNmtConnector.detokenizeTags(input)).isEqualTo(detokenizedInput);

    // second test
    input = String.format("x y z %s   a   %s  b   %s  c", ISO, OPEN1, CLOSE1);
    detokenizedInput = String.format("x y z %sa   %sb%s  c", ISO, OPEN1, CLOSE1);
    assertThat(MarianNmtConnector.detokenizeTags(input)).isEqualTo(detokenizedInput);
  }


  /**
   * Test {@link MarianNmtConnector#moveTagsFromBetweenBpeFragments(String[])}.
   */
  @Test
  void testMoveTagsFromBetweenBpeFragments() {

    assertThat(MarianNmtConnector.moveTagsFromBetweenBpeFragments(
        new String[] { "a", "b", "c@@", OPEN1, "x", "y", "z" }))
            .containsExactly("a", "b", OPEN1, "c@@", "x", "y", "z");

    assertThat(MarianNmtConnector.moveTagsFromBetweenBpeFragments(
        new String[] { "a", "b@@", "c@@", OPEN1, "x", "y", "z" }))
            .containsExactly("a", OPEN1, "b@@", "c@@", "x", "y", "z");

    assertThat(MarianNmtConnector.moveTagsFromBetweenBpeFragments(
        new String[] { "a@@", "b@@", "c@@", OPEN1, "x", "y", "z" }))
            .containsExactly(OPEN1, "a@@", "b@@", "c@@", "x", "y", "z");

    assertThat(MarianNmtConnector.moveTagsFromBetweenBpeFragments(
        new String[] { "a@@", "b", "c@@", OPEN1, "x", "y", "z" }))
            .containsExactly("a@@", "b", OPEN1, "c@@", "x", "y", "z");

    assertThat(MarianNmtConnector.moveTagsFromBetweenBpeFragments(
        new String[] { "a", "b", "c@@", CLOSE1, "x", "y", "z" }))
            .containsExactly("a", "b", "c@@", "x", CLOSE1, "y", "z");

    assertThat(MarianNmtConnector.moveTagsFromBetweenBpeFragments(
        new String[] { "a", "b", "c@@", CLOSE1, "x", "y", "z" }))
            .containsExactly("a", "b", "c@@", "x", CLOSE1, "y", "z");

    assertThat(MarianNmtConnector.moveTagsFromBetweenBpeFragments(
        new String[] { "a", "b", "c@@", CLOSE1, "x@@", "y", "z" }))
            .containsExactly("a", "b", "c@@", "x@@", "y", CLOSE1, "z");

    assertThat(MarianNmtConnector.moveTagsFromBetweenBpeFragments(
        new String[] { "a", "b", "c@@", CLOSE1, "x@@", "y@@", "z" }))
            .containsExactly("a", "b", "c@@", "x@@", "y@@", "z", CLOSE1);

    assertThat(MarianNmtConnector.moveTagsFromBetweenBpeFragments(
        new String[] { "a", "b", "c@@", CLOSE1, "x", "y@@", "z" }))
            .containsExactly("a", "b", "c@@", "x", CLOSE1, "y@@", "z");

    assertThat(MarianNmtConnector.moveTagsFromBetweenBpeFragments(
        new String[] { "a", "b", "c@@", OPEN1, CLOSE1, "x", "y@@", "z" }))
            .containsExactly("a", "b", OPEN1, "c@@", "x", CLOSE1, "y@@", "z");

    assertThat(MarianNmtConnector.moveTagsFromBetweenBpeFragments(
        new String[] { "a", "b", "c@@", CLOSE1, OPEN1, "x", "y@@", "z" }))
            .containsExactly("a", "b", OPEN1, "c@@", "x", CLOSE1, "y@@", "z");
  }


  /**
   * Test {@link MarianNmtConnector#maskTags(String[])}.
   */
  @Test
  void testMaskUnmaskTags() {

    // init variables to be re-used between tests
    String unmasked = null;
    String masked = null;

    unmasked = "a b c " + OPEN1 + " x y z";
    masked = "a b c x" + OPEN1 + "c x y z";
    assertThat(MarianNmtConnector.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarianNmtConnector.unmaskTags(masked)).isEqualTo(unmasked);

    unmasked = OPEN1 + " x y z";
    masked = "x" + OPEN1 + " x y z";
    assertThat(MarianNmtConnector.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarianNmtConnector.unmaskTags(masked)).isEqualTo(unmasked);

    unmasked = "a b c " + OPEN1;
    masked = "a b c " + OPEN1 + "c";
    assertThat(MarianNmtConnector.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarianNmtConnector.unmaskTags(masked)).isEqualTo(unmasked);

    unmasked = "a b c " + ISO + " " + OPEN1 + " x y z";
    masked = "a b c x" + ISO + "c x" + OPEN1 + "c x y z";
    assertThat(MarianNmtConnector.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarianNmtConnector.unmaskTags(masked)).isEqualTo(unmasked);

    unmasked = ISO + " " + OPEN1 + " x y z";
    masked = "x" + ISO + " x" + OPEN1 + " x y z";
    assertThat(MarianNmtConnector.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarianNmtConnector.unmaskTags(masked)).isEqualTo(unmasked);

    unmasked = "a b c " + ISO + " " + OPEN1;
    masked = "a b c " + ISO + "c " + OPEN1 + "c";
    assertThat(MarianNmtConnector.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarianNmtConnector.unmaskTags(masked)).isEqualTo(unmasked);
  }


  /**
   * Test {@link MarianNmtConnector#balanceTags(String, String[])}.
   */
  @Test
  void testBalanceTags() {

    // init variables to be re-used between tests
    String[] sourceTokensWithTags = null;
    Map<Integer, Integer> closing2OpeningTag = null;
    String target = null;
    String[] targetTokens = null;

    // first test
    sourceTokensWithTags = String.format("%s %s This is %s %s a test . %s %s",
        ISO, OPEN1, CLOSE1, OPEN2, CLOSE2, ISO).split(" ");
    closing2OpeningTag = MarianNmtConnector.createTagIdMap(sourceTokensWithTags);
    target = String.format("%s Das %s %s ist %s ein Test . %s %s",
        ISO, CLOSE1, OPEN1, OPEN2, CLOSE2, ISO);
    targetTokens = target.split(" ");
    MarianNmtConnector.balanceTags(closing2OpeningTag, targetTokens);
    assertThat(targetTokens).containsExactly(
        ISO, OPEN1, "Das", "ist", CLOSE1, OPEN2, "ein", "Test", ".", CLOSE2, ISO);

    // second test
    sourceTokensWithTags = String.format("%s %s %s This is %s %s a test . %s",
        ISO, OPEN1, OPEN2, CLOSE2, CLOSE1, ISO).split(" ");
    closing2OpeningTag = MarianNmtConnector.createTagIdMap(sourceTokensWithTags);
    target = String.format("%s Das %s %s %s %s ist ein Test . %s",
        ISO, CLOSE2, CLOSE1, OPEN1, OPEN2, ISO);
    targetTokens = target.split(" ");
    MarianNmtConnector.balanceTags(closing2OpeningTag, targetTokens);
    assertThat(targetTokens).containsExactly(
        ISO, OPEN2, OPEN1, "Das", "ist", CLOSE1, CLOSE2, "ein", "Test", ".", ISO);

    // first test with bpe fragments
    sourceTokensWithTags = String.format("%s %s This is %s %s a test . %s %s",
        ISO, OPEN1, CLOSE1, OPEN2, CLOSE2, ISO).split(" ");
    closing2OpeningTag = MarianNmtConnector.createTagIdMap(sourceTokensWithTags);
    target = String.format("%s Da@@ s %s %s is@@ t %s ein Test . %s %s",
        ISO, CLOSE1, OPEN1, OPEN2, CLOSE2, ISO);
    targetTokens = target.split(" ");
    MarianNmtConnector.balanceTags(closing2OpeningTag, targetTokens);
    assertThat(targetTokens).containsExactly(
        ISO, OPEN1, "Da@@", "s", "is@@", "t", CLOSE1, OPEN2, "ein", "Test", ".", CLOSE2, ISO);

    // second test with bpe fragments
    sourceTokensWithTags = String.format("%s %s %s This is %s %s a test . %s",
        ISO, OPEN1, OPEN2, CLOSE2, CLOSE1, ISO).split(" ");
    closing2OpeningTag = MarianNmtConnector.createTagIdMap(sourceTokensWithTags);
    target = String.format("%s Da@@ s %s %s %s %s is@@ t ein Test . %s",
        ISO, CLOSE2, CLOSE1, OPEN1, OPEN2, ISO);
    targetTokens = target.split(" ");
    MarianNmtConnector.balanceTags(closing2OpeningTag, targetTokens);
    assertThat(targetTokens).containsExactly(
        ISO, OPEN2, OPEN1, "Da@@", "s", "is@@", "t", CLOSE1, CLOSE2, "ein", "Test", ".", ISO);
  }


  private static Map<Integer, List<String>> createSourceTokenIndex2tags(
      String[] sourceTokensWithTags)
      throws ReflectiveOperationException {

    // use reflection to make private method accessible
    String methodName = "createSourceTokenIndex2Tags";
    Method method = MarianNmtConnector.class.getDeclaredMethod(methodName, String[].class);
    method.setAccessible(true);

    @SuppressWarnings("unchecked")
    Map<Integer, List<String>> sourceTokenIndex2tags =
        (Map<Integer, List<String>>)method.invoke(null, new Object[] { sourceTokensWithTags });

    return sourceTokenIndex2tags;
  }
}
