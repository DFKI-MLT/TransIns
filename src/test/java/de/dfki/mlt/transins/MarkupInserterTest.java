package de.dfki.mlt.transins;

import static de.dfki.mlt.transins.TagUtils.asXml;
import static de.dfki.mlt.transins.TagUtils.removeTags;
import static de.dfki.mlt.transins.TestUtils.CLOSE1;
import static de.dfki.mlt.transins.TestUtils.CLOSE2;
import static de.dfki.mlt.transins.TestUtils.CLOSE3;
import static de.dfki.mlt.transins.TestUtils.ISO1;
import static de.dfki.mlt.transins.TestUtils.ISO2;
import static de.dfki.mlt.transins.TestUtils.OPEN1;
import static de.dfki.mlt.transins.TestUtils.OPEN2;
import static de.dfki.mlt.transins.TestUtils.OPEN3;
import static de.dfki.mlt.transins.TestUtils.asArray;
import static de.dfki.mlt.transins.TestUtils.asString;
import static de.dfki.mlt.transins.TestUtils.tagMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.io.StringReader;
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
 * Test class for {@link MarkupInserter}.
 *
 * @author Jörg Steffen, DFKI
 */
class MarkupInserterTest {

  // builder to check for valid XML
  private static DocumentBuilder builder;


  @BeforeAll
  public static void init() {

    try {
      builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    }
  }


  /**
   * Test {@link MarkupInserter#createTagMap(String[])}.
   */
  @Test
  void testCreateTagMap() {

    // init variables to be re-used between tests
    String[] tokensWithTags = null;
    TagMap resultTagMap = null;

    // first test
    tokensWithTags = asArray("ISO OPEN1 This CLOSE1 is a OPEN2 test . CLOSE2 ISO");
    resultTagMap = MarkupInserter.createTagMap(tokensWithTags);
    assertThat(resultTagMap.size()).isEqualTo(2);
    assertThat(resultTagMap.entrySet()).contains(entry(OPEN1, CLOSE1), entry(OPEN2, CLOSE2));

    // second test
    tokensWithTags = asArray("ISO OPEN1 This OPEN2 is a CLOSE2 test . CLOSE1 ISO");
    resultTagMap = MarkupInserter.createTagMap(tokensWithTags);
    assertThat(resultTagMap.size()).isEqualTo(2);
    assertThat(resultTagMap.entrySet()).contains(entry(OPEN1, CLOSE1), entry(OPEN2, CLOSE2));
  }


  /**
   * Test {@link MarkupInserter#replaceEmptyTagPairsWithIsos(String[], TagMap, Map)} and
   * {@link MarkupInserter#replaceIsosWithEmptyTagPairs(String[], Map)}.
   */
  @Test
  void testReplaceEmptyTagPairsWithIsos() {

    // init variables to be re-used between tests
    String[] tokensWithTags = null;
    String[] expectedResult = null;
    Map<String, List<String>> isoReplacements = null;

    // one empty tag pair
    tokensWithTags = asArray("x OPEN3 CLOSE3 y");
    expectedResult = asArray("x ISO1 y");
    isoReplacements = new HashMap<>();
    testReplaceEmptyTagPairsWithIsos(tokensWithTags, expectedResult, isoReplacements);
    assertThat(isoReplacements).hasSize(1);
    assertThat(isoReplacements).contains(entry(ISO1, Arrays.asList(asArray("OPEN3 CLOSE3"))));

    // two empty tag pairs
    tokensWithTags = asArray("x OPEN3 CLOSE3 OPEN2 CLOSE2 y");
    expectedResult = asArray("x ISO1 ISO2 y");
    isoReplacements = new HashMap<>();
    testReplaceEmptyTagPairsWithIsos(tokensWithTags, expectedResult, isoReplacements);
    assertThat(isoReplacements).hasSize(2);
    assertThat(isoReplacements).contains(entry(ISO1, Arrays.asList(asArray("OPEN3 CLOSE3"))));
    assertThat(isoReplacements).contains(entry(ISO2, Arrays.asList(asArray("OPEN2 CLOSE2"))));

    // two nested empty tag pairs
    tokensWithTags = asArray("x OPEN3 OPEN2 CLOSE2 CLOSE3 y");
    expectedResult = asArray("x ISO1 y");
    isoReplacements = new HashMap<>();
    testReplaceEmptyTagPairsWithIsos(tokensWithTags, expectedResult, isoReplacements);
    assertThat(isoReplacements).hasSize(1);
    assertThat(isoReplacements).contains(
        entry(ISO1, Arrays.asList(asArray("OPEN3 OPEN2 CLOSE2 CLOSE3"))));

    // one empty tag pair with isolated tag
    tokensWithTags = asArray("x OPEN3 ISO1 CLOSE3 y");
    expectedResult = asArray("x ISO2 y");
    isoReplacements = new HashMap<>();
    testReplaceEmptyTagPairsWithIsos(tokensWithTags, expectedResult, isoReplacements);
    assertThat(isoReplacements).hasSize(1);
    assertThat(isoReplacements).contains(entry(ISO2, Arrays.asList(asArray("OPEN3 ISO1 CLOSE3"))));

    // two nested empty tag pairs with isolated tag
    tokensWithTags = asArray("x OPEN3 OPEN2 ISO1 CLOSE2 CLOSE3 y");
    expectedResult = asArray("x ISO2 y");
    isoReplacements = new HashMap<>();
    testReplaceEmptyTagPairsWithIsos(tokensWithTags, expectedResult, isoReplacements);
    assertThat(isoReplacements).hasSize(1);
    assertThat(isoReplacements).contains(
        entry(ISO2, Arrays.asList(asArray("OPEN3 OPEN2 ISO1 CLOSE2 CLOSE3"))));

    // one empty tag pair nested in non-empty pair
    tokensWithTags = asArray("x OPEN2 y OPEN3 CLOSE3 a CLOSE2 b");
    expectedResult = asArray("x OPEN2 y ISO1 a CLOSE2 b");
    isoReplacements = new HashMap<>();
    testReplaceEmptyTagPairsWithIsos(tokensWithTags, expectedResult, isoReplacements);
    assertThat(isoReplacements).hasSize(1);
    assertThat(isoReplacements).contains(entry(ISO1, Arrays.asList(asArray("OPEN3 CLOSE3"))));
  }


