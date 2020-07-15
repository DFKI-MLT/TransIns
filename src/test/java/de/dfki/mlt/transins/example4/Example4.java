package de.dfki.mlt.transins.example4;
/*===========================================================================
  Copyright (C) 2009-2010 by the Okapi Framework contributors
-----------------------------------------------------------------------------
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
===========================================================================*/

import java.io.File;

import net.sf.okapi.common.ISegmenter;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.Range;
import net.sf.okapi.common.resource.ISegments;
import net.sf.okapi.common.resource.Segment;
import net.sf.okapi.common.resource.TextContainer;
import net.sf.okapi.common.resource.TextFragment;
import net.sf.okapi.common.resource.TextFragment.TagType;
import net.sf.okapi.lib.segmentation.SRXDocument;

/**
 * Example 4 from net.sf.okapi.examples
 *
 * @author Jörg Steffen, DFKI
 */
public final class Example4 {

  private Example4() {

    // private constructor to enforce noninstantiability
  }


  /**
   * @param args
   *          the arguments; not used here
   */
  public static void main(String[] args) {

    // Create and load the SRX document
    SRXDocument doc = new SRXDocument();
    File file = new File("src/test/resources/examples/myRules.srx");
    doc.loadRules(file.getAbsolutePath());

    // Obtain a segmenter for English
    ISegmenter segmenter = doc.compileLanguageRules(LocaleId.fromString("en"), null);

    // Plain text case
    int count = segmenter.computeSegments("Part 1. Part 2.");
    System.out.println("count=" + count);
    for (Range range : segmenter.getRanges()) {
      System.out.println(String.format("start=%d, end=%d",
          range.start, range.end));
    }

    // TextContainer case
    TextFragment tf = new TextFragment();
    tf.append(TagType.OPENING, "span", "<span>");
    tf.append("Part 1.");
    tf.append(TagType.CLOSING, "span", "</span>");
    tf.append(" Part 2.");
    tf.append(TagType.PLACEHOLDER, "alone", "<alone/>");
    tf.append(" Part 3.");
    TextContainer tc = new TextContainer(tf);
    segmenter.computeSegments(tc);
    ISegments segs = tc.getSegments();
    segs.create(segmenter.getRanges());
    for (Segment seg : segs) {
      System.out.println("segment=[" + seg.toString() + "]");
    }
  }
}
