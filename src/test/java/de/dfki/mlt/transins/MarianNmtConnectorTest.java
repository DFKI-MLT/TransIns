package de.dfki.mlt.transins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import net.sf.okapi.common.exceptions.OkapiException;

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
  private static final String OPEN3 = MarianNmtConnector.createOpeningTag(5);
  private static final String CLOSE3 = MarianNmtConnector.createClosingTag(6);

  // map of closing tags to opening tags
  private static Map<String, String> closing2OpeningTag = null;

  // builder to check for valid XML
  private static DocumentBuilder builder = null;


  @BeforeAll
  public static void init() {

    closing2OpeningTag = new HashMap<>();
    closing2OpeningTag.put(CLOSE1, OPEN1);
    closing2OpeningTag.put(CLOSE2, OPEN2);
    closing2OpeningTag.put(CLOSE3, OPEN3);

    try {
      builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    }
  }


  /**
   * Test {@link MarianNmtConnector#createTagMap(String)}.
   */
  @Test
  void testCreateTagMap() {

    // init variables to be re-used between tests
    String[] sourceTokensWithTags = null;
    Map<String, String> localClosing2OpeningTag = null;

    // first test
    sourceTokensWithTags = toArray("ISO OPEN1 This CLOSE1 is a OPEN2 test . CLOSE2 ISO");
    localClosing2OpeningTag = MarianNmtConnector.createTagMap(sourceTokensWithTags);
    assertThat(localClosing2OpeningTag).contains(entry(CLOSE1, OPEN1), entry(CLOSE2, OPEN2));

    // second test
    sourceTokensWithTags = toArray("ISO OPEN1 This OPEN2 is a CLOSE2 test . CLOSE1 ISO");
    localClosing2OpeningTag = MarianNmtConnector.createTagMap(sourceTokensWithTags);
    assertThat(localClosing2OpeningTag).contains(entry(CLOSE1, OPEN1), entry(CLOSE2, OPEN2));
  }


  /**
   * Test {@link MarianNmtConnector#createSourceTokenIndex2Tags(String[], int)}.
   */
  @Test
  void testCreateSourceTokenIndex2Tags() {

    String[] sourceTokens = toArray("ISO OPEN1 This CLOSE1 is a OPEN2 test . CLOSE2 ISO");

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
    String[] sourceTokens = null;
    List<Integer> pointedSourceTokens = null;
    Map<Integer, List<String>> sourceTokenIndex2tags = null;

    // first test
    sourceTokens = toArray("ISO OPEN1 OPEN2 This CLOSE2 is a CLOSE1 test . ISO");
    pointedSourceTokens = Arrays.asList(new Integer[] { 1, 2 });
    sourceTokenIndex2tags = createSourceTokenIndex2tags(sourceTokens);

    MarianNmtConnector.moveSourceTagsToPointedTokens(
        sourceTokenIndex2tags, closing2OpeningTag, pointedSourceTokens,
        MarianNmtConnector.removeTags(sourceTokens).length);

    assertThat(sourceTokenIndex2tags).hasSize(4);
    assertThat(sourceTokenIndex2tags.get(0)).containsExactly(ISO);
    assertThat(sourceTokenIndex2tags.get(1)).containsExactly(OPEN1);
    assertThat(sourceTokenIndex2tags.get(2)).containsExactly(CLOSE1);
    assertThat(sourceTokenIndex2tags.get(5)).containsExactly(OPEN2, ISO, CLOSE2);

    // second test
    sourceTokens = toArray("ISO OPEN1 OPEN2 This CLOSE2 is a CLOSE1 test . ISO");
    pointedSourceTokens = Arrays.asList(new Integer[] { 0, 1, 2 });
    sourceTokenIndex2tags = createSourceTokenIndex2tags(sourceTokens);

    MarianNmtConnector.moveSourceTagsToPointedTokens(
        sourceTokenIndex2tags, closing2OpeningTag, pointedSourceTokens,
        MarianNmtConnector.removeTags(sourceTokens).length);

    assertThat(sourceTokenIndex2tags).hasSize(3);
    assertThat(sourceTokenIndex2tags.get(0)).containsExactly(ISO, OPEN1, OPEN2, CLOSE2);
    assertThat(sourceTokenIndex2tags.get(2)).containsExactly(CLOSE1);
    assertThat(sourceTokenIndex2tags.get(5)).containsExactly(ISO);

    // third test
    sourceTokens = toArray("ISO OPEN1 OPEN2 x CLOSE2 y z a CLOSE1 b c");
    pointedSourceTokens = Arrays.asList(new Integer[] { 1, 2 });
    sourceTokenIndex2tags = createSourceTokenIndex2tags(sourceTokens);

    MarianNmtConnector.moveSourceTagsToPointedTokens(
        sourceTokenIndex2tags, closing2OpeningTag, pointedSourceTokens,
        MarianNmtConnector.removeTags(sourceTokens).length);

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

    String[] sourceTokens = toArray("ISO OPEN1 Th@@ i@@ s CLOSE1 is a OPEN2 te@@ st . CLOSE2 ISO");
    String[] sourceTokensWithoutTags = MarianNmtConnector.removeTags(sourceTokens);
    Map<Integer, List<String>> sourceTokenIndex2tags = createSourceTokenIndex2tags(sourceTokens);

    List<String> tags = null;

    // non-bpe token 'is'
    tags = (List<String>)method.invoke(
        null, 3,
        new HashMap<>(sourceTokenIndex2tags),
        sourceTokensWithoutTags);
    assertThat(tags).isEmpty();

    // last bpe fragment 's'
    tags = (List<String>)method.invoke(
        null, 2,
        new HashMap<>(sourceTokenIndex2tags),
        sourceTokensWithoutTags);
    assertThat(tags).containsExactly(ISO, OPEN1, CLOSE1);

    // middle bpe fragment 'i@@'
    tags = (List<String>)method.invoke(
        null, 1,
        new HashMap<>(sourceTokenIndex2tags),
        sourceTokensWithoutTags);
    assertThat(tags).containsExactly(ISO, OPEN1, CLOSE1);

    // first bpe fragment 'Th@@'
    tags = (List<String>)method.invoke(
        null, 0,
        new HashMap<>(sourceTokenIndex2tags),
        sourceTokensWithoutTags);
    assertThat(tags).containsExactly(ISO, OPEN1, CLOSE1);

    // EOS
    tags = (List<String>)method.invoke(
        null, 8,
        new HashMap<>(sourceTokenIndex2tags),
        sourceTokensWithoutTags);
    assertThat(tags).containsExactly(ISO);
  }


  /**
   * Test
   * {@link MarianNmtConnector#reinsertTags(String[], String[], String[], Alignments)}
   * with soft alignments.
   */
  @Test
  void testReinsertTagsWithSoftAlignments() {

    String[] sourceTokens = toArray("ISO OPEN1 This CLOSE1 is a OPEN2 test . CLOSE2 ISO");

    // init variables to be re-used between tests
    String[] targetTokensWithoutTags = null;
    String rawAlignments = null;
    String[] expectedResult = null;

    // first test
    targetTokensWithoutTags = "Das ist ein Test .".split(" ");
    rawAlignments = ""
        + "1,0,0,0,0,0 " // Das -> This
        + "0,1,0,0,0,0 " // ist -> is
        + "0,0,1,0,0,0 " // ein -> a
        + "0,0,0,1,0,0 " // Test -> test
        + "0,0,0,0,1,0 " // . -> .
        + "0,0,0,0,0,1"; // EOS -> EOS
    expectedResult = toArray("ISO OPEN1 Das CLOSE1 ist ein OPEN2 Test . CLOSE2 ISO");
    testReinsertTagsWithSoftAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // second test
    targetTokensWithoutTags = "Test ein ist das .".split(" ");
    rawAlignments = ""
        + "0,0,0,1,0,0 " // Test -> test
        + "0,0,1,0,0,0 " // ein -> a
        + "0,1,0,0,0,0 " // ist -> is
        + "1,0,0,0,0,0 " // das -> This
        + "0,0,0,0,1,0 " // . -> .
        + "0,0,0,0,0,1"; // EOS -> EOS
    expectedResult = toArray("ISO OPEN2 Test ein ist OPEN1 das CLOSE1 . CLOSE2 ISO");
    testReinsertTagsWithSoftAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);
  }


  /**
   * Test
   * {@link MarianNmtConnector#reinsertTags(String[], String[], String[], Alignments)}
   * with hard alignments.
   */
  @Test
  void testReinsertTagsWithHardAlignments() {

    String[] sourceTokens = toArray("ISO OPEN1 This CLOSE1 is a OPEN2 test . CLOSE2 ISO");

    // init variables to be re-used between tests
    String[] targetTokensWithoutTags = null;
    String rawAlignments = null;
    String[] expectedResult = null;

    // first test
    targetTokensWithoutTags = toArray("Das ist ein Test .");
    rawAlignments = "0-0 1-1 2-2 3-3 4-4 5-5";
    expectedResult = toArray("ISO OPEN1 Das CLOSE1 ist ein OPEN2 Test . CLOSE2 ISO");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // second test
    targetTokensWithoutTags = toArray("Test ein ist das .");
    //                                 This is  a   Test .
    rawAlignments = "0-3 1-2 2-1 3-0 4-4 5-5";
    expectedResult = toArray("ISO OPEN2 Test ein ist OPEN1 das CLOSE1 . CLOSE2 ISO");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);
  }


  /**
   * Test
   * {@link MarianNmtConnector#reinsertTags(String[], String[], String[], Alignments)}
   * with more complex examples.
   */
  @Test
  void testReinsertTagsComplex() {

    String[] sourceTokens = toArray("OPEN1 x y z CLOSE1 a b c");

    // init variables to be re-used between tests
    String[] targetTokensWithoutTags = null;
    String rawAlignments = null;
    String[] expectedResult = null;

    // first test
    targetTokensWithoutTags = toArray("X1 N Z X2 N N");
    rawAlignments = "0-0 0-3 2-2";
    expectedResult = toArray("OPEN1 X1 N Z CLOSE1 OPEN1 X2 N N");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // second test
    targetTokensWithoutTags = toArray("Z1 Z2 X N N N");
    rawAlignments = "0-2 2-0 2-1";
    expectedResult = toArray("Z1 CLOSE1 Z2 CLOSE1 OPEN1 X N N N");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // third test
    targetTokensWithoutTags = toArray("Z1 N X1 Z2 N X2");
    rawAlignments = "0-2 0-5 2-0 2-3";
    expectedResult = toArray("Z1 CLOSE1 N OPEN1 X1 Z2 CLOSE1 N OPEN1 X2");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);
  }


  private void testReinsertTagsWithSoftAlignments(
      String[] sourceTokens, String[] targetTokensWithoutTags, String rawAlignments,
      String[] expectedResult) {

    testReinsertTags(sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult, false);
  }


  private void testReinsertTagsWithHardAlignments(
      String[] sourceTokens, String[] targetTokensWithoutTags, String rawAlignments,
      String[] expectedResult) {

    testReinsertTags(sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult, true);
  }


  private void testReinsertTags(
      String[] sourceTokens, String[] targetTokensWithoutTags, String rawAlignments,
      String[] expectedResult, boolean hardAlignments) {

    String[] sourceTokensWithoutTags = MarianNmtConnector.removeTags(sourceTokens);
    Alignments algn = null;
    if (hardAlignments) {
      algn = new HardAlignments(rawAlignments);
    } else {
      algn = new SoftAlignments(rawAlignments);
    }
    Map<Integer, List<String>> sourceTokenIndex2tags =
        MarianNmtConnector.createSourceTokenIndex2Tags(sourceTokens);

    String[] targetTokensWithTags =
        MarianNmtConnector.reinsertTags(
            sourceTokensWithoutTags, targetTokensWithoutTags, algn, sourceTokenIndex2tags);
    assertThat(targetTokensWithTags)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            toString(expectedResult), toString(targetTokensWithTags)))
        .containsExactly(expectedResult);
  }


  /**
   * Test {@link MarianNmtConnector#undoBytePairEncoding(String[])}.
   */
  @Test
  void testUndoBytePairEncoding() {

    // init variables to be re-used between tests
    String[] targetTokens = null;
    String[] expectedResult = null;

    // simple case
    targetTokens = toArray("a b@@ c@@ d x");
    expectedResult = toArray("a bcd x");
    testUndoBytePairEncoding(targetTokens, expectedResult);

    // at sentence beginning
    targetTokens = toArray("b@@ c@@ d x");
    expectedResult = toArray("bcd x");
    testUndoBytePairEncoding(targetTokens, expectedResult);

    // at sentence end
    targetTokens = toArray("a b@@ c@@ d");
    expectedResult = toArray("a bcd");
    testUndoBytePairEncoding(targetTokens, expectedResult);
  }


  private void testUndoBytePairEncoding(String[] targetTokens, String[] expectedResult) {

    targetTokens = MarianNmtConnector.undoBytePairEncoding(targetTokens);
    assertThat(targetTokens)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            toString(expectedResult), toString(targetTokens)))
        .containsExactly(expectedResult);
  }


  /**
   * Test {@link MarianNmtConnector#handleInvertedTags(Map, String[])}.
   */
  @Test
  void testHandleInvertedTags() {

    // init variables to be re-used between tests
    String[] targetTokens = null;
    String[] expectedResult = null;

    // single closing tag
    targetTokens = toArray("x CLOSE1 y");
    expectedResult = toArray("x y");
    testHandleInvertedTags(targetTokens, expectedResult);

    // multiple closing tags
    targetTokens = toArray("x CLOSE1 y CLOSE1 z");
    expectedResult = toArray("x y z ");
    testHandleInvertedTags(targetTokens, expectedResult);

    // multiple closing tags
    targetTokens = toArray("x CLOSE1 y CLOSE2 z");
    expectedResult = toArray("x y z");
    testHandleInvertedTags(targetTokens, expectedResult);

    // single closing tag at beginning
    targetTokens = toArray("CLOSE1 x y");
    expectedResult = toArray("x y");
    testHandleInvertedTags(targetTokens, expectedResult);

    // multiple closing tags at beginning
    targetTokens = toArray("CLOSE1 CLOSE1 x y");
    expectedResult = toArray("x y");
    testHandleInvertedTags(targetTokens, expectedResult);

    // single closing tag at end
    targetTokens = toArray("x y CLOSE1");
    expectedResult = toArray("x y");
    testHandleInvertedTags(targetTokens, expectedResult);

    // multiple closing tags at end
    targetTokens = toArray("x y CLOSE1 CLOSE1");
    expectedResult = toArray("x y");
    testHandleInvertedTags(targetTokens, expectedResult);

    // closing tag followed by opening tag
    targetTokens = toArray("x CLOSE1 y OPEN1 z");
    expectedResult = toArray("OPEN1 x y z CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // closing tag at beginning followed by opening tag
    targetTokens = toArray("CLOSE1 x y OPEN1 z");
    expectedResult = toArray("OPEN1 x y z CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // closing tag followed by opening tag at end
    targetTokens = toArray("x CLOSE1 y z OPEN1");
    expectedResult = toArray("OPEN1 x y z CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // closing tag at beginning followed by opening tag at end
    targetTokens = toArray("CLOSE1 x y z OPEN1");
    expectedResult = toArray("OPEN1 x y z CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // two inverted tags with gap
    targetTokens = toArray("x CLOSE1 y OPEN1 z a CLOSE1 b OPEN1 c");
    expectedResult = toArray("OPEN1 x y z CLOSE1 OPEN1 a b c CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // two inverted tags without gap
    targetTokens = toArray("x CLOSE1 y OPEN1 z CLOSE1 a OPEN1 b c");
    expectedResult = toArray("OPEN1 x y OPEN1 z CLOSE1 a b CLOSE1 c");
    testHandleInvertedTags(targetTokens, expectedResult);

    // two nested inverted tags with gap
    targetTokens = toArray("x CLOSE1 y CLOSE1 z a OPEN1 b OPEN1 c");
    expectedResult = toArray("OPEN1 x y CLOSE1 z a b CLOSE1 OPEN1 c");
    testHandleInvertedTags(targetTokens, expectedResult);

    // two nested inverted tags without gap
    targetTokens = toArray("x CLOSE1 y CLOSE1 z OPEN1 a OPEN1 b c");
    expectedResult = toArray("OPEN1 x y CLOSE1 z a CLOSE1 OPEN1 b c");
    testHandleInvertedTags(targetTokens, expectedResult);

    // two nested inverted tags with gap, mixed
    targetTokens = toArray("x CLOSE1 y CLOSE2 z a OPEN2 b OPEN1 c");
    expectedResult = toArray("OPEN1 x OPEN2 y z a b CLOSE2 c CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // two nested inverted tags with gap, mixed, overlapping
    targetTokens = toArray("x CLOSE1 y CLOSE2 z a OPEN1 b OPEN2 c");
    expectedResult = toArray("OPEN1 x OPEN2 y z a b CLOSE1 c CLOSE2");
    testHandleInvertedTags(targetTokens, expectedResult);

    // inverted tags followed by non-inverted tags with gap
    targetTokens = toArray("x CLOSE1 y OPEN1 z a OPEN1 b CLOSE1 c");
    expectedResult = toArray("OPEN1 x y z CLOSE1 a OPEN1 b CLOSE1 c");
    testHandleInvertedTags(targetTokens, expectedResult);

    // non-inverted tags followed by inverted tags with gap
    targetTokens = toArray("x OPEN1 y CLOSE1 z a CLOSE1 b OPEN1 c");
    expectedResult = toArray("x OPEN1 y CLOSE1 z OPEN1 a b c CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // inverted tags followed by non-inverted tags without gap
    targetTokens = toArray("x CLOSE1 y OPEN1 z OPEN1 a CLOSE1 b");
    expectedResult = toArray("OPEN1 x y z CLOSE1 OPEN1 a CLOSE1 b");
    testHandleInvertedTags(targetTokens, expectedResult);

    // non-inverted tags followed by inverted tags without gap
    targetTokens = toArray("x OPEN1 y CLOSE1 z CLOSE1 a OPEN1 b");
    expectedResult = toArray("x OPEN1 y CLOSE1 OPEN1 z a b CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // mixed with isolated tags
    targetTokens = toArray("ISO Das CLOSE1 OPEN1 ist OPEN2 ein Test . CLOSE2 ISO");
    expectedResult = toArray("ISO OPEN1 Das ist CLOSE1 OPEN2 ein Test . CLOSE2 ISO");
    testHandleInvertedTags(targetTokens, expectedResult);

    // mixed with isolated tags, nested
    targetTokens = toArray("ISO Das CLOSE2 CLOSE1 OPEN1 OPEN2 ist ein Test . ISO");
    expectedResult = toArray("ISO OPEN1 OPEN2 Das ist CLOSE2 CLOSE1 ein Test . ISO");
    testHandleInvertedTags(targetTokens, expectedResult);
  }


  private void testHandleInvertedTags(String[] targetTokens, String[] expectedResult) {

    targetTokens = MarianNmtConnector.handleInvertedTags(closing2OpeningTag, targetTokens);
    assertThat(targetTokens)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            toString(expectedResult), toString(targetTokens)))
        .containsExactly(expectedResult);
  }


  /**
   * Test {@link MarianNmtConnector#removeRedundantTags(Map, String[])}.
   */
  @Test
  void testRemoveRedundantTags() {

    // init variables to be re-used between tests
    String[] targetTokens = null;
    String[] expectedResult = null;

    // single opening tag
    targetTokens = toArray("x OPEN1 y");
    expectedResult = toArray("x y");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // multiple opening tags
    targetTokens = toArray("x OPEN1 y OPEN1");
    expectedResult = toArray("x y");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // multiple opening tags and multiple closing tags
    targetTokens = toArray("x OPEN1 y OPEN1 z CLOSE1 a b CLOSE1 c");
    expectedResult = toArray("x OPEN1 y z a b CLOSE1 c");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // multiple opening tags and single closing tag
    targetTokens = toArray("x OPEN1 y OPEN1 z CLOSE1 a b c");
    expectedResult = toArray("x OPEN1 y z CLOSE1 a b c");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // single opening tag and two closing tags
    targetTokens = toArray("x OPEN1 y z CLOSE1 a b CLOSE1 c");
    expectedResult = toArray("x OPEN1 y z a b CLOSE1 c");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // single opening tag and three closing tags
    targetTokens = toArray("x OPEN1 y z CLOSE1 a b CLOSE1 c CLOSE1 d");
    expectedResult = toArray("x OPEN1 y z a b c CLOSE1 d");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // multiple opening tags and multiple closing tags followed by tag pair
    targetTokens = toArray("x OPEN1 y OPEN1 z CLOSE1 a b CLOSE1 c OPEN1 i j CLOSE1 k");
    expectedResult = toArray("x OPEN1 y z a b CLOSE1 c OPEN1 i j CLOSE1 k");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // mixed tag pairs
    targetTokens = toArray("x OPEN1 y OPEN1 z CLOSE1 a b CLOSE1 c OPEN2 i OPEN2 j CLOSE2 k");
    expectedResult = toArray("x OPEN1 y z a b CLOSE1 c OPEN2 i j CLOSE2 k");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // mixed tag pairs, nested
    targetTokens = toArray("x OPEN1 y OPEN1 z OPEN2 a b OPEN2 c CLOSE2 i CLOSE1 j CLOSE1 k");
    expectedResult = toArray("x OPEN1 y z OPEN2 a b c CLOSE2 i j CLOSE1 k");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // mixed tag pairs, overlapping
    targetTokens = toArray("x OPEN1 y OPEN1 z OPEN2 a b OPEN2 c CLOSE1 i CLOSE1 j CLOSE2 k");
    expectedResult = toArray("x OPEN1 y z OPEN2 a b c i CLOSE1 j CLOSE2 k");
    testRemoveRedundantTags(targetTokens, expectedResult);
  }


  private void testRemoveRedundantTags(String[] targetTokens, String[] expectedResult) {

    targetTokens = MarianNmtConnector.removeRedundantTags(closing2OpeningTag, targetTokens);
    assertThat(targetTokens)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            toString(expectedResult), toString(targetTokens)))
        .containsExactly(expectedResult);
  }


  /**
   * Test {@link MarianNmtConnector#sortOpeningTags(int, int, String[], Map)}.
   * This method is private, access for unit test achieved via reflection.
   */
  @Test
  void testSortOpeningTags()
      throws ReflectiveOperationException {

    // use reflection to make private method accessible
    String methodName = "sortOpeningTags";
    Method method = MarianNmtConnector.class.getDeclaredMethod(
        methodName, int.class, int.class, String[].class, Map.class);
    method.setAccessible(true);

    // init variables to be re-used between tests
    String[] targetTokens = null;
    String[] expectedResult = null;

    // closing tags in same order
    targetTokens = toArray("x OPEN1 OPEN2 OPEN3 y CLOSE1 z CLOSE2 a CLOSE3");
    expectedResult = toArray("x OPEN3 OPEN2 OPEN1 y CLOSE1 z CLOSE2 a CLOSE3");
    testSortTags(targetTokens, 1, 4, expectedResult, method);

    // closing tags in inverse order
    targetTokens = toArray("x OPEN1 OPEN2 OPEN3 y CLOSE3 z CLOSE2 a CLOSE1");
    expectedResult = toArray("x OPEN1 OPEN2 OPEN3 y CLOSE3 z CLOSE2 a CLOSE1");
    testSortTags(targetTokens, 1, 4, expectedResult, method);

    // non-tag in range
    assertThatExceptionOfType(InvocationTargetException.class).isThrownBy(
        () -> {
          method.invoke(null, 0, 4,
              toArray("x OPEN1 OPEN2 OPEN3 y CLOSE3 z CLOSE2 a CLOSE1"), closing2OpeningTag);
        }).withCauseInstanceOf(OkapiException.class);

    // not enough closing tags
    assertThatExceptionOfType(InvocationTargetException.class).isThrownBy(
        () -> {
          method.invoke(null, 1, 4,
              toArray("x OPEN1 OPEN2 OPEN3 y CLOSE3 z CLOSE2 a"), closing2OpeningTag);
        }).withCauseInstanceOf(OkapiException.class);
  }


  /**
   * Test {@link MarianNmtConnector#sortClosingTags(int, int, String[], Map)}.
   * This method is private, access for unit test achieved via reflection.
   */
  @Test
  void testSortClosingTags()
      throws ReflectiveOperationException {

    // use reflection to make private method accessible
    String methodName = "sortClosingTags";
    Method method = MarianNmtConnector.class.getDeclaredMethod(
        methodName, int.class, int.class, String[].class, Map.class);
    method.setAccessible(true);

    // init variables to be re-used between tests
    String[] targetTokens = null;
    String[] expectedResult = null;

    // closing tags in same order
    targetTokens = toArray("x OPEN1 y OPEN2 z OPEN3 a CLOSE1 CLOSE2 CLOSE3 b");
    expectedResult = toArray("x OPEN1 y OPEN2 z OPEN3 a CLOSE3 CLOSE2 CLOSE1 b");
    testSortTags(targetTokens, 7, 10, expectedResult, method);

    // closing tags in inverse order
    targetTokens = toArray("x OPEN1 y OPEN2 z OPEN3 a CLOSE3 CLOSE2 CLOSE1 b");
    expectedResult = toArray("x OPEN1 y OPEN2 z OPEN3 a CLOSE3 CLOSE2 CLOSE1 b");
    testSortTags(targetTokens, 7, 10, expectedResult, method);

    // closing tags mixed
    targetTokens = toArray("OPEN3 x OPEN1 y OPEN2 z CLOSE1 CLOSE3 a CLOSE2 b c");
    expectedResult = toArray("OPEN3 x OPEN1 y OPEN2 z CLOSE1 CLOSE3 a CLOSE2 b c");
    testSortTags(targetTokens, 6, 8, expectedResult, method);

    // non-tag in range
    assertThatExceptionOfType(InvocationTargetException.class).isThrownBy(
        () -> {
          method.invoke(null, 6, 10,
              toArray("x OPEN1 y OPEN2 z OPEN3 a CLOSE1 CLOSE2 CLOSE3 b"), closing2OpeningTag);
        }).withCauseInstanceOf(OkapiException.class);

    // not enough opening tags
    assertThatExceptionOfType(InvocationTargetException.class).isThrownBy(
        () -> {
          method.invoke(null, 6, 9,
              toArray("x y OPEN2 z OPEN3 a CLOSE1 CLOSE2 CLOSE3 b"), closing2OpeningTag);
        }).withCauseInstanceOf(OkapiException.class);
  }


  private void testSortTags(
      String[] targetTokens, int startIndex, int endIndex, String[] expectedResult, Method method)
      throws ReflectiveOperationException {

    targetTokens =
        (String[])method.invoke(null, startIndex, endIndex, targetTokens, closing2OpeningTag);
    assertThat(targetTokens)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            toString(expectedResult), toString(targetTokens)))
        .containsExactly(expectedResult);
  }


  /**
   * Test {@link MarianNmtConnector#balanceTags(Map, String[])}.
   */
  @Test
  void testBalanceTags() {

    // init variables to be re-used between tests
    String[] targetTokens = null;
    String[] expectedResult = null;

    // opening tag sequence
    targetTokens = toArray("x OPEN1 OPEN2 y z CLOSE1 a CLOSE2");
    expectedResult = toArray("x OPEN2 OPEN1 y z CLOSE1 a CLOSE2");
    testBalanceTags(targetTokens, expectedResult);

    // opening tag
    targetTokens = toArray("x OPEN1 y z CLOSE1 a");
    expectedResult = toArray("x OPEN1 y z CLOSE1 a");
    testBalanceTags(targetTokens, expectedResult);

    // opening tag sequence at beginning
    targetTokens = toArray("OPEN1 OPEN2 x y CLOSE1 a CLOSE2");
    expectedResult = toArray("OPEN2 OPEN1 x y CLOSE1 a CLOSE2");
    testBalanceTags(targetTokens, expectedResult);

    // opening tag at beginning
    targetTokens = toArray("OPEN1 x y CLOSE1 a");
    expectedResult = toArray("OPEN1 x y CLOSE1 a");
    testBalanceTags(targetTokens, expectedResult);

    // closing tag sequence
    targetTokens = toArray("x OPEN1 y OPEN2 z a CLOSE1 CLOSE2 b");
    expectedResult = toArray("x OPEN1 y OPEN2 z a CLOSE2 CLOSE1 b");
    testBalanceTags(targetTokens, expectedResult);

    // closing tag
    targetTokens = toArray("x OPEN1 y z a CLOSE1 b");
    expectedResult = toArray("x OPEN1 y z a CLOSE1 b");
    testBalanceTags(targetTokens, expectedResult);

    // closing tag sequence at end
    targetTokens = toArray("x OPEN1 y OPEN2 z a CLOSE1 CLOSE2");
    expectedResult = toArray("x OPEN1 y OPEN2 z a CLOSE2 CLOSE1");
    testBalanceTags(targetTokens, expectedResult);

    // closing tag at end
    targetTokens = toArray("x OPEN1 y z a CLOSE1");
    expectedResult = toArray("x OPEN1 y z a CLOSE1");
    testBalanceTags(targetTokens, expectedResult);

    // single overlapping range
    targetTokens = toArray("x OPEN1 y OPEN2 z CLOSE1 a CLOSE2");
    expectedResult = toArray("x OPEN1 y OPEN2 z CLOSE2 CLOSE1 OPEN2 a CLOSE2");
    testBalanceTags(targetTokens, expectedResult);

    // double overlapping range
    targetTokens = toArray("x OPEN1 y OPEN2 z OPEN3 a CLOSE1 b CLOSE2 c CLOSE3");
    expectedResult = toArray(
        "x OPEN1 y OPEN2 z OPEN3 a CLOSE3 CLOSE2 CLOSE1 OPEN2 OPEN3 b "
            + "CLOSE3 CLOSE2 OPEN3 c CLOSE3");
    testBalanceTags(targetTokens, expectedResult);
  }


  private void testBalanceTags(String[] targetTokens, String[] expectedResult) {

    targetTokens = MarianNmtConnector.balanceTags(closing2OpeningTag, targetTokens);
    assertThat(targetTokens)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            toString(expectedResult), toString(targetTokens)))
        .containsExactly(expectedResult);
    String xml = MarianNmtConnector.toXml(targetTokens, closing2OpeningTag);
    assertThat(isValidXml(xml));
  }


  private static boolean isValidXml(String input) {

    try {
      builder.parse(new InputSource(new StringReader(input)));
    } catch (SAXException | IOException e) {
      return false;
    }

    return true;
  }


  /**
   * Test {@link MarianNmtConnector#mergeNeighborTagPairs(Map, String[])}.
   */
  @Test
  void testMergeNeighborTagPairs() {

    // init variables to be re-used between tests
    String[] targetTokens = null;
    String[] expectedResult = null;

    // one merge
    targetTokens = toArray("x OPEN1 y CLOSE1 OPEN1 z CLOSE1 a b c");
    expectedResult = toArray("x OPEN1 y z CLOSE1 a b c");
    testMergeNeighborTagPairs(targetTokens, expectedResult);

    // one merge with ending tag at end
    targetTokens = toArray("x OPEN1 y CLOSE1 OPEN1 z CLOSE1");
    expectedResult = toArray("x OPEN1 y z CLOSE1");
    testMergeNeighborTagPairs(targetTokens, expectedResult);

    // two merges
    targetTokens = toArray("x OPEN1 y CLOSE1 OPEN1 z CLOSE1 OPEN1 a b CLOSE1 c");
    expectedResult = toArray("x OPEN1 y z a b CLOSE1 c");
    testMergeNeighborTagPairs(targetTokens, expectedResult);

    // mixed tags pairs
    targetTokens = toArray("x OPEN1 y CLOSE1 OPEN1 z CLOSE1 OPEN2 a CLOSE2 OPEN2 b CLOSE2 c");
    expectedResult = toArray("x OPEN1 y z CLOSE1 OPEN2 a b CLOSE2 c");
    testMergeNeighborTagPairs(targetTokens, expectedResult);
  }


  void testMergeNeighborTagPairs(String[] targetTokens, String[] expectedResult) {

    targetTokens = MarianNmtConnector.mergeNeighborTagPairs(closing2OpeningTag, targetTokens);
    assertThat(targetTokens)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            toString(expectedResult), toString(targetTokens)))
        .containsExactly(expectedResult);
  }


  /**
   * Test {@link MarianNmtConnector#moveTagsFromBetweenBpeFragments(String[])}.
   */
  @Test
  void testMoveTagsFromBetweenBpeFragments() {

    // init variables to be re-used between tests
    String[] targetTokens;
    String[] expectedResult = null;

    // two fragments with opening tag
    targetTokens = toArray("a b c@@ OPEN1 x y z");
    expectedResult = toArray("a b OPEN1 c@@ x y z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // three fragments with opening tag
    targetTokens = toArray("a b@@ c@@ OPEN1 x y z");
    expectedResult = toArray("a OPEN1 b@@ c@@ x y z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // four fragments with opening tag
    targetTokens = toArray("a@@ b@@ c@@ OPEN1 x y z");
    expectedResult = toArray("OPEN1 a@@ b@@ c@@ x y z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // two fragments followed by two fragments with opening tag
    targetTokens = toArray("a@@ b c@@ OPEN1 x y z");
    expectedResult = toArray("a@@ b OPEN1 c@@ x y z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // two fragments with closing tag
    targetTokens = toArray("a b c@@ CLOSE1 x y z");
    expectedResult = toArray("a b c@@ x CLOSE1 y z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // three fragments with closing tag
    targetTokens = toArray("a b c@@ CLOSE1 x@@ y z");
    expectedResult = toArray("a b c@@ x@@ y CLOSE1 z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // four fragments with closing tag
    targetTokens = toArray("a b c@@ CLOSE1 x@@ y@@ z");
    expectedResult = toArray("a b c@@ x@@ y@@ z CLOSE1");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // two fragments with closing tag followed by two fragments
    targetTokens = toArray("a b c@@ CLOSE1 x y@@ z");
    expectedResult = toArray("a b c@@ x CLOSE1 y@@ z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // opening and closing tags between fragments
    targetTokens = toArray("a b c@@ OPEN1 CLOSE1 x y@@ z");
    expectedResult = toArray("a b OPEN1 c@@ x CLOSE1 y@@ z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // closing and opening tags between fragments
    targetTokens = toArray("a b c@@ CLOSE1 OPEN1 x y@@ z");
    expectedResult = toArray("a b OPEN1 c@@ x CLOSE1 y@@ z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);
  }


  private void testMoveTagsFromBetweenBpeFragments(
      String[] targetTokens, String[] expectedResult) {

    targetTokens = MarianNmtConnector.moveTagsFromBetweenBpeFragments(targetTokens);
    assertThat(targetTokens)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            toString(expectedResult), toString(targetTokens)))
        .containsExactly(expectedResult);
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
    unmasked = "a b c " + ISO + " " + OPEN1 + " x y z";
    masked = "a b c x" + ISO + "c x" + OPEN1 + "c x y z";
    assertThat(MarianNmtConnector.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarianNmtConnector.unmaskTags(masked)).isEqualTo(unmasked);

    // two tags at beginning
    unmasked = ISO + " " + OPEN1 + " x y z";
    masked = "x" + ISO + " x" + OPEN1 + " x y z";
    assertThat(MarianNmtConnector.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarianNmtConnector.unmaskTags(masked)).isEqualTo(unmasked);

    // two tags at end
    unmasked = "a b c " + ISO + " " + OPEN1;
    masked = "a b c " + ISO + "c " + OPEN1 + "c";
    assertThat(MarianNmtConnector.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarianNmtConnector.unmaskTags(masked)).isEqualTo(unmasked);
  }


  /**
   * Test {@link MarianNmtConnector#detokenizeTags(String)}.
   */
  @Test
  void testDetokenizeTags() {

    // init variables to be re-used between tests
    String input = null;
    String expectedResult = null;

    // standard
    input = String.format("x y z %s a %s b %s c", ISO, OPEN1, CLOSE1);
    expectedResult = String.format("x y z %sa %sb%s c", ISO, OPEN1, CLOSE1);
    testDetokenizeTags(input, expectedResult);

    // multiple whitespaces
    input = String.format("x y z %s   a   %s  b   %s  c", ISO, OPEN1, CLOSE1);
    expectedResult = String.format("x y z %sa   %sb%s  c", ISO, OPEN1, CLOSE1);
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


  /**
   * Utility method to split string into tokens and replace readable tags with Okapi tags.
   *
   * @param input
   *          the string to split
   * @return tokens with Okapi tags
   */
  private static String[] toArray(String input) {

    String[] tokens = input.split(" ");
    for (int i = 0; i < tokens.length; i++) {
      String oneToken = tokens[i];
      switch (oneToken) {
        case "ISO":
          oneToken = ISO;
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
  private static String toString(String[] tokens) {

    StringBuilder result = new StringBuilder();
    for (String oneToken : tokens) {
      if (oneToken.equals(ISO)) {
        result.append("ISO ");
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

    return result.toString().trim();
  }


  /**
   * @param args
   *          the arguments; not used here
   */
  public static void main(String[] args) {

    init();
    testTagCleanup();
  }


  /**
   * Stress test of tag cleanup.
   */
  //@Test
  static void testTagCleanup() {

    List<String> baseTokens = Arrays.asList(new String[] {
        "x", "y", "z", "a", "b", "c"
    });
    List<String> tags = Arrays.asList(new String[] {
        ISO, OPEN1, CLOSE1, OPEN2, CLOSE2, OPEN3, CLOSE3
    });

    // randomly insert tags in tokens
    Random random = new Random(System.currentTimeMillis());

    for (int run = 1; run <= 1000; run++) {
      for (int numberOfTags = 1; numberOfTags <= 20; numberOfTags++) {
        List<String> tokens = new ArrayList<>(baseTokens);
        List<String> tagsToInsert = new ArrayList<>();
        for (int i = 0; i < numberOfTags; i++) {
          tagsToInsert.add(tags.get(random.nextInt(tags.size())));
        }
        for (String oneTag : tagsToInsert) {
          tokens.add(random.nextInt(tokens.size() + 1), oneTag);
        }
        String[] targetTokensWithTags = tokens.toArray(new String[tokens.size()]);
        System.out.println(String.format("tags: %d run: %d", numberOfTags, run));
        System.out.println("input:               " + toString(targetTokensWithTags));
        targetTokensWithTags =
            MarianNmtConnector.handleInvertedTags(closing2OpeningTag, targetTokensWithTags);
        System.out.println("handleInvertedTags:  " + toString(targetTokensWithTags));
        targetTokensWithTags =
            MarianNmtConnector.removeRedundantTags(closing2OpeningTag, targetTokensWithTags);
        System.out.println("removeRedundantTags: " + toString(targetTokensWithTags));
        targetTokensWithTags =
            MarianNmtConnector.balanceTags(closing2OpeningTag, targetTokensWithTags);
        System.out.println("balanceTags:         " + toString(targetTokensWithTags));
        String xml = MarianNmtConnector.toXml(targetTokensWithTags, closing2OpeningTag);
        System.out.println(xml);
        if (!isValidXml(xml)) {
          System.err.println(String.format("xml not valid:%n%s", xml));
          System.exit(1);
        }
      }
    }
  }
}
