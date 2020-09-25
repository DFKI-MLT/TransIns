package de.dfki.mlt.transins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.Test;
import static de.dfki.mlt.transins.TestUtils.CLOSE1;
import static de.dfki.mlt.transins.TestUtils.CLOSE2;
import static de.dfki.mlt.transins.TestUtils.CLOSE3;
import static de.dfki.mlt.transins.TestUtils.OPEN1;
import static de.dfki.mlt.transins.TestUtils.OPEN2;
import static de.dfki.mlt.transins.TestUtils.OPEN3;

/**
 * Test class for {@link TagMap}.
 *
 * @author Jörg Steffen, DFKI
 */
public class TagMapTest {


  /**
   * Test {@link TagMap#getOpeningTag(String)} and {@link TagMap#getClosingTag(String)}.
   */
  @Test
  void testTagMap() {

    TagMap tagMap = new TagMap();
    tagMap.put(OPEN1, CLOSE1);
    tagMap.put(OPEN2, CLOSE2);
    tagMap.put(OPEN3, CLOSE3);

    assertThat(tagMap.getClosingTag(OPEN1)).isEqualTo(CLOSE1);
    assertThat(tagMap.getClosingTag(OPEN2)).isEqualTo(CLOSE2);
    assertThat(tagMap.getClosingTag(OPEN3)).isEqualTo(CLOSE3);

    assertThat(tagMap.getOpeningTag(CLOSE1)).isEqualTo(OPEN1);
    assertThat(tagMap.getOpeningTag(CLOSE2)).isEqualTo(OPEN2);
    assertThat(tagMap.getOpeningTag(CLOSE3)).isEqualTo(OPEN3);

    assertThat(tagMap.entrySet()).hasSize(3);
    assertThat(tagMap.entrySet()).contains(
        entry(OPEN1, CLOSE1), entry(OPEN2, CLOSE2), entry(OPEN3, CLOSE3));
  }
}
