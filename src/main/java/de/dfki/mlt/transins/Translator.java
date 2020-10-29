package de.dfki.mlt.transins;

import java.io.File;
import java.net.URI;
import java.util.Hashtable;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.MimeTypeMapper;
import net.sf.okapi.common.Util;
import net.sf.okapi.common.exceptions.OkapiException;
import net.sf.okapi.common.filters.FilterConfigurationMapper;
import net.sf.okapi.common.filters.IFilterConfigurationMapper;
import net.sf.okapi.common.pipelinedriver.PipelineDriver;
import net.sf.okapi.common.resource.RawDocument;
import net.sf.okapi.connectors.apertium.ApertiumMTConnector;
import net.sf.okapi.connectors.microsoft.MicrosoftMTConnector;
import net.sf.okapi.filters.openoffice.OpenOfficeFilter;
import net.sf.okapi.filters.openxml.OpenXMLFilter;
import net.sf.okapi.steps.common.FilterEventsToRawDocumentStep;
import net.sf.okapi.steps.common.RawDocumentToFilterEventsStep;
import net.sf.okapi.steps.leveraging.LeveragingStep;
import net.sf.okapi.steps.segmentation.SegmentationStep;

/**
 * Translator creates an Okapi pipeline that extracts content from supported documents formats,
 * feeds them to supported translation engines and re-inserts the translation into the original
 * document format.
 *
 * @author Jörg Steffen, DFKI
 */
public class Translator {

  /** supported translation engines */
  public enum TransId {

    /** Microsoft Azure translation engine */
    MICROSOFT,

    /** Apertium */
    APERTIUM,

    /** Marian NMT server */
    MARIAN,

    /** Marian NMT server with batch runner */
    MARIAN_BATCH,

    /** dummy that just uppercases all content */
    UPPERCASE_DUMMY
  }

  private static final Logger logger = LoggerFactory.getLogger(Translator.class);

  private IFilterConfigurationMapper fcMapper;
  private Map<String, String> extensionsMap;


  /**
   * Create a new translator instance.
   */
  public Translator() {

    // init filter configuration mapper
    this.fcMapper = new FilterConfigurationMapper();
    // add filters for OpenXML, OpenOffice and HTML
    this.fcMapper.addConfigurations(OpenXMLFilter.class.getName());
    this.fcMapper.addConfigurations(OpenOfficeFilter.class.getName());
    this.fcMapper.addConfigurations(CustomHtmlFilter.class.getName());

    // init extension map
    this.extensionsMap = new Hashtable<>();

    this.extensionsMap.put("docx", "okf_openxml");
    this.extensionsMap.put("pptx", "okf_openxml");
    this.extensionsMap.put("xlsx", "okf_openxml");

    this.extensionsMap.put("odt", "okf_openoffice");
    this.extensionsMap.put("swx", "okf_openoffice");
    this.extensionsMap.put("ods", "okf_openoffice");
    this.extensionsMap.put("swc", "okf_openoffice");
    this.extensionsMap.put("odp", "okf_openoffice");
    this.extensionsMap.put("sxi", "okf_openoffice");
    this.extensionsMap.put("odg", "okf_openoffice");
    this.extensionsMap.put("sxd", "okf_openoffice");

    this.extensionsMap.put("htm", "okf_html");
    this.extensionsMap.put("html", "okf_html");
  }


