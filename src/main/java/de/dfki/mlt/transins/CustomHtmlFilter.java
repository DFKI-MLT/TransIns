package de.dfki.mlt.transins;

import net.sf.okapi.common.skeleton.ISkeletonWriter;
import net.sf.okapi.filters.html.HtmlFilter;
import net.sf.okapi.filters.html.HtmlSkeletonWriter;

/**
 * A custom Okapi HTML filter that make an unlimited number of referent copies available
 * in the HtmlSkeletonWriter.
 *
 * @author Jörg Steffen, DFKI
 */
public class CustomHtmlFilter extends HtmlFilter {

  @Override
  public ISkeletonWriter createSkeletonWriter() {

    HtmlSkeletonWriter htmlSkeletonWriter = new HtmlSkeletonWriter();
    htmlSkeletonWriter.setReferentCopies(Integer.MAX_VALUE);
    return htmlSkeletonWriter;
  }
}
