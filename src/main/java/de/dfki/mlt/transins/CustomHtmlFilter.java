package de.dfki.mlt.transins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.htmlparser.jericho.Source;
import net.sf.okapi.common.MimeTypeMapper;
import net.sf.okapi.common.filters.FilterConfiguration;
import net.sf.okapi.common.resource.RawDocument;
import net.sf.okapi.common.skeleton.ISkeletonWriter;
import net.sf.okapi.filters.html.HtmlFilter;
import net.sf.okapi.filters.html.HtmlSkeletonWriter;

/**
 * A custom Okapi HTML filter that make an unlimited number of referent copies available
 * in the HtmlSkeletonWriter. Also add custom configurations and fixes an Okapi issue where
 * the detected encoding is not used by Okapi.
 *
 * @author Jörg Steffen, DFKI
 */
public class CustomHtmlFilter extends HtmlFilter {

  private static final Logger logger = LoggerFactory.getLogger(CustomHtmlFilter.class);


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


  @Override
  public void open(RawDocument input, boolean generateSkeleton) {

    // explicitly set detected encoding as encoding of input raw document;
    // otherwise, for unknown reasons, Okapi still uses the user provided
    // (and potentially wrong) encoding originally set in the input raw document
    Source parsedHeader = getParsedHeader(input.getStream());
    String detectedEncoding = parsedHeader.getDocumentSpecifiedEncoding();
    if (detectedEncoding != null && !detectedEncoding.equalsIgnoreCase(input.getEncoding())) {
      logger.info("correcting document encoding from {} to {}",
          input.getEncoding(), detectedEncoding);
      input.setEncoding(detectedEncoding);
    }

    super.open(input, generateSkeleton);
  }
}