  /**
   * Translate source document from the given source language to the given target language using the
   * translator with the given id.
   *
   * @param sourceFileName
   *          the source document file name
   * @param sourceLang
   *          the source language
   * @param sourceEnc
   *          the source document encoding
   * @param targetFileName
   *          the target document file name
   * @param targetLang
   *          the target language
   * @param targetEnc
   *          the target document encoding
   * @param translatorId
   *          the translator id
   * @param applySegmentation
   *          add segmentation when {@code true}
   */
  public void translate(
      String sourceFileName, String sourceLang, String sourceEnc,
      String targetFileName, String targetLang, String targetEnc,
      TransId translatorId, boolean applySegmentation) {

    // get file extension
    String ext = Util.getExtension(sourceFileName);
    // remove dot from extension
    ext = ext.substring(1);
    if (Util.isEmpty(ext)) {
      throw new OkapiException(
          String.format("No file extension detected in \"%s\".", sourceFileName));
    }
    // get configuration id for extension
    String configId = this.extensionsMap.get(ext);
    if (configId == null) {
      throw new OkapiException(String.format(
          "Could not guess the configuration for the extension '%s'", ext));
    }
    // get MIME type for extension
    String mimeType = MimeTypeMapper.getMimeType(ext);

    // parameter summary
    logger.info("             source file: {}", sourceFileName);
    logger.info("         source language: {}", sourceLang);
    logger.info("         source encoding: {}", sourceEnc);
    logger.info("             target file: {}", targetFileName);
    logger.info("         target language: {}", targetLang);
    logger.info("         target encoding: {}", targetEnc);
    logger.info("           translator id: {}", translatorId);
    logger.info("      MIME type detected: {}", mimeType);
    logger.info("  configuration detected: {}", configId);

    try (RawDocument rawDoc =
        new RawDocument(
            new File(sourceFileName).toURI(),
            sourceEnc,
            LocaleId.fromString(sourceLang),
            LocaleId.fromString(targetLang))) {

      rawDoc.setFilterConfigId(configId);

      if (translatorId == TransId.MARIAN_BATCH) {
        runMarianNmtBatch(rawDoc, sourceLang, targetLang, applySegmentation);
      }

      String docId = rawDoc.hashCode() + "";

      // create the driver
      PipelineDriver driver = new PipelineDriver();
      driver.setFilterConfigurationMapper(this.fcMapper);
      driver.setRootDirectories(
          System.getProperty("user.dir"),
          Util.getDirectoryName(rawDoc.getInputURI().getPath()));

      // raw document to filter events step
      driver.addStep(new RawDocumentToFilterEventsStep());

      // add segmentation step (optional)
      if (applySegmentation) {
        driver.addStep(createSeqmentationStep());
      }

      // add leveraging step for selected translator
      switch (translatorId) {
        case MICROSOFT:
          driver.addStep(createMicrosoftLeveragingStep("src/main/resources/msConfig.cfg"));
          break;
        case APERTIUM:
          driver.addStep(createApertiumLeveragingStep("src/main/resources/apertiumConfig.cfg"));
          break;
        case MARIAN:
        case MARIAN_BATCH:
          driver.addStep(createMarianLeveragingStep(
              "src/main/resources/marianConfig.cfg", docId));
          break;
        case UPPERCASE_DUMMY:
          driver.addStep(new UppercaseStep());
          break;
        default:
          logger.error("unknown translator id \"{}\"", translatorId);
          return;
      }

      // filter events to raw document final step
      driver.addStep(new FilterEventsToRawDocumentStep());

      driver.addBatchItem(rawDoc, new File(targetFileName).toURI(), targetEnc);

      // process
      driver.processBatch();

      if (translatorId == TransId.MARIAN_BATCH) {
        // remove processed text fragments for this document from batch runner
        BatchRunner.INSTANCE.clear(docId);
      }
    }
  }


  /**
   * Collect text fragments and batch process them.
   *
   * @param rawDoc
   *          the raw document
   * @param sourceLang
   *          the source language
   * @param targetLang
   *          the target language
   * @param applySegmentation
   *          add segmentation when {@code true}
   */
  private void runMarianNmtBatch(
      RawDocument rawDoc, String sourceLang, String targetLang, boolean applySegmentation) {

    String docId = rawDoc.hashCode() + "";

    // create the driver
    PipelineDriver driver = new PipelineDriver();
    driver.setFilterConfigurationMapper(this.fcMapper);
    driver.setRootDirectories(
        System.getProperty("user.dir"),
        Util.getDirectoryName(rawDoc.getInputURI().getPath()));

    // raw document to filter events step
    driver.addStep(new RawDocumentToFilterEventsStep());

    // add segmentation step (optional)
    if (applySegmentation) {
      driver.addStep(createSeqmentationStep());
    }

    // collect all text fragments for batch processor
    driver.addStep(new TextFragmentsCollector(docId));

    // add document to Okapi pipeline for processing
    driver.addBatchItem(rawDoc);

    // process
    driver.processBatch();

    // batch process text fragments collected in the pipeline above;
    // use parameters of MarianNmtConnector
    URI paramUri = new File("src/main/resources/marianConfig.cfg").toURI();
    MarianNmtParameters translatorParams = new MarianNmtParameters();
    translatorParams.load(Util.URItoURL(paramUri), false);
    BatchRunner.INSTANCE.processBatch(
        docId,
        translatorParams.getTranslationUrl(),
        translatorParams.getPrePostHost(),
        translatorParams.getPrePostPort(),
        sourceLang, targetLang);
    logger.debug(BatchRunner.INSTANCE.getStats(docId));
  }


