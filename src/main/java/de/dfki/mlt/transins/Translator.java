package de.dfki.mlt.transins;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dfki.mlt.transins.MarkupInserter.MarkupStrategy;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.MimeTypeMapper;
import net.sf.okapi.common.StreamUtil;
import net.sf.okapi.common.Util;
import net.sf.okapi.common.exceptions.OkapiException;
import net.sf.okapi.common.filters.FilterConfigurationMapper;
import net.sf.okapi.common.filters.IFilterConfigurationMapper;
import net.sf.okapi.common.io.FileCachedInputStream;
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
   * @return set of supported file extensions
   */
  public Set<String> getSupportedExtensions() {

    return this.extensionsMap.keySet();
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
   * @param markupStrategy
   *          the markup re-insertion strategy to use; only applies when using Marian NMT
   * @param applySegmentation
   *          add segmentation when {@code true}
   */
  public void translate(
      String sourceFileName, String sourceLang, String sourceEnc,
      String targetFileName, String targetLang, String targetEnc,
      TransId translatorId, MarkupStrategy markupStrategy, boolean applySegmentation) {

    // get file extension
    String ext = Util.getExtension(sourceFileName);
    // remove dot from extension
    if (ext.length() > 0) {
      ext = ext.substring(1);
    }
    if (Util.isEmpty(ext)) {
      throw new OkapiException(
          String.format("No file extension detected in \"%s\".", sourceFileName));
    }

    // create input stream and translate
    try (InputStream inputStream =
        Files.newInputStream(Path.of(new File(sourceFileName).toURI()))) {
      translate(inputStream, ext, sourceLang, sourceEnc,
          targetFileName, targetLang, targetEnc, translatorId, markupStrategy, applySegmentation);
    } catch (IOException e) {
      throw new OkapiException(
          String.format("could not read source file \"%s\"", sourceFileName), e);
    }
  }


  /**
   * Translate source document read from the given input stream from the given source language to
   * the given target language using the translator with the given id.
   *
   * @param inputStream
   *          the stream to read the source document from
   * @param fileExtension
   *          the source document type, usually indicated by the file extension
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
   * @param markupStrategy
   *          the markup re-insertion strategy to use; only applies when using Marian NMT
   * @param applySegmentation
   *          apply segmentation when {@code true}
   */
  public void translate(
      InputStream inputStream, String fileExtension, String sourceLang, String sourceEnc,
      String targetFileName, String targetLang, String targetEnc,
      TransId translatorId, MarkupStrategy markupStrategy, boolean applySegmentation) {

    // get configuration id for file extension
    String configId = this.extensionsMap.get(fileExtension);
    if (configId == null) {
      throw new OkapiException(String.format(
          "Could not guess the configuration for the extension '%s'", fileExtension));
    }
    // get MIME type for file extension
    String mimeType = MimeTypeMapper.getMimeType(fileExtension);

    // parameter summary
    logger.info("             source language: {}", sourceLang);
    logger.info("             source encoding: {}", sourceEnc);
    logger.info("                 target file: {}", targetFileName);
    logger.info("             target language: {}", targetLang);
    logger.info("             target encoding: {}", targetEnc);
    logger.info("               translator id: {}", translatorId);
    logger.info("markup re-insertion strategy: {}", markupStrategy);
    logger.info("          MIME type detected: {}", mimeType);
    logger.info("      configuration detected: {}", configId);

    // read Marian NMT configuration if Marian NMT is used as translator
    MarianNmtParameters marianNmtResourceParams = new MarianNmtParameters();
    if (translatorId == TransId.MARIAN || translatorId == TransId.MARIAN_BATCH) {
      URI paramUri = new File("src/main/resources/marianConfig.cfg").toURI();
      marianNmtResourceParams.load(Util.URItoURL(paramUri), false);
      marianNmtResourceParams.setMarkupStrategy(markupStrategy);
    }

    if (translatorId == TransId.MARIAN_BATCH) {
      try {
        // make sure input stream is resettable, as we have to create the raw document twice
        // from the same input stream
        FileCachedInputStream resettableInputStream = StreamUtil.createResettableStream(
            inputStream, FileCachedInputStream.DEFAULT_BUFFER_SIZE);

        try (RawDocument rawDoc =
            new RawDocument(
                resettableInputStream,
                sourceEnc,
                LocaleId.fromString(sourceLang),
                LocaleId.fromString(targetLang))) {
          rawDoc.setFilterConfigId(configId);

          // add document id under which the results of the batch processor are stored
          // to Marian NMT configuration
          marianNmtResourceParams.setDocumentId(rawDoc.hashCode() + "");

          // run batch processor
          runMarianNmtBatch(
              rawDoc, sourceLang, targetLang, marianNmtResourceParams, applySegmentation);

          // reset input stream so that the same rawDocument can be processed again
          if (!resettableInputStream.isOpen()) {
            resettableInputStream.reopen();
          }
          resettableInputStream.reset();
          inputStream = resettableInputStream;
        }
      } catch (IOException e) {
        throw new OkapiException("creation of resettable input stream failed", e);
      }
    }

    // process document for the first time (if using MARIAN)
    // or for the second time (if using MARIAN_BATCH); in the latter case, pre-/postprocessing
    // results will be returned from cache
    try (RawDocument rawDoc =
        new RawDocument(
            inputStream,
            sourceEnc,
            LocaleId.fromString(sourceLang),
            LocaleId.fromString(targetLang))) {

      rawDoc.setFilterConfigId(configId);

      // create the driver
      PipelineDriver driver = new PipelineDriver();
      driver.setFilterConfigurationMapper(this.fcMapper);
      String projectDir = Util.getDirectoryName(Paths.get(".").toAbsolutePath().toString());
      driver.setRootDirectories(projectDir, projectDir);

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
          driver.addStep(createMarianLeveragingStep(marianNmtResourceParams));
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
        BatchRunner.INSTANCE.clear(marianNmtResourceParams.getDocumentId());
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
   * @param marianNmtResourceParams
   *          the Marian NMT configuration
   * @param applySegmentation
   *          add segmentation when {@code true}
   */
  private void runMarianNmtBatch(
      RawDocument rawDoc, String sourceLang, String targetLang,
      MarianNmtParameters marianNmtResourceParams, boolean applySegmentation) {

    // create the driver
    PipelineDriver driver = new PipelineDriver();
    driver.setFilterConfigurationMapper(this.fcMapper);
    String projectDir = Util.getDirectoryName(Paths.get(".").toAbsolutePath().toString());
    driver.setRootDirectories(projectDir, projectDir);

    // raw document to filter events step
    driver.addStep(new RawDocumentToFilterEventsStep());

    // add segmentation step (optional)
    if (applySegmentation) {
      driver.addStep(createSeqmentationStep());
    }

    // collect all text fragments for batch processor
    driver.addStep(new TextFragmentsCollector(marianNmtResourceParams.getDocumentId()));

    // add document to Okapi pipeline for processing
    driver.addBatchItem(rawDoc);

    // process
    driver.processBatch();

    // batch process text fragments collected in the pipeline above;
    // use parameters of MarianNmtConnector
    BatchRunner.INSTANCE.processBatch(sourceLang, targetLang, marianNmtResourceParams);
    logger.debug(BatchRunner.INSTANCE.getStats(marianNmtResourceParams.getDocumentId()));
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
   * @param marianNmtResourceParams
   *          the Marian NMT configuration
   * @return the leveraging step
   */
  private LeveragingStep createMarianLeveragingStep(MarianNmtParameters marianNmtResourceParams) {

    LeveragingStep levStep = new LeveragingStep();

    net.sf.okapi.steps.leveraging.Parameters levParams =
        (net.sf.okapi.steps.leveraging.Parameters)levStep.getParameters();
    levParams.setResourceClassName(MarianNmtConnector.class.getName());
    levParams.setResourceParameters(marianNmtResourceParams.toString());
    levParams.setFillTarget(true);

    return levStep;
  }
}