  private void testReplaceEmptyTagPairsWithIsos(
      String[] tokensWithTags, String[] expectedResult, Map<String, List<String>> isoReplacements) {

    String[] resultTokens = MarkupInserter.replaceEmptyTagPairsWithIsos(
        tokensWithTags, tagMap, isoReplacements);
    assertThat(resultTokens)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(expectedResult), asString(resultTokens)))
        .containsExactly(expectedResult);

    String[] originalTokens = MarkupInserter.replaceIsosWithEmptyTagPairs(
        resultTokens, isoReplacements);
    assertThat(originalTokens)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(originalTokens), asString(tokensWithTags)))
        .containsExactly(tokensWithTags);
  }


  /**
   * Test {@link MarkupInserter#createTokenIndex2Tags(SplitTagsSentence)}.
   */
  @Test
  void testCreateTokenIndex2Tags() {

    // init variables to be re-used between tests
    String[] tokensWithTags = null;
    Map<Integer, List<String>> index2Tags = null;

    // simple case
    tokensWithTags = asArray("start ISO1 OPEN1 This CLOSE1 is a OPEN2 test . CLOSE2 ISO2 end");
    index2Tags = MarkupInserter.createTokenIndex2Tags(
        new SplitTagsSentence(tokensWithTags, tagMap));
    assertThat(index2Tags).hasSize(4);
    assertThat(index2Tags.get(1)).containsExactly(ISO1, OPEN1, CLOSE1);
    assertThat(index2Tags.get(4)).containsExactly(OPEN2);
    assertThat(index2Tags.get(5)).containsExactly(CLOSE2);
    assertThat(index2Tags.get(6)).containsExactly(ISO2);

    // single tag pair
    tokensWithTags = asArray("start OPEN1 x y CLOSE1 end");
    index2Tags = MarkupInserter.createTokenIndex2Tags(
        new SplitTagsSentence(tokensWithTags, tagMap));
    assertThat(index2Tags).hasSize(2);
    assertThat(index2Tags.get(1)).containsExactly(OPEN1);
    assertThat(index2Tags.get(2)).containsExactly(CLOSE1);

    // two tag pairs
    tokensWithTags = asArray("start OPEN1 x y CLOSE1 OPEN2 a b CLOSE2 end");
    index2Tags = MarkupInserter.createTokenIndex2Tags(
        new SplitTagsSentence(tokensWithTags, tagMap));
    assertThat(index2Tags).hasSize(4);
    assertThat(index2Tags.get(1)).containsExactly(OPEN1);
    assertThat(index2Tags.get(2)).containsExactly(CLOSE1);
    assertThat(index2Tags.get(3)).containsExactly(OPEN2);
    assertThat(index2Tags.get(4)).containsExactly(CLOSE2);

    // two tag pairs, nested
    tokensWithTags = asArray("start OPEN1 OPEN2 x y CLOSE2 CLOSE1 end");
    index2Tags = MarkupInserter.createTokenIndex2Tags(
        new SplitTagsSentence(tokensWithTags, tagMap));
    assertThat(index2Tags).hasSize(2);
    assertThat(index2Tags.get(1)).containsExactly(OPEN1, OPEN2);
    assertThat(index2Tags.get(2)).containsExactly(CLOSE2, CLOSE1);

    // single tag pair with ISO at beginning
    tokensWithTags = asArray("start OPEN1 ISO1 x y CLOSE1 end");
    index2Tags = MarkupInserter.createTokenIndex2Tags(
        new SplitTagsSentence(tokensWithTags, tagMap));
    assertThat(index2Tags).hasSize(2);
    assertThat(index2Tags.get(1)).containsExactly(OPEN1, ISO1);
    assertThat(index2Tags.get(2)).containsExactly(CLOSE1);

    // single tag pair with ISO at middle
    tokensWithTags = asArray("start OPEN1 x ISO1 y CLOSE1 end");
    index2Tags = MarkupInserter.createTokenIndex2Tags(
        new SplitTagsSentence(tokensWithTags, tagMap));
    assertThat(index2Tags).hasSize(2);
    assertThat(index2Tags.get(1)).containsExactly(OPEN1);
    assertThat(index2Tags.get(2)).containsExactly(ISO1, CLOSE1);

    // single tag pair with ISO at end
    tokensWithTags = asArray("start OPEN1 x y ISO1 CLOSE1 end");
    index2Tags = MarkupInserter.createTokenIndex2Tags(
        new SplitTagsSentence(tokensWithTags, tagMap));
    assertThat(index2Tags).hasSize(3);
    assertThat(index2Tags.get(1)).containsExactly(OPEN1);
    assertThat(index2Tags.get(2)).containsExactly(CLOSE1);
    assertThat(index2Tags.get(3)).containsExactly(ISO1);

    // single tag pair with two ISOs
    tokensWithTags = asArray("start OPEN1 x ISO1 y ISO2 CLOSE1 end");
    index2Tags = MarkupInserter.createTokenIndex2Tags(
        new SplitTagsSentence(tokensWithTags, tagMap));
    assertThat(index2Tags).hasSize(3);
    assertThat(index2Tags.get(1)).containsExactly(OPEN1);
    assertThat(index2Tags.get(2)).containsExactly(ISO1, CLOSE1);
    assertThat(index2Tags.get(3)).containsExactly(ISO2);
  }


  /**
   * Test {@link MarkupInserter#moveSourceTagsToPointedTokens(Map, Map, List, int)}.
   */
  @Test
  void testMoveSourceTagsToPointedTokens() {

    // init variables to be re-used between tests
    String[] tokensWithTags = null;
    List<Integer> pointedSourceTokens = null;
    Map<Integer, List<String>> sourceTokenIndex2Tags = null;
    List<String> unusedTags = null;

    // ISO not pointed to, but following pointed token
    tokensWithTags = asArray("x ISO1 y z");
    pointedSourceTokens = List.of(2);
    sourceTokenIndex2Tags =
        MarkupInserter.createTokenIndex2Tags(new SplitTagsSentence(tokensWithTags, tagMap));
    unusedTags = MarkupInserter.moveSourceTagsToPointedTokens(
        sourceTokenIndex2Tags, tagMap, pointedSourceTokens, removeTags(tokensWithTags).length);

    assertThat(unusedTags).isEmpty();
    assertThat(sourceTokenIndex2Tags).hasSize(1);
    assertThat(sourceTokenIndex2Tags.get(2)).containsExactly(ISO1);

    // ISO not pointed to, no following pointed token
    tokensWithTags = asArray("x ISO1 y z");
    pointedSourceTokens = List.of(0);
    sourceTokenIndex2Tags =
        MarkupInserter.createTokenIndex2Tags(new SplitTagsSentence(tokensWithTags, tagMap));
    unusedTags = MarkupInserter.moveSourceTagsToPointedTokens(
        sourceTokenIndex2Tags, tagMap, pointedSourceTokens, removeTags(tokensWithTags).length);

    assertThat(unusedTags).containsExactly(ISO1);
    assertThat(sourceTokenIndex2Tags).isEmpty();

    // one tag pair without pointing tokens
    tokensWithTags = asArray("OPEN1 OPEN2 This CLOSE2 is a CLOSE1 test .");
    pointedSourceTokens = List.of(1, 2);
    sourceTokenIndex2Tags =
        MarkupInserter.createTokenIndex2Tags(new SplitTagsSentence(tokensWithTags, tagMap));
    unusedTags = MarkupInserter.moveSourceTagsToPointedTokens(
        sourceTokenIndex2Tags, tagMap, pointedSourceTokens, removeTags(tokensWithTags).length);

    assertThat(unusedTags).containsExactly(OPEN2, CLOSE2);
    assertThat(sourceTokenIndex2Tags).hasSize(2);
    assertThat(sourceTokenIndex2Tags.get(1)).containsExactly(OPEN1);
    assertThat(sourceTokenIndex2Tags.get(2)).containsExactly(CLOSE1);

    // all tag pairs with pointing tokens
    tokensWithTags = asArray("OPEN1 OPEN2 This CLOSE2 is a CLOSE1 test .");
    pointedSourceTokens = List.of(0, 1, 2);
    sourceTokenIndex2Tags =
        MarkupInserter.createTokenIndex2Tags(new SplitTagsSentence(tokensWithTags, tagMap));
    unusedTags = MarkupInserter.moveSourceTagsToPointedTokens(
        sourceTokenIndex2Tags, tagMap, pointedSourceTokens, removeTags(tokensWithTags).length);

    assertThat(unusedTags).isEmpty();
    assertThat(sourceTokenIndex2Tags).hasSize(2);
    assertThat(sourceTokenIndex2Tags.get(0)).containsExactly(OPEN1, OPEN2, CLOSE2);
    assertThat(sourceTokenIndex2Tags.get(2)).containsExactly(CLOSE1);

    // one tag pair without pointing tokens, ISO not at sentence end
    tokensWithTags = asArray("OPEN1 OPEN2 x CLOSE2 y z a CLOSE1 b ISO2 c");
    pointedSourceTokens = List.of(1, 2);
    sourceTokenIndex2Tags =
        MarkupInserter.createTokenIndex2Tags(new SplitTagsSentence(tokensWithTags, tagMap));
    unusedTags = MarkupInserter.moveSourceTagsToPointedTokens(
        sourceTokenIndex2Tags, tagMap, pointedSourceTokens, removeTags(tokensWithTags).length);

    assertThat(unusedTags).containsExactly(OPEN2, CLOSE2, ISO2);
    assertThat(sourceTokenIndex2Tags).hasSize(2);
    assertThat(sourceTokenIndex2Tags.get(1)).containsExactly(OPEN1);
    assertThat(sourceTokenIndex2Tags.get(2)).containsExactly(CLOSE1);
  }


  /**
   * Test {@link MarkupInserter#getTagsForSourceTokenIndex(int, Map, String[])}.
   */
  @Test
  void testGetTagsForSourceTokenIndex() {

    String[] sourceTokens =
        asArray("start ISO1 OPEN1 Th@@ i@@ s CLOSE1 is a OPEN2 te@@ st . CLOSE2 ISO2 end");
    String[] sourceTokensWithoutTags = removeTags(sourceTokens);
    Map<Integer, List<String>> sourceTokenIndex2Tags =
        MarkupInserter.createTokenIndex2Tags(new SplitTagsSentence(sourceTokens, tagMap));

    // init variables to be re-used between tests
    List<String> tags = null;

    // non-bpe token 'is'
    tags = MarkupInserter.getTagsForSourceTokenIndex(
        4, sourceTokenIndex2Tags, sourceTokensWithoutTags);
    assertThat(tags).isEmpty();

    // last bpe fragment 's'
    tags = MarkupInserter.getTagsForSourceTokenIndex(
        3, sourceTokenIndex2Tags, sourceTokensWithoutTags);
    assertThat(tags).containsExactly(ISO1, OPEN1, CLOSE1);

    // middle bpe fragment 'i@@'
    tags = MarkupInserter.getTagsForSourceTokenIndex(
        2, sourceTokenIndex2Tags, sourceTokensWithoutTags);
    assertThat(tags).containsExactly(ISO1, OPEN1, CLOSE1);

    // first bpe fragment 'Th@@'
    tags = MarkupInserter.getTagsForSourceTokenIndex(
        1, sourceTokenIndex2Tags, sourceTokensWithoutTags);
    assertThat(tags).containsExactly(ISO1, OPEN1, CLOSE1);

    // EOS
    tags = MarkupInserter.getTagsForSourceTokenIndex(
        9, sourceTokenIndex2Tags, sourceTokensWithoutTags);
    assertThat(tags).containsExactly(ISO2);
  }


  /**
   * Test
   * {@link MarkupInserter#reinsertTags(SplitTagsSentence, Map, String[], Alignments)}
   * with soft alignments.
   */
  @Test
  void testReinsertTagsWithSoftAlignments() {

    String[] sourceTokens = asArray("ISO1 OPEN1 This CLOSE1 is a OPEN2 test . CLOSE2 ISO2");

    // init variables to be re-used between tests
    String[] targetTokensWithoutTags = null;
    String rawAlignments = null;
    String[] expectedResult = null;

    // parallel alignment
    targetTokensWithoutTags = "Das ist ein Test .".split(" ");
    rawAlignments = ""
        + "1,0,0,0,0,0 " // Das -> This
        + "0,1,0,0,0,0 " // ist -> is
        + "0,0,1,0,0,0 " // ein -> a
        + "0,0,0,1,0,0 " // Test -> test
        + "0,0,0,0,1,0 " // . -> .
        + "0,0,0,0,0,1"; // EOS -> EOS
    expectedResult = asArray("ISO1 OPEN1 Das CLOSE1 ist ein OPEN2 Test . CLOSE2 ISO2");
    testReinsertTagsWithSoftAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // reversed alignment
    targetTokensWithoutTags = "Test ein ist das .".split(" ");
    rawAlignments = ""
        + "0,0,0,1,0,0 " // Test -> test
        + "0,0,1,0,0,0 " // ein -> a
        + "0,1,0,0,0,0 " // ist -> is
        + "1,0,0,0,0,0 " // das -> This
        + "0,0,0,0,1,0 " // . -> .
        + "0,0,0,0,0,1"; // EOS -> EOS
    expectedResult = asArray("ISO1 OPEN2 Test ein ist OPEN1 das CLOSE1 . CLOSE2 ISO2");
    testReinsertTagsWithSoftAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);
  }


  /**
   * Test
   * {@link MarkupInserter#reinsertTags(SplitTagsSentence, Map, String[], Alignments)}
   * with hard alignments.
   */
  @Test
  void testReinsertTagsWithHardAlignments() {

    // init variables to be re-used between tests
    String[] sourceTokens = null;
    String[] targetTokensWithoutTags = null;
    String rawAlignments = null;
    String[] expectedResult = null;

    // parallel alignment
    sourceTokens = asArray("ISO1 OPEN1 This CLOSE1 is a OPEN2 test . CLOSE2 ISO2");
    targetTokensWithoutTags = asArray("Das ist ein Test .");
    rawAlignments = "0-0 1-1 2-2 3-3 4-4 5-5";
    expectedResult = asArray("ISO1 OPEN1 Das CLOSE1 ist ein OPEN2 Test . CLOSE2 ISO2");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // reversed alignment
    sourceTokens = asArray("ISO1 OPEN1 This CLOSE1 is a OPEN2 test . CLOSE2 ISO2");
    targetTokensWithoutTags = asArray("Test ein ist das .");
    //                                 This is  a   Test .
    rawAlignments = "0-3 1-2 2-1 3-0 4-4 5-5";
    expectedResult = asArray("ISO1 OPEN2 Test ein ist OPEN1 das CLOSE1 . CLOSE2 ISO2");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // end-of-sentence points to source token with tag
    sourceTokens = asArray("ISO1 OPEN1 Zum Inhalt springen CLOSE1 ISO2");
    targetTokensWithoutTags = asArray("aller au contenu");
    rawAlignments = "0-0 1-2 2-3";
    expectedResult = asArray("ISO1 OPEN1 aller au contenu CLOSE1 ISO2");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // tags enclosing the whole sentence
    sourceTokens = asArray("ISO1 OPEN1 a b c d CLOSE1 ISO2");
    targetTokensWithoutTags = asArray("b a d c");
    rawAlignments = "0-1 1-0 2-3 4-2";
    expectedResult = asArray("ISO1 OPEN1 b a d c CLOSE1 ISO2");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // tag pair over the whole sentence
    sourceTokens = asArray("OPEN1 a b c d CLOSE1");
    targetTokensWithoutTags = asArray("b a d c");
    rawAlignments = "0-1 1-0 2-3 4-2";
    expectedResult = asArray("OPEN1 b a d c CLOSE1");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // multiple tag pairs over the whole sentence
    sourceTokens = asArray("OPEN1 OPEN2 OPEN3 a b c d CLOSE3 CLOSE2 CLOSE1");
    targetTokensWithoutTags = asArray("b a d c");
    rawAlignments = "0-1 1-0 2-3 4-2";
    expectedResult = asArray("OPEN1 OPEN2 OPEN3 b a d c CLOSE3 CLOSE2 CLOSE1");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // tokens with isolated tags are pointed to multiple times
    sourceTokens = asArray("x ISO1 ISO2 OPEN1 y z CLOSE1");
    targetTokensWithoutTags = asArray("a b c");
    rawAlignments = "1-0 1-1 2-2";
    expectedResult = asArray("ISO1 ISO2 OPEN1 a OPEN1 b c CLOSE1");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // one target token to multiple source tokens with same tags
    sourceTokens = asArray("x ISO1 ISO2 OPEN1 y z CLOSE1");
    targetTokensWithoutTags = asArray("a b c");
    rawAlignments = "1-0 2-0 2-2";
    expectedResult = asArray("ISO1 ISO2 OPEN1 a CLOSE1 b c CLOSE1");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // single tag pair with ISO at end
    sourceTokens = asArray("x OPEN1 y z ISO1 CLOSE1");
    targetTokensWithoutTags = asArray("a b c");
    rawAlignments = "0-0 1-1 2-2";
    expectedResult = asArray("a OPEN1 b c CLOSE1 ISO1");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // single tag pair with ISO at beginning
    sourceTokens = asArray("ISO1 OPEN1 x y CLOSE1 z");
    targetTokensWithoutTags = asArray("a b c");
    rawAlignments = "0-0 1-1 2-2";
    expectedResult = asArray("ISO1 OPEN1 a b CLOSE1 c");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);
  }


  /**
   * Test
   * {@link MarkupInserter#reinsertTags(SplitTagsSentence, Map, String[], Alignments)}
   * with more complex examples.
   */
  @Test
  void testReinsertTagsComplex() {

    String[] sourceTokens = asArray("OPEN1 x y z CLOSE1 a b c");

    // init variables to be re-used between tests
    String[] targetTokensWithoutTags = null;
    String rawAlignments = null;
    String[] expectedResult = null;

    // first test
    targetTokensWithoutTags = asArray("X1 N Z X2 N N");
    rawAlignments = "0-0 0-3 2-2";
    expectedResult = asArray("OPEN1 X1 N Z CLOSE1 OPEN1 X2 N N");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // second test
    targetTokensWithoutTags = asArray("Z1 Z2 X N N N");
    rawAlignments = "0-2 2-0 2-1";
    expectedResult = asArray("Z1 CLOSE1 Z2 CLOSE1 OPEN1 X N N N");
    testReinsertTagsWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // third test
    targetTokensWithoutTags = asArray("Z1 N X1 Z2 N X2");
    rawAlignments = "0-2 0-5 2-0 2-3";
    expectedResult = asArray("Z1 CLOSE1 N OPEN1 X1 Z2 CLOSE1 N OPEN1 X2");
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

    Alignments algn = null;
    if (hardAlignments) {
      algn = new HardAlignments(rawAlignments);
    } else {
      algn = new SoftAlignments(rawAlignments);
    }

    SplitTagsSentence sourceSentence = new SplitTagsSentence(sourceTokens, tagMap);
    Map<Integer, List<String>> sourceTokenIndex2tags =
        MarkupInserter.createTokenIndex2Tags(sourceSentence);

    String[] targetTokensWithTags =
        MarkupInserter.reinsertTags(
            sourceSentence, sourceTokenIndex2tags, targetTokensWithoutTags, algn);
    assertThat(targetTokensWithTags)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(expectedResult), asString(targetTokensWithTags)))
        .containsExactly(expectedResult);
  }


  /**
   * Test {@link MarkupInserter#moveTagsFromBetweenBpeFragments(String[])}.
   */
  @Test
  void testMoveTagsFromBetweenBpeFragments() {

    // init variables to be re-used between tests
    String[] targetTokens;
    String[] expectedResult = null;

    // two fragments with opening tag
    targetTokens = asArray("a b c@@ OPEN1 x y z");
    expectedResult = asArray("a b OPEN1 c@@ x y z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // three fragments with opening tag
    targetTokens = asArray("a b@@ c@@ OPEN1 x y z");
    expectedResult = asArray("a OPEN1 b@@ c@@ x y z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // four fragments with opening tag
    targetTokens = asArray("a@@ b@@ c@@ OPEN1 x y z");
    expectedResult = asArray("OPEN1 a@@ b@@ c@@ x y z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // two fragments followed by two fragments with opening tag
    targetTokens = asArray("a@@ b c@@ OPEN1 x y z");
    expectedResult = asArray("a@@ b OPEN1 c@@ x y z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // two fragments with closing tag
    targetTokens = asArray("a b c@@ CLOSE1 x y z");
    expectedResult = asArray("a b c@@ x CLOSE1 y z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // three fragments with closing tag
    targetTokens = asArray("a b c@@ CLOSE1 x@@ y z");
    expectedResult = asArray("a b c@@ x@@ y CLOSE1 z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // four fragments with closing tag
    targetTokens = asArray("a b c@@ CLOSE1 x@@ y@@ z");
    expectedResult = asArray("a b c@@ x@@ y@@ z CLOSE1");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // two fragments with closing tag followed by two fragments
    targetTokens = asArray("a b c@@ CLOSE1 x y@@ z");
    expectedResult = asArray("a b c@@ x CLOSE1 y@@ z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // opening and closing tags between fragments
    targetTokens = asArray("a b c@@ OPEN1 CLOSE1 x y@@ z");
    expectedResult = asArray("a b OPEN1 c@@ x CLOSE1 y@@ z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // closing and opening tags between fragments
    targetTokens = asArray("a b c@@ CLOSE1 OPEN1 x y@@ z");
    expectedResult = asArray("a b OPEN1 c@@ x CLOSE1 y@@ z");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // two opening and two closing tag between fragments
    targetTokens = asArray("a OPEN1 b@@ OPEN2 OPEN3 CLOSE2 CLOSE3 c CLOSE1");
    expectedResult = asArray("a OPEN1 OPEN2 OPEN3 b@@ c CLOSE3 CLOSE2 CLOSE1");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // two closing and two opening tag between fragments
    targetTokens = asArray("a OPEN1 b@@ CLOSE2 CLOSE3 OPEN2 OPEN3 c CLOSE1");
    expectedResult = asArray("a OPEN1 OPEN2 OPEN3 b@@ c CLOSE3 CLOSE2 CLOSE1");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // fragments at beginning of sentence
    targetTokens = asArray("a@@ OPEN1 CLOSE1 b c");
    expectedResult = asArray("OPEN1 a@@ b CLOSE1 c");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // fragments at end of sentence
    targetTokens = asArray("a b@@ OPEN1 CLOSE1 c");
    expectedResult = asArray("a OPEN1 b@@ c CLOSE1");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);

    // closing before opening tags between fragments
    targetTokens = asArray("a OPEN1 b@@ CLOSE1 OPEN2 c CLOSE2");
    expectedResult = asArray("a OPEN1 OPEN2 b@@ c CLOSE2 CLOSE1");
    testMoveTagsFromBetweenBpeFragments(targetTokens, expectedResult);
  }


  private void testMoveTagsFromBetweenBpeFragments(
      String[] targetTokens, String[] expectedResult) {

    targetTokens = MarkupInserter.moveTagsFromBetweenBpeFragments(targetTokens, tagMap);
    assertThat(targetTokens)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(expectedResult), asString(targetTokens)))
        .containsExactly(expectedResult);
  }


  /**
   * Test {@link MarkupInserter#undoBytePairEncoding(String[])}.
   */
  @Test
  void testUndoBytePairEncoding() {

    // init variables to be re-used between tests
    String[] targetTokens = null;
    String[] expectedResult = null;

    // simple case
    targetTokens = asArray("a b@@ c@@ d x");
    expectedResult = asArray("a bcd x");
    testUndoBytePairEncoding(targetTokens, expectedResult);

    // at sentence beginning
    targetTokens = asArray("b@@ c@@ d x");
    expectedResult = asArray("bcd x");
    testUndoBytePairEncoding(targetTokens, expectedResult);

    // at sentence end
    targetTokens = asArray("a b@@ c@@ d");
    expectedResult = asArray("a bcd");
    testUndoBytePairEncoding(targetTokens, expectedResult);
  }


  private void testUndoBytePairEncoding(String[] targetTokens, String[] expectedResult) {

    targetTokens = MarkupInserter.undoBytePairEncoding(targetTokens);
    assertThat(targetTokens)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(expectedResult), asString(targetTokens)))
        .containsExactly(expectedResult);
  }


  /**
   * Test {@link MarkupInserter#handleInvertedTags(Map, String[])}.
   */
  @Test
  void testHandleInvertedTags() {

    // init variables to be re-used between tests
    String[] targetTokens = null;
    String[] expectedResult = null;

    // single closing tag
    targetTokens = asArray("x CLOSE1 y");
    expectedResult = asArray("x y");
    testHandleInvertedTags(targetTokens, expectedResult);

    // multiple closing tags
    targetTokens = asArray("x CLOSE1 y CLOSE1 z");
    expectedResult = asArray("x y z ");
    testHandleInvertedTags(targetTokens, expectedResult);

    // multiple closing tags
    targetTokens = asArray("x CLOSE1 y CLOSE2 z");
    expectedResult = asArray("x y z");
    testHandleInvertedTags(targetTokens, expectedResult);

    // single closing tag at beginning
    targetTokens = asArray("CLOSE1 x y");
    expectedResult = asArray("x y");
    testHandleInvertedTags(targetTokens, expectedResult);

    // multiple closing tags at beginning
    targetTokens = asArray("CLOSE1 CLOSE1 x y");
    expectedResult = asArray("x y");
    testHandleInvertedTags(targetTokens, expectedResult);

    // single closing tag at end
    targetTokens = asArray("x y CLOSE1");
    expectedResult = asArray("x y");
    testHandleInvertedTags(targetTokens, expectedResult);

    // multiple closing tags at end
    targetTokens = asArray("x y CLOSE1 CLOSE1");
    expectedResult = asArray("x y");
    testHandleInvertedTags(targetTokens, expectedResult);

    // closing tag followed by opening tag
    targetTokens = asArray("x CLOSE1 y OPEN1 z");
    expectedResult = asArray("OPEN1 x y z CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // closing tag at beginning followed by opening tag
    targetTokens = asArray("CLOSE1 x y OPEN1 z");
    expectedResult = asArray("OPEN1 x y z CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // closing tag followed by opening tag at end
    targetTokens = asArray("x CLOSE1 y z OPEN1");
    expectedResult = asArray("OPEN1 x y z CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // closing tag at beginning followed by opening tag at end
    targetTokens = asArray("CLOSE1 x y z OPEN1");
    expectedResult = asArray("OPEN1 x y z CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // two inverted tags with gap
    targetTokens = asArray("x CLOSE1 y OPEN1 z a CLOSE1 b OPEN1 c");
    expectedResult = asArray("OPEN1 x y z CLOSE1 OPEN1 a b c CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // two inverted tags without gap
    targetTokens = asArray("x CLOSE1 y OPEN1 z CLOSE1 a OPEN1 b c");
    expectedResult = asArray("OPEN1 x y OPEN1 z CLOSE1 a b CLOSE1 c");
    testHandleInvertedTags(targetTokens, expectedResult);

    // two nested inverted tags with gap
    targetTokens = asArray("x CLOSE1 y CLOSE1 z a OPEN1 b OPEN1 c");
    expectedResult = asArray("OPEN1 x y CLOSE1 z a b CLOSE1 OPEN1 c");
    testHandleInvertedTags(targetTokens, expectedResult);

    // two nested inverted tags without gap
    targetTokens = asArray("x CLOSE1 y CLOSE1 z OPEN1 a OPEN1 b c");
    expectedResult = asArray("OPEN1 x y CLOSE1 z a CLOSE1 OPEN1 b c");
    testHandleInvertedTags(targetTokens, expectedResult);

    // two nested inverted tags with gap, mixed
    targetTokens = asArray("x CLOSE1 y CLOSE2 z a OPEN2 b OPEN1 c");
    expectedResult = asArray("OPEN1 x OPEN2 y z a b CLOSE2 c CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // two nested inverted tags with gap, mixed, overlapping
    targetTokens = asArray("x CLOSE1 y CLOSE2 z a OPEN1 b OPEN2 c");
    expectedResult = asArray("OPEN1 x OPEN2 y z a b CLOSE1 c CLOSE2");
    testHandleInvertedTags(targetTokens, expectedResult);

    // inverted tags followed by non-inverted tags with gap
    targetTokens = asArray("x CLOSE1 y OPEN1 z a OPEN1 b CLOSE1 c");
    expectedResult = asArray("OPEN1 x y z CLOSE1 a OPEN1 b CLOSE1 c");
    testHandleInvertedTags(targetTokens, expectedResult);

    // non-inverted tags followed by inverted tags with gap
    targetTokens = asArray("x OPEN1 y CLOSE1 z a CLOSE1 b OPEN1 c");
    expectedResult = asArray("x OPEN1 y CLOSE1 z OPEN1 a b c CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // inverted tags followed by non-inverted tags without gap
    targetTokens = asArray("x CLOSE1 y OPEN1 z OPEN1 a CLOSE1 b");
    expectedResult = asArray("OPEN1 x y z CLOSE1 OPEN1 a CLOSE1 b");
    testHandleInvertedTags(targetTokens, expectedResult);

    // non-inverted tags followed by inverted tags without gap
    targetTokens = asArray("x OPEN1 y CLOSE1 z CLOSE1 a OPEN1 b");
    expectedResult = asArray("x OPEN1 y CLOSE1 OPEN1 z a b CLOSE1");
    testHandleInvertedTags(targetTokens, expectedResult);

    // mixed with isolated tags
    targetTokens = asArray("ISO Das CLOSE1 OPEN1 ist OPEN2 ein Test . CLOSE2 ISO");
    expectedResult = asArray("ISO OPEN1 Das ist CLOSE1 OPEN2 ein Test . CLOSE2 ISO");
    testHandleInvertedTags(targetTokens, expectedResult);

    // mixed with isolated tags, nested
    targetTokens = asArray("ISO Das CLOSE2 CLOSE1 OPEN1 OPEN2 ist ein Test . ISO");
    expectedResult = asArray("ISO OPEN1 OPEN2 Das ist CLOSE2 CLOSE1 ein Test . ISO");
    testHandleInvertedTags(targetTokens, expectedResult);
  }


  private void testHandleInvertedTags(String[] targetTokens, String[] expectedResult) {

    targetTokens = MarkupInserter.handleInvertedTags(tagMap, targetTokens);
    assertThat(targetTokens)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(expectedResult), asString(targetTokens)))
        .containsExactly(expectedResult);
  }


  /**
   * Test {@link MarkupInserter#removeRedundantTags(Map, String[])}.
   */
  @Test
  void testRemoveRedundantTags() {

    // init variables to be re-used between tests
    String[] targetTokens = null;
    String[] expectedResult = null;

    // single opening tag
    targetTokens = asArray("x OPEN1 y");
    expectedResult = asArray("x y");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // multiple opening tags
    targetTokens = asArray("x OPEN1 y OPEN1");
    expectedResult = asArray("x y");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // multiple opening tags and multiple closing tags
    targetTokens = asArray("x OPEN1 y OPEN1 z CLOSE1 a b CLOSE1 c");
    expectedResult = asArray("x OPEN1 y z a b CLOSE1 c");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // multiple opening tags and single closing tag
    targetTokens = asArray("x OPEN1 y OPEN1 z CLOSE1 a b c");
    expectedResult = asArray("x OPEN1 y z CLOSE1 a b c");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // single opening tag and two closing tags
    targetTokens = asArray("x OPEN1 y z CLOSE1 a b CLOSE1 c");
    expectedResult = asArray("x OPEN1 y z a b CLOSE1 c");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // single opening tag and three closing tags
    targetTokens = asArray("x OPEN1 y z CLOSE1 a b CLOSE1 c CLOSE1 d");
    expectedResult = asArray("x OPEN1 y z a b c CLOSE1 d");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // multiple opening tags and multiple closing tags followed by tag pair
    targetTokens = asArray("x OPEN1 y OPEN1 z CLOSE1 a b CLOSE1 c OPEN1 i j CLOSE1 k");
    expectedResult = asArray("x OPEN1 y z a b CLOSE1 c OPEN1 i j CLOSE1 k");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // mixed tag pairs
    targetTokens = asArray("x OPEN1 y OPEN1 z CLOSE1 a b CLOSE1 c OPEN2 i OPEN2 j CLOSE2 k");
    expectedResult = asArray("x OPEN1 y z a b CLOSE1 c OPEN2 i j CLOSE2 k");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // mixed tag pairs, nested
    targetTokens = asArray("x OPEN1 y OPEN1 z OPEN2 a b OPEN2 c CLOSE2 i CLOSE1 j CLOSE1 k");
    expectedResult = asArray("x OPEN1 y z OPEN2 a b c CLOSE2 i j CLOSE1 k");
    testRemoveRedundantTags(targetTokens, expectedResult);

    // mixed tag pairs, overlapping
    targetTokens = asArray("x OPEN1 y OPEN1 z OPEN2 a b OPEN2 c CLOSE1 i CLOSE1 j CLOSE2 k");
    expectedResult = asArray("x OPEN1 y z OPEN2 a b c i CLOSE1 j CLOSE2 k");
    testRemoveRedundantTags(targetTokens, expectedResult);
  }


  private void testRemoveRedundantTags(String[] targetTokens, String[] expectedResult) {

    targetTokens = MarkupInserter.removeRedundantTags(tagMap, targetTokens);
    assertThat(targetTokens)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(expectedResult), asString(targetTokens)))
        .containsExactly(expectedResult);
  }


  /**
   * Test {@link MarkupInserter#sortOpeningTags(int, int, String[], Map)}.
   */
  @Test
  void testSortOpeningTags() {

    // init variables to be re-used between tests
    String[] targetTokens = null;
    String[] expectedResult = null;

    // closing tags in same order
    targetTokens = asArray("x OPEN1 OPEN2 OPEN3 y CLOSE1 z CLOSE2 a CLOSE3");
    expectedResult = asArray("x OPEN3 OPEN2 OPEN1 y CLOSE1 z CLOSE2 a CLOSE3");
    assertThat(MarkupInserter.sortOpeningTags(1, 4, targetTokens, tagMap))
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(expectedResult), asString(targetTokens)))
        .containsExactly(expectedResult);

    // closing tags in inverse order
    targetTokens = asArray("x OPEN1 OPEN2 OPEN3 y CLOSE3 z CLOSE2 a CLOSE1");
    expectedResult = asArray("x OPEN1 OPEN2 OPEN3 y CLOSE3 z CLOSE2 a CLOSE1");
    assertThat(MarkupInserter.sortOpeningTags(1, 4, targetTokens, tagMap))
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(expectedResult), asString(targetTokens)))
        .containsExactly(expectedResult);

    // non-tag in range
    assertThatExceptionOfType(OkapiException.class).isThrownBy(
        () -> {
          MarkupInserter.sortOpeningTags(0, 4,
              asArray("x OPEN1 OPEN2 OPEN3 y CLOSE3 z CLOSE2 a CLOSE1"), tagMap);
        });

    // not enough closing tags
    assertThatExceptionOfType(OkapiException.class).isThrownBy(
        () -> {
          MarkupInserter.sortOpeningTags(1, 4,
              asArray("x OPEN1 OPEN2 OPEN3 y CLOSE3 z CLOSE2 a"), tagMap);
        });
  }


  /**
   * Test {@link MarkupInserter#sortClosingTags(int, int, String[], Map)}.
   */
  @Test
  void testSortClosingTags() {

    // init variables to be re-used between tests
    String[] targetTokens = null;
    String[] expectedResult = null;

    // closing tags in same order
    targetTokens = asArray("x OPEN1 y OPEN2 z OPEN3 a CLOSE1 CLOSE2 CLOSE3 b");
    expectedResult = asArray("x OPEN1 y OPEN2 z OPEN3 a CLOSE3 CLOSE2 CLOSE1 b");
    assertThat(MarkupInserter.sortClosingTags(7, 10, targetTokens, tagMap))
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(expectedResult), asString(targetTokens)))
        .containsExactly(expectedResult);

    // closing tags in inverse order
    targetTokens = asArray("x OPEN1 y OPEN2 z OPEN3 a CLOSE3 CLOSE2 CLOSE1 b");
    expectedResult = asArray("x OPEN1 y OPEN2 z OPEN3 a CLOSE3 CLOSE2 CLOSE1 b");
    assertThat(MarkupInserter.sortClosingTags(7, 10, targetTokens, tagMap))
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(expectedResult), asString(targetTokens)))
        .containsExactly(expectedResult);

    // closing tags mixed
    targetTokens = asArray("OPEN3 x OPEN1 y OPEN2 z CLOSE1 CLOSE3 a CLOSE2 b c");
    expectedResult = asArray("OPEN3 x OPEN1 y OPEN2 z CLOSE1 CLOSE3 a CLOSE2 b c");
    assertThat(MarkupInserter.sortClosingTags(6, 8, targetTokens, tagMap))
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(expectedResult), asString(targetTokens)))
        .containsExactly(expectedResult);

    // non-tag in range
    assertThatExceptionOfType(OkapiException.class).isThrownBy(
        () -> {
          MarkupInserter.sortClosingTags(6, 10,
              asArray("x OPEN1 y OPEN2 z OPEN3 a CLOSE1 CLOSE2 CLOSE3 b"), tagMap);
        });

    // not enough opening tags
    assertThatExceptionOfType(OkapiException.class).isThrownBy(
        () -> {
          MarkupInserter.sortClosingTags(6, 9,
              asArray("x OPEN1 y OPEN2 z OPEN3 a CLOSE1 CLOSE2 CLOSE3 b"), tagMap);
        });
  }


  /**
   * Test {@link MarkupInserter#balanceTags(Map, String[])}.
   */
  @Test
  void testBalanceTags() {

    // init variables to be re-used between tests
    String[] targetTokens = null;
    String[] expectedResult = null;

    // opening tag sequence
    targetTokens = asArray("x OPEN1 OPEN2 y z CLOSE1 a CLOSE2");
    expectedResult = asArray("x OPEN2 OPEN1 y z CLOSE1 a CLOSE2");
    testBalanceTags(targetTokens, expectedResult);

    // opening tag
    targetTokens = asArray("x OPEN1 y z CLOSE1 a");
    expectedResult = asArray("x OPEN1 y z CLOSE1 a");
    testBalanceTags(targetTokens, expectedResult);

    // opening tag sequence at beginning
    targetTokens = asArray("OPEN1 OPEN2 x y CLOSE1 a CLOSE2");
    expectedResult = asArray("OPEN2 OPEN1 x y CLOSE1 a CLOSE2");
    testBalanceTags(targetTokens, expectedResult);

    // opening tag at beginning
    targetTokens = asArray("OPEN1 x y CLOSE1 a");
    expectedResult = asArray("OPEN1 x y CLOSE1 a");
    testBalanceTags(targetTokens, expectedResult);

    // closing tag sequence
    targetTokens = asArray("x OPEN1 y OPEN2 z a CLOSE1 CLOSE2 b");
    expectedResult = asArray("x OPEN1 y OPEN2 z a CLOSE2 CLOSE1 b");
    testBalanceTags(targetTokens, expectedResult);

    // closing tag
    targetTokens = asArray("x OPEN1 y z a CLOSE1 b");
    expectedResult = asArray("x OPEN1 y z a CLOSE1 b");
    testBalanceTags(targetTokens, expectedResult);

    // closing tag sequence at end
    targetTokens = asArray("x OPEN1 y OPEN2 z a CLOSE1 CLOSE2");
    expectedResult = asArray("x OPEN1 y OPEN2 z a CLOSE2 CLOSE1");
    testBalanceTags(targetTokens, expectedResult);

    // closing tag at end
    targetTokens = asArray("x OPEN1 y z a CLOSE1");
    expectedResult = asArray("x OPEN1 y z a CLOSE1");
    testBalanceTags(targetTokens, expectedResult);

    // single overlapping range
    targetTokens = asArray("x OPEN1 y OPEN2 z CLOSE1 a CLOSE2");
    expectedResult = asArray("x OPEN1 y OPEN2 z CLOSE2 CLOSE1 OPEN2 a CLOSE2");
    testBalanceTags(targetTokens, expectedResult);

    // double overlapping range
    targetTokens = asArray("x OPEN1 y OPEN2 z OPEN3 a CLOSE1 b CLOSE2 c CLOSE3");
    expectedResult = asArray(
        "x OPEN1 y OPEN2 z OPEN3 a CLOSE3 CLOSE2 CLOSE1 OPEN2 OPEN3 b "
            + "CLOSE3 CLOSE2 OPEN3 c CLOSE3");
    testBalanceTags(targetTokens, expectedResult);
  }


  private void testBalanceTags(String[] targetTokens, String[] expectedResult) {

    targetTokens = MarkupInserter.balanceTags(tagMap, targetTokens);
    assertThat(targetTokens)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(expectedResult), asString(targetTokens)))
        .containsExactly(expectedResult);
    String xml = asXml(targetTokens, tagMap);
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
   * Test {@link MarkupInserter#mergeNeighborTagPairs(Map, String[])}.
   */
  @Test
  void testMergeNeighborTagPairs() {

    // init variables to be re-used between tests
    String[] targetTokens = null;
    String[] expectedResult = null;

    // one merge
    targetTokens = asArray("x OPEN1 y CLOSE1 OPEN1 z CLOSE1 a b c");
    expectedResult = asArray("x OPEN1 y z CLOSE1 a b c");
    testMergeNeighborTagPairs(targetTokens, expectedResult);

    // one merge with ending tag at end
    targetTokens = asArray("x OPEN1 y CLOSE1 OPEN1 z CLOSE1");
    expectedResult = asArray("x OPEN1 y z CLOSE1");
    testMergeNeighborTagPairs(targetTokens, expectedResult);

    // two merges
    targetTokens = asArray("x OPEN1 y CLOSE1 OPEN1 z CLOSE1 OPEN1 a b CLOSE1 c");
    expectedResult = asArray("x OPEN1 y z a b CLOSE1 c");
    testMergeNeighborTagPairs(targetTokens, expectedResult);

    // mixed tags pairs
    targetTokens = asArray("x OPEN1 y CLOSE1 OPEN1 z CLOSE1 OPEN2 a CLOSE2 OPEN2 b CLOSE2 c");
    expectedResult = asArray("x OPEN1 y z CLOSE1 OPEN2 a b CLOSE2 c");
    testMergeNeighborTagPairs(targetTokens, expectedResult);

    // two nested tag pairs
    targetTokens = asArray("x OPEN1 OPEN2 y CLOSE2 CLOSE1 OPEN1 OPEN2 z CLOSE2 CLOSE1 c");
    expectedResult = asArray("x OPEN1 OPEN2 y z CLOSE2 CLOSE1 c");
    testMergeNeighborTagPairs(targetTokens, expectedResult);

    // three nested tag pairs
    targetTokens = asArray("x OPEN1 OPEN2 OPEN3 y CLOSE3 CLOSE2 CLOSE1 "
        + "OPEN1 OPEN2 OPEN3 z CLOSE3 CLOSE2 CLOSE1 c");
    expectedResult = asArray("x OPEN1 OPEN2 OPEN3 y z CLOSE3 CLOSE2 CLOSE1 c");
    testMergeNeighborTagPairs(targetTokens, expectedResult);
  }


  void testMergeNeighborTagPairs(String[] targetTokens, String[] expectedResult) {

    targetTokens = MarkupInserter.mergeNeighborTagPairs(tagMap, targetTokens);
    assertThat(targetTokens)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(expectedResult), asString(targetTokens)))
        .containsExactly(expectedResult);
  }


  /**
   * Test {@link MarkupInserter#collectUnusedTags(String[], String[])}.
   */
  @Test
  void testCollectUnusedTags() {

    // init variables to be re-used between tests
    String[] sourceTokensWithTags = null;
    String[] targetTokensWithTags = null;
    List<String> unusedTags = null;

    // all tags used
    sourceTokensWithTags = asArray("ISO1 a OPEN1 b CLOSE1 c ISO2");
    targetTokensWithTags = asArray("ISO1 x OPEN1 y CLOSE1 z ISO2");
    unusedTags = MarkupInserter.collectUnusedTags(sourceTokensWithTags, targetTokensWithTags);
    assertThat(unusedTags).isEmpty();

    // all tags used, some multiple times
    sourceTokensWithTags = asArray("ISO1 a OPEN1 b CLOSE1 c ISO2");
    targetTokensWithTags = asArray("ISO1 x OPEN1 y CLOSE1 OPEN1 z CLOSE1 ISO2");
    unusedTags = MarkupInserter.collectUnusedTags(sourceTokensWithTags, targetTokensWithTags);
    assertThat(unusedTags).isEmpty();

    // not all tags used
    sourceTokensWithTags = asArray("ISO1 a OPEN1 b CLOSE1 c ISO2");
    targetTokensWithTags = asArray("ISO1 x OPEN1 y z ISO2");
    unusedTags = MarkupInserter.collectUnusedTags(sourceTokensWithTags, targetTokensWithTags);
    assertThat(unusedTags).containsExactly(CLOSE1);

    // no tags used
    sourceTokensWithTags = asArray("ISO1 a OPEN1 b CLOSE1 c ISO2");
    targetTokensWithTags = asArray("x y z");
    unusedTags = MarkupInserter.collectUnusedTags(sourceTokensWithTags, targetTokensWithTags);
    assertThat(unusedTags).containsExactly(ISO1, OPEN1, CLOSE1, ISO2);
  }


  /**
   * Test {@link MarkupInserter#maskTags(String[])} and
   * {@link MarkupInserter#unmaskTags(String)}.
   */
  @Test
  void testMaskAndUnmaskTags() {

    // init variables to be re-used between tests
    String unmasked = null;
    String masked = null;

    // one tag
    unmasked = "a b c " + OPEN1 + " x y z";
    masked = "a b c x" + OPEN1 + "c x y z";
    assertThat(MarkupInserter.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarkupInserter.unmaskTags(masked)).isEqualTo(unmasked);

    // tag at beginning
    unmasked = OPEN1 + " x y z";
    masked = "x" + OPEN1 + " x y z";
    assertThat(MarkupInserter.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarkupInserter.unmaskTags(masked)).isEqualTo(unmasked);

    // tag at end
    unmasked = "a b c " + OPEN1;
    masked = "a b c " + OPEN1 + "c";
    assertThat(MarkupInserter.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarkupInserter.unmaskTags(masked)).isEqualTo(unmasked);

    // two tags
    unmasked = "a b c " + ISO1 + " " + OPEN1 + " x y z";
    masked = "a b c x" + ISO1 + "c x" + OPEN1 + "c x y z";
    assertThat(MarkupInserter.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarkupInserter.unmaskTags(masked)).isEqualTo(unmasked);

    // two tags at beginning
    unmasked = ISO1 + " " + OPEN1 + " x y z";
    masked = "x" + ISO1 + " x" + OPEN1 + " x y z";
    assertThat(MarkupInserter.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarkupInserter.unmaskTags(masked)).isEqualTo(unmasked);

    // two tags at end
    unmasked = "a b c " + ISO1 + " " + OPEN1;
    masked = "a b c " + ISO1 + "c " + OPEN1 + "c";
    assertThat(MarkupInserter.maskTags(unmasked.split(" "))).isEqualTo(masked);
    assertThat(MarkupInserter.unmaskTags(masked)).isEqualTo(unmasked);
  }


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
    expectedResult = String.format("x y z %sa %sb%s c", ISO1, OPEN1, CLOSE1);
    testDetokenizeTags(input, expectedResult);

    // multiple whitespaces
    input = String.format("x y z %s   a   %s  b   %s  c", ISO1, OPEN1, CLOSE1);
    expectedResult = String.format("x y z %sa   %sb%s  c", ISO1, OPEN1, CLOSE1);
    testDetokenizeTags(input, expectedResult);

    // empty tag pair
    input = String.format("x y z %s a %s %s b", ISO1, OPEN1, CLOSE1);
    expectedResult = String.format("x y z %sa %s%s b", ISO1, OPEN1, CLOSE1);
    testDetokenizeTags(input, expectedResult);
  }


  private void testDetokenizeTags(String input, String expectedResult) {

    input = MarkupInserter.detokenizeTags(input);
    assertThat(input)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            expectedResult, input))
        .isEqualTo(expectedResult);
  }


  /**
   * Test {@link MarkupInserter#createTokenIndex2TagsComplete(String[], Map)}.
   */
  @Test
  void testCreateTokenIndex2TagsComplete() {

    // init variables to be re-used between tests
    String[] tokensWithTags = null;
    Map<Integer, List<String>> index2Tags = null;

    // single tag pair
    tokensWithTags = asArray("start OPEN1 x y CLOSE1 end");
    index2Tags = MarkupInserter.createTokenIndex2TagsComplete(
        new SplitTagsSentence(tokensWithTags, tagMap), tagMap);
    assertThat(index2Tags).hasSize(2);
    assertThat(index2Tags.get(1)).containsExactly(OPEN1, CLOSE1);
    assertThat(index2Tags.get(2)).containsExactly(OPEN1, CLOSE1);

    // two tag pairs
    tokensWithTags = asArray("start OPEN1 x y CLOSE1 OPEN2 a b CLOSE2 end");
    index2Tags = MarkupInserter.createTokenIndex2TagsComplete(
        new SplitTagsSentence(tokensWithTags, tagMap), tagMap);
    assertThat(index2Tags).hasSize(4);
    assertThat(index2Tags.get(1)).containsExactly(OPEN1, CLOSE1);
    assertThat(index2Tags.get(2)).containsExactly(OPEN1, CLOSE1);
    assertThat(index2Tags.get(3)).containsExactly(OPEN2, CLOSE2);
    assertThat(index2Tags.get(4)).containsExactly(OPEN2, CLOSE2);

    // two tag pairs, nested
    tokensWithTags = asArray("start OPEN1 OPEN2 x y CLOSE2 CLOSE1 end");
    index2Tags = MarkupInserter.createTokenIndex2TagsComplete(
        new SplitTagsSentence(tokensWithTags, tagMap), tagMap);
    assertThat(index2Tags).hasSize(2);
    assertThat(index2Tags.get(1)).containsExactly(OPEN1, OPEN2, CLOSE2, CLOSE1);
    assertThat(index2Tags.get(2)).containsExactly(OPEN1, OPEN2, CLOSE2, CLOSE1);

    // single tag pair with ISO at beginning
    tokensWithTags = asArray("start OPEN1 ISO1 x y CLOSE1 end");
    index2Tags = MarkupInserter.createTokenIndex2TagsComplete(
        new SplitTagsSentence(tokensWithTags, tagMap), tagMap);
    assertThat(index2Tags).hasSize(2);
    assertThat(index2Tags.get(1)).containsExactly(OPEN1, ISO1, CLOSE1);
    assertThat(index2Tags.get(2)).containsExactly(OPEN1, CLOSE1);

    // single tag pair with ISO at middle
    tokensWithTags = asArray("start OPEN1 x ISO1 y CLOSE1 end");
    index2Tags = MarkupInserter.createTokenIndex2TagsComplete(
        new SplitTagsSentence(tokensWithTags, tagMap), tagMap);
    assertThat(index2Tags).hasSize(2);
    assertThat(index2Tags.get(1)).containsExactly(OPEN1, CLOSE1);
    assertThat(index2Tags.get(2)).containsExactly(OPEN1, ISO1, CLOSE1);

    // single tag pair with ISO at end
    tokensWithTags = asArray("start OPEN1 x y ISO1 CLOSE1 end");
    index2Tags = MarkupInserter.createTokenIndex2TagsComplete(
        new SplitTagsSentence(tokensWithTags, tagMap), tagMap);
    assertThat(index2Tags).hasSize(3);
    assertThat(index2Tags.get(1)).containsExactly(OPEN1, CLOSE1);
    assertThat(index2Tags.get(2)).containsExactly(OPEN1, CLOSE1);
    assertThat(index2Tags.get(3)).containsExactly(ISO1);

    // single tag pair with two ISOs
    tokensWithTags = asArray("start OPEN1 x ISO1 y ISO2 CLOSE1 end");
    index2Tags = MarkupInserter.createTokenIndex2TagsComplete(
        new SplitTagsSentence(tokensWithTags, tagMap), tagMap);
    assertThat(index2Tags).hasSize(3);
    assertThat(index2Tags.get(1)).containsExactly(OPEN1, CLOSE1);
    assertThat(index2Tags.get(2)).containsExactly(OPEN1, ISO1, CLOSE1);
    assertThat(index2Tags.get(3)).containsExactly(ISO2);
  }


  /**
   * Test {@link MarkupInserter#moveIsoTagsToPointedTokens(Map, List, int)}.
   */
  @Test
  void testMoveIsoTagsToPointedTokens() {

    // init variables to be re-used between tests
    String[] tokensWithTags = null;
    List<Integer> pointedSourceTokens = null;
    Map<Integer, List<String>> sourceTokenIndex2Tags = null;
    List<String> unusedTags = null;

    // two ISO tags, both not pointed to
    tokensWithTags = asArray("x ISO1 y ISO2 z");
    pointedSourceTokens = List.of(1);
    sourceTokenIndex2Tags =
        MarkupInserter.createTokenIndex2Tags(new SplitTagsSentence(tokensWithTags, tagMap));
    unusedTags = MarkupInserter.moveIsoTagsToPointedTokens(
        sourceTokenIndex2Tags, pointedSourceTokens, removeTags(tokensWithTags).length);

    assertThat(unusedTags).containsExactly(ISO2);
    assertThat(sourceTokenIndex2Tags).hasSize(1);
    assertThat(sourceTokenIndex2Tags.get(1)).containsExactly(ISO1);

    // two ISO tags, both pointed to
    tokensWithTags = asArray("x ISO1 y z a ISO2 b");
    pointedSourceTokens = List.of(1, 4);
    sourceTokenIndex2Tags =
        MarkupInserter.createTokenIndex2Tags(new SplitTagsSentence(tokensWithTags, tagMap));
    unusedTags = MarkupInserter.moveIsoTagsToPointedTokens(
        sourceTokenIndex2Tags, pointedSourceTokens, removeTags(tokensWithTags).length);

    assertThat(unusedTags).isEmpty();
    assertThat(sourceTokenIndex2Tags).hasSize(2);
    assertThat(sourceTokenIndex2Tags.get(1)).containsExactly(ISO1);
    assertThat(sourceTokenIndex2Tags.get(4)).containsExactly(ISO2);

    // ISO not pointed to, but following pointed token
    tokensWithTags = asArray("x ISO1 y z");
    pointedSourceTokens = List.of(2);
    sourceTokenIndex2Tags =
        MarkupInserter.createTokenIndex2Tags(new SplitTagsSentence(tokensWithTags, tagMap));
    unusedTags = MarkupInserter.moveIsoTagsToPointedTokens(
        sourceTokenIndex2Tags, pointedSourceTokens, removeTags(tokensWithTags).length);

    assertThat(unusedTags).isEmpty();
    assertThat(sourceTokenIndex2Tags).hasSize(1);
    assertThat(sourceTokenIndex2Tags.get(2)).containsExactly(ISO1);

    // ISO not pointed to, no following pointed token
    tokensWithTags = asArray("x ISO1 y z");
    pointedSourceTokens = List.of(0);
    sourceTokenIndex2Tags =
        MarkupInserter.createTokenIndex2Tags(new SplitTagsSentence(tokensWithTags, tagMap));
    unusedTags = MarkupInserter.moveIsoTagsToPointedTokens(
        sourceTokenIndex2Tags, pointedSourceTokens, removeTags(tokensWithTags).length);

    assertThat(unusedTags).containsExactly(ISO1);
    assertThat(sourceTokenIndex2Tags).isEmpty();
  }


  /**
   * Test
   * {@link MarkupInserter#reinsertTagsComplete(SplitTagsSentence, Map, String[], Alignments, boolean)}
   * with soft alignments.
   */
  @Test
  void testReinsertTagsCompleteWithSoftAlignments() {

    String[] sourceTokens = asArray("ISO1 OPEN1 This CLOSE1 is a OPEN2 test . CLOSE2 ISO2");

    // init variables to be re-used between tests
    String[] targetTokensWithoutTags = null;
    String rawAlignments = null;
    String[] expectedResult = null;

    // parallel alignment
    targetTokensWithoutTags = "Das ist ein Test .".split(" ");
    rawAlignments = ""
        + "1,0,0,0,0,0 " // Das -> This
        + "0,1,0,0,0,0 " // ist -> is
        + "0,0,1,0,0,0 " // ein -> a
        + "0,0,0,1,0,0 " // Test -> test
        + "0,0,0,0,1,0 " // . -> .
        + "0,0,0,0,0,1"; // EOS -> EOS
    expectedResult = asArray("ISO1 OPEN1 Das CLOSE1 ist ein OPEN2 Test CLOSE2 OPEN2 . CLOSE2 ISO2");
    testReinsertTagsCompleteWithSoftAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // reversed alignment
    targetTokensWithoutTags = "Test ein ist das .".split(" ");
    rawAlignments = ""
        + "0,0,0,1,0,0 " // Test -> test
        + "0,0,1,0,0,0 " // ein -> a
        + "0,1,0,0,0,0 " // ist -> is
        + "1,0,0,0,0,0 " // das -> This
        + "0,0,0,0,1,0 " // . -> .
        + "0,0,0,0,0,1"; // EOS -> EOS
    expectedResult = asArray("ISO1 OPEN2 Test CLOSE2 ein ist OPEN1 das CLOSE1 OPEN2 . CLOSE2 ISO2");
    testReinsertTagsCompleteWithSoftAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);
  }


  /**
   * Test
   * {@link MarkupInserter#reinsertTagsComplete(SplitTagsSentence, Map, String[], Alignments, boolean)}
   * with hard alignments.
   */
  @Test
  void testReinsertTagsCompleteWithHardAlignments() {

    // init variables to be re-used between tests
    String[] sourceTokens = null;
    String[] targetTokensWithoutTags = null;
    String rawAlignments = null;
    String[] expectedResult = null;

    // parallel alignment
    sourceTokens = asArray("ISO1 OPEN1 This CLOSE1 is a OPEN2 test . CLOSE2 ISO2");
    targetTokensWithoutTags = asArray("Das ist ein Test .");
    rawAlignments = "0-0 1-1 2-2 3-3 4-4 5-5";
    expectedResult = asArray("ISO1 OPEN1 Das CLOSE1 ist ein OPEN2 Test CLOSE2 OPEN2 . CLOSE2 ISO2");
    testReinsertTagsCompleteWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // reversed alignment
    sourceTokens = asArray("ISO1 OPEN1 This CLOSE1 is a OPEN2 test . CLOSE2 ISO2");
    targetTokensWithoutTags = asArray("Test ein ist das .");
    //                                 This is  a   Test .
    rawAlignments = "0-3 1-2 2-1 3-0 4-4 5-5";
    expectedResult = asArray("ISO1 OPEN2 Test CLOSE2 ein ist OPEN1 das CLOSE1 OPEN2 . CLOSE2 ISO2");
    testReinsertTagsCompleteWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // end-of-sentence points to source token with ISO tag
    sourceTokens = asArray("ISO1 OPEN1 Zum Inhalt ISO2 springen CLOSE1 end");
    targetTokensWithoutTags = asArray("aller au contenu");
    rawAlignments = "0-0 1-2 2-3";
    expectedResult = asArray("ISO1 OPEN1 aller CLOSE1 au OPEN1 contenu CLOSE1 ISO2");
    testReinsertTagsCompleteWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // tags enclosing the whole sentence
    sourceTokens = asArray("ISO1 OPEN1 a b c d CLOSE1 ISO2");
    targetTokensWithoutTags = asArray("b a d c");
    rawAlignments = "0-1 1-0 2-3 4-2";
    expectedResult = asArray("ISO1 OPEN1 b a d c CLOSE1 ISO2");
    testReinsertTagsCompleteWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // tag pair over the whole sentence
    sourceTokens = asArray("OPEN1 a b c d CLOSE1");
    targetTokensWithoutTags = asArray("b a d c");
    rawAlignments = "0-1 1-0 2-3 4-2";
    expectedResult = asArray("OPEN1 b a d c CLOSE1");
    testReinsertTagsCompleteWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // multiple tag pairs over the whole sentence
    sourceTokens = asArray("OPEN1 OPEN2 OPEN3 a b c d CLOSE3 CLOSE2 CLOSE1");
    targetTokensWithoutTags = asArray("b a d c");
    rawAlignments = "0-1 1-0 2-3 4-2";
    expectedResult = asArray("OPEN1 OPEN2 OPEN3 b a d c CLOSE3 CLOSE2 CLOSE1");
    testReinsertTagsCompleteWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // tokens with isolated tags are pointed to multiple times
    sourceTokens = asArray("x ISO1 ISO2 OPEN1 y z CLOSE1");
    targetTokensWithoutTags = asArray("a b c");
    rawAlignments = "1-0 1-1 2-2";
    expectedResult = asArray("ISO1 ISO2 OPEN1 a CLOSE1 OPEN1 b CLOSE1 OPEN1 c CLOSE1");
    testReinsertTagsCompleteWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // one target token to multiple source tokens with same tags
    sourceTokens = asArray("x ISO1 ISO2 OPEN1 y z CLOSE1");
    targetTokensWithoutTags = asArray("a b c");
    rawAlignments = "1-0 2-0 2-2";
    expectedResult = asArray("ISO1 ISO2 OPEN1 a CLOSE1 b OPEN1 c CLOSE1");
    testReinsertTagsCompleteWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // single tag pair with ISO at end
    sourceTokens = asArray("x OPEN1 y z ISO1 CLOSE1");
    targetTokensWithoutTags = asArray("a b c");
    rawAlignments = "0-0 1-1 2-2";
    expectedResult = asArray("a OPEN1 b CLOSE1 OPEN1 c CLOSE1 ISO1");
    testReinsertTagsCompleteWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // single tag pair with ISO at beginning
    sourceTokens = asArray("ISO1 OPEN1 x y CLOSE1 z");
    targetTokensWithoutTags = asArray("a b c");
    rawAlignments = "0-0 1-1 2-2";
    expectedResult = asArray("ISO1 OPEN1 a CLOSE1 OPEN1 b CLOSE1 c");
    testReinsertTagsCompleteWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);
  }


  /**
   * Test
   * {@link MarkupInserter#reinsertTagsComplete(SplitTagsSentence, Map, String[], Alignments, boolean)}
   * with more complex examples.
   */
  @Test
  void testReinsertTagsCompleteComplex() {

    String[] sourceTokens = asArray("OPEN1 x y z CLOSE1 a b c");

    // init variables to be re-used between tests
    String[] targetTokensWithoutTags = null;
    String rawAlignments = null;
    String[] expectedResult = null;

    // first test
    targetTokensWithoutTags = asArray("X1 N Z X2 N N");
    rawAlignments = "0-0 0-3 2-2";
    expectedResult = asArray("OPEN1 X1 CLOSE1 N OPEN1 Z CLOSE1 OPEN1 X2 CLOSE1 N N");
    testReinsertTagsCompleteWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // second test
    targetTokensWithoutTags = asArray("Z1 Z2 X N N N");
    rawAlignments = "0-2 2-0 2-1";
    expectedResult = asArray("OPEN1 Z1 CLOSE1 OPEN1 Z2 CLOSE1 OPEN1 X CLOSE1 N N N");
    testReinsertTagsCompleteWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);

    // third test
    targetTokensWithoutTags = asArray("Z1 N X1 Z2 N X2");
    rawAlignments = "0-2 0-5 2-0 2-3";
    expectedResult = asArray("OPEN1 Z1 CLOSE1 N OPEN1 X1 CLOSE1 OPEN1 Z2 CLOSE1 N OPEN1 X2 CLOSE1");
    testReinsertTagsCompleteWithHardAlignments(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult);
  }


  private void testReinsertTagsCompleteWithSoftAlignments(
      String[] sourceTokens, String[] targetTokensWithoutTags, String rawAlignments,
      String[] expectedResult) {

    testReinsertTagsComplete(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult, false);
  }


  private void testReinsertTagsCompleteWithHardAlignments(
      String[] sourceTokens, String[] targetTokensWithoutTags, String rawAlignments,
      String[] expectedResult) {

    testReinsertTagsComplete(
        sourceTokens, targetTokensWithoutTags, rawAlignments, expectedResult, true);
  }


  private void testReinsertTagsComplete(
      String[] sourceTokens, String[] targetTokensWithoutTags, String rawAlignments,
      String[] expectedResult, boolean hardAlignments) {

    Alignments algn = null;
    if (hardAlignments) {
      algn = new HardAlignments(rawAlignments);
    } else {
      algn = new SoftAlignments(rawAlignments);
    }

    SplitTagsSentence sourceSentence = new SplitTagsSentence(sourceTokens, tagMap);
    Map<Integer, List<String>> sourceTokenIndex2tags =
        MarkupInserter.createTokenIndex2TagsComplete(sourceSentence, tagMap);

    String[] targetTokensWithTags =
        MarkupInserter.reinsertTagsComplete(
            sourceSentence, sourceTokenIndex2tags, targetTokensWithoutTags, algn);
    assertThat(targetTokensWithTags)
        // provide human-readable string in case of error
        .as(String.format("%nexpected: %s%nactual: %s",
            asString(expectedResult), asString(targetTokensWithTags)))
        .containsExactly(expectedResult);
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

    List<String> baseTokens = List.of("x", "y@@", "z", "a", "b@@", "c@@", "i", "j", "k");
    List<String> tags = List.of(ISO1, ISO2, OPEN1, CLOSE1, OPEN2, CLOSE2, OPEN3, CLOSE3);

    // randomly insert tags in tokens
    Random random = new Random(System.currentTimeMillis());

    int run = 0;
    while (true) {
      run++;
      if (run % 100_000 == 0) {
        System.out.println(String.format("run: %,d", run));
      }
      for (int numberOfTags = 1; numberOfTags <= 20; numberOfTags++) {
        List<String> tokens = new ArrayList<>(baseTokens);
        List<String> tagsToInsert = new ArrayList<>();
        for (int i = 0; i < numberOfTags; i++) {
          tagsToInsert.add(tags.get(random.nextInt(tags.size())));
        }
        for (String oneTag : tagsToInsert) {
          tokens.add(random.nextInt(tokens.size() + 1), oneTag);
        }
        String[] input = tokens.toArray(new String[tokens.size()]);
        String[] moveTagsFromBetweenBpeFragments =
            MarkupInserter.moveTagsFromBetweenBpeFragments(input, tagMap);
        String[] undoBytePairEncoding =
            MarkupInserter.undoBytePairEncoding(moveTagsFromBetweenBpeFragments);
        String[] handleInvertedTags =
            MarkupInserter.handleInvertedTags(tagMap, undoBytePairEncoding);
        String[] removeRedundantTags =
            MarkupInserter.removeRedundantTags(tagMap, handleInvertedTags);
        String[] balanceTags =
            MarkupInserter.balanceTags(tagMap, removeRedundantTags);
        String[] mergeNeighborTagPairs =
            MarkupInserter.mergeNeighborTagPairs(tagMap, balanceTags);
        String xml = asXml(mergeNeighborTagPairs, tagMap);
        if (!isValidXml(xml)) {
          System.err.println(
              String.format("input:                           %s",
                  asString(input)));
          System.err.println(
              String.format("moveTagsFromBetweenBpeFragments: %s",
                  asString(moveTagsFromBetweenBpeFragments)));
          System.err.println(
              String.format("undoBytePairEncoding:            %s",
                  asString(undoBytePairEncoding)));
          System.err.println(
              String.format("handleInvertedTags:              %s",
                  asString(handleInvertedTags)));
          System.err.println(
              String.format("removeRedundantTags:             %s",
                  asString(removeRedundantTags)));
          System.err.println(
              String.format("balanceTags:                     %s",
                  asString(balanceTags)));
          System.err.println(
              String.format("mergeNeighborTagPairs:           %s",
                  asString(mergeNeighborTagPairs)));
          System.err.println(String.format("xml:%n%s", xml));
        }
      }
    }
  }
}
