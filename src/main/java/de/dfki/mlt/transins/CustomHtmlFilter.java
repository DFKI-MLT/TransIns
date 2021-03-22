package de.dfki.mlt.transins;

import net.sf.okapi.common.MimeTypeMapper;
import net.sf.okapi.common.filters.FilterConfiguration;
import net.sf.okapi.common.skeleton.ISkeletonWriter;
import net.sf.okapi.filters.html.HtmlFilter;
import net.sf.okapi.filters.html.HtmlSkeletonWriter;

/**
 * A custom Okapi HTML filter that make an unlimited number of referent copies available
 * in the HtmlSkeletonWriter. Also add custom configurations.
 *
 * @author Jörg Steffen, DFKI
 */
public class CustomHtmlFilter extends HtmlFilter {

  /**
   * Create a new custom html filter with custom configurations.
   */
  public CustomHtmlFilter() {

    super();
    addConfiguration(new FilterConfiguration("okf_html_custom",
        MimeTypeMapper.HTML_MIME_TYPE, getClass().getName(),
        "HTML", "HTML or XHTML documents",
        "/okapi/okf_html_nonwellformedConfiguration.yml", ".html;.htm;"));
    addConfiguration(new FilterConfiguration("okf_html-wellFormed_custom",
        MimeTypeMapper.XHTML_MIME_TYPE, getClass().getName(),
        "HTML (Well-Formed)", "XHTML and well-formed HTML documents",
        "/okapi/okf_html_wellformedConfiguration.yml"));
  }


  @Override
  public ISkeletonWriter createSkeletonWriter() {

    HtmlSkeletonWriter htmlSkeletonWriter = new HtmlSkeletonWriter();
    htmlSkeletonWriter.setReferentCopies(Integer.MAX_VALUE);
    return htmlSkeletonWriter;
  }
}
