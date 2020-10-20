package de.dfki.mlt.transins;

import java.util.List;

import net.sf.okapi.common.Event;
import net.sf.okapi.common.pipeline.BasePipelineStep;
import net.sf.okapi.common.resource.ISegments;
import net.sf.okapi.common.resource.TextFragment;

/**
 * Collect text fragments that actually contain text and add them to batch processor input.
 *
 * @author Jörg Steffen, DFKI
 */
public class TextFragmentsCollector extends BasePipelineStep {

  private List<TextFragment> batchInput;


  /**
   * Create a new text fragments collector for the given document id.
   *
   * @param docId
   *          the document id
   */
  public TextFragmentsCollector(String docId) {

    this.batchInput = BatchRunner.INSTANCE.getBatchInput(docId);
  }


  @Override
  public String getName() {

    return "text fragments collector";
  }


  @Override
  public String getDescription() {

    return "Collect all text fragments and add them to batch processor input";
  }


  @Override
  protected Event handleTextUnit(Event event) {

    ISegments sourceSegments = event.getTextUnit().getSourceSegments();
    for (var seg : sourceSegments) {
      // only collect text fragments that actually have text
      if (seg.text.hasText(false)) {
        this.batchInput.add(seg.text);
      }
    }
    return event;
  }
}
