package de.dfki.mlt.transins;
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

/**
 * Demo step that applies uppercasing to all content.
 *
 * @author Jörg Steffen, DFKI
 */
public class UppercaseStep extends BasePipelineStep {

  private LocaleId trgLoc;


  /**
   * Set the given target locale.
   *
   * @param targetLocale
   *          the target locale
   */
  @StepParameterMapping(parameterType = StepParameterType.TARGET_LOCALE)
  public void setTargetLocale(LocaleId targetLocale) {

    this.trgLoc = targetLocale;
  }


  @Override
  public String getName() {

    return "Uppercase";
  }


  @Override
  public String getDescription() {

    return "Converts text units content to upper cases.";
  }


  @Override
  protected Event handleTextUnit(Event event) {

    ITextUnit tu = event.getTextUnit();
    if (tu.isTranslatable()) {
      TextContainer tc = tu.createTarget(this.trgLoc, false, IResource.COPY_SEGMENTED_CONTENT);
      for (Segment seg : tc.getSegments()) {
        TextFragment tf = seg.getContent();
        tf.setCodedText(tf.getCodedText().toUpperCase());
      }
    }
    return event;
  }
}