  /**
   * @return default segmenter for western languages
   */
  private SegmentationStep createSeqmentationStep() {

    SegmentationStep segStep = new SegmentationStep();

    net.sf.okapi.steps.segmentation.Parameters segParams =
        (net.sf.okapi.steps.segmentation.Parameters)segStep.getParameters();
    segParams.setSegmentSource(true);
    segParams.setSegmentTarget(true);
    File segRules = new File("src/main/resources/defaultSegmentation.srx");
    segParams.setSourceSrxPath(segRules.getAbsolutePath());
    segParams.setTargetSrxPath(segRules.getAbsolutePath());
    segParams.setCopySource(true);

    return segStep;
  }


  /**
   * Create leveraging step using Microsoft translator.
   *
   * @param translatorConfig
   *          the translator configuration
   * @return the leveraging step
   */
  private LeveragingStep createMicrosoftLeveragingStep(String translatorConfig) {

    LeveragingStep levStep = new LeveragingStep();

    net.sf.okapi.steps.leveraging.Parameters levParams =
        (net.sf.okapi.steps.leveraging.Parameters)levStep.getParameters();
    levParams.setResourceClassName(MicrosoftMTConnector.class.getName());

    net.sf.okapi.connectors.microsoft.Parameters resourceParams =
        new net.sf.okapi.connectors.microsoft.Parameters();

    // use the specified parameters if available, otherwise use the default
    if (translatorConfig != null) {
      URI paramUri = new File(translatorConfig).toURI();
      resourceParams.load(Util.URItoURL(paramUri), false);
    }
    levParams.setResourceParameters(resourceParams.toString());
    levParams.setFillTarget(true);

    return levStep;
  }


  /**
   * Create leveraging step using Apertium translator.
   *
   * @param translatorConfig
   *          the translator configuration
   * @return the leveraging step
   */
  private LeveragingStep createApertiumLeveragingStep(String translatorConfig) {

    LeveragingStep levStep = new LeveragingStep();

    net.sf.okapi.steps.leveraging.Parameters levParams =
        (net.sf.okapi.steps.leveraging.Parameters)levStep.getParameters();
    levParams.setResourceClassName(ApertiumMTConnector.class.getName());

    net.sf.okapi.connectors.apertium.Parameters resourceParams =
        new net.sf.okapi.connectors.apertium.Parameters();

    // use the specified parameters if available, otherwise use the default
    if (translatorConfig != null) {
      URI paramUri = new File(translatorConfig).toURI();
      resourceParams.load(Util.URItoURL(paramUri), false);
    }
    levParams.setResourceParameters(resourceParams.toString());
    levParams.setFillTarget(true);

    return levStep;
  }


  /**
   * Create leveraging step using Marian translator.
   *
   * @param translatorConfig
   *          the translator configuration
   * @param docId
   *          the document id of the document to translate; use hash code of raw document
   * @return the leveraging step
   */
  private LeveragingStep createMarianLeveragingStep(String translatorConfig, String docId) {

    LeveragingStep levStep = new LeveragingStep();

    net.sf.okapi.steps.leveraging.Parameters levParams =
        (net.sf.okapi.steps.leveraging.Parameters)levStep.getParameters();
    levParams.setResourceClassName(MarianNmtConnector.class.getName());

    MarianNmtParameters resourceParams = new MarianNmtParameters();

    // use the specified parameters if available, otherwise use the default
    if (translatorConfig != null) {
      URI paramUri = new File(translatorConfig).toURI();
      resourceParams.load(Util.URItoURL(paramUri), false);
    }
    resourceParams.setDocumentId(docId);
    levParams.setResourceParameters(resourceParams.toString());
    levParams.setFillTarget(true);

    return levStep;
  }
}
