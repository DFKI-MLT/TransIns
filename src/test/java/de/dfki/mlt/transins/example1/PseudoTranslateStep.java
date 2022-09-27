package de.dfki.mlt.transins.example1;

/*===========================================================================
  Copyright (C) 2009-2014 by the Okapi Framework contributors
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

import net.sf.okapi.common.Event;
import net.sf.okapi.common.IResource;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.pipeline.BasePipelineStep;
import net.sf.okapi.common.pipeline.annotations.StepParameterMapping;
import net.sf.okapi.common.pipeline.annotations.StepParameterType;
import net.sf.okapi.common.resource.ITextUnit;
import net.sf.okapi.common.resource.Segment;
import net.sf.okapi.common.resource.TextContainer;
import net.sf.okapi.common.resource.TextFragment;

@SuppressWarnings("all")
public class PseudoTranslateStep extends BasePipelineStep {

  private static final String OLDCHARS = "AaEeIiOoUuYyCcDdNn";
  private static final String NEWCHARS =
      "\u00c2\u00e5\u00c9\u00e8\u00cf\u00ec\u00d8\u00f5\u00db\u00fc\u00dd\u00ff\u00c7\u00e7\u00d0\u00f0\u00d1\u00f1";

  private LocaleId trgLoc;


  @SuppressWarnings("deprecation")
  @StepParameterMapping(parameterType = StepParameterType.TARGET_LOCALE)
  public void setTargetLocale(LocaleId targetLocale) {

    trgLoc = targetLocale;
  }


  public String getName() {

    return "Pseudo-Translation";
  }


  public String getDescription() {

    return "Pseudo-translates text units content.";
  }


  @Override
  protected Event handleTextUnit(Event event) {

    ITextUnit tu = event.getTextUnit();
    if (!tu.isTranslatable())
      return event;

    TextContainer tc = tu.createTarget(trgLoc, false, IResource.COPY_SEGMENTED_CONTENT);
    // Process each segment content
    for (Segment seg : tc.getSegments()) {
      TextFragment tf = seg.getContent();
      StringBuilder text = new StringBuilder(tf.getCodedText());
      int n;
      for (int i = 0; i < text.length(); i++) {
        if (TextFragment.isMarker(text.charAt(i))) {
          i++; // Skip the pair
        } else {
          if ((n = OLDCHARS.indexOf(text.charAt(i))) > -1) {
            text.setCharAt(i, NEWCHARS.charAt(n));
          }
        }
      }
      tf.setCodedText(text.toString());
    }

    return event;
  }
}
