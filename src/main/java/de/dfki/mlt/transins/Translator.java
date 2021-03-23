package de.dfki.mlt.transins;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Hashtable;
import java.util.List;
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
import net.sf.okapi.common.filters.FilterConfiguration;
import net.sf.okapi.common.filters.FilterConfigurationMapper;
import net.sf.okapi.common.filters.IFilterConfigurationMapper;
import net.sf.okapi.common.filterwriter.IFilterWriter;
import net.sf.okapi.common.io.FileCachedInputStream;
import net.sf.okapi.common.pipeline.BasePipelineStep;
import net.sf.okapi.common.pipeline.IPipelineStep;
import net.sf.okapi.common.pipelinedriver.PipelineDriver;
import net.sf.okapi.common.resource.RawDocument;
import net.sf.okapi.connectors.apertium.ApertiumMTConnector;
import net.sf.okapi.connectors.microsoft.MicrosoftMTConnector;
import net.sf.okapi.filters.openoffice.OpenOfficeFilter;
import net.sf.okapi.filters.openxml.OpenXMLFilter;
import net.sf.okapi.filters.plaintext.PlainTextFilter;
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

  /** supported translation engines, not including Marian NMT */
  public enum TransId {

    /** Microsoft Azure translation engine */
    MICROSOFT,

    /** Apertium */
    APERTIUM,

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
    this.fcMapper.addConfiguration(
        new FilterConfiguration(
            "okf_plaintext_custom",
            PlainTextFilter.FILTER_MIME,
            PlainTextFilter.class.getName(),
            "Plain Text (custom)",
            "plain text filter with configuration as set in custom parameters file",
            "/okapi/okf_plaintext_custom.fprm",
            ".txt"));

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

    this.extensionsMap.put("htm", "okf_html_custom");
    this.extensionsMap.put("html", "okf_html_custom");

    this.extensionsMap.put("txt", "okf_plaintext_custom");
  }


  /**
   * @return set of supported file extensions
   */
  public Set<String> getSupportedExtensions() {

    return this.extensionsMap.keySet();
  }


  /**
   * Translate source document from the given source language to the given target language using the
   * translator with the given id. Use this method for non-Marian NMT translations.
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
   * @param applySegmentation
   *          add segmentation when {@code true}
   * @param translatorId
   *          the translator id
   */
  public void translate(
      String sourceFileName, String sourceLang, String sourceEnc,
      String targetFileName, String targetLang, String targetEnc,
      boolean applySegmentation, TransId translatorId) {

    // get file extension
    String ext = getExtensionFromFileName(sourceFileName);

    // create input stream and translate
    try (InputStream inputStream =
        Files.newInputStream(Path.of(new File(sourceFileName).toURI()))) {
      translate(inputStream, ext, sourceLang, sourceEnc,
          targetFileName, targetLang, targetEnc, applySegmentation, translatorId);
    } catch (IOException e) {
      throw new OkapiException(
          String.format("could not read source file \"%s\"", sourceFileName), e);
    }
  }


  /**
   * Translate source document read from the given input stream from the given source language to
   * the given target language using the translator with the given id. Use this method for
   * non-Marian NMT translations.
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
   * @param applySegmentation
   *          apply segmentation when {@code true}
   * @param translatorId
   *          the translator id
   */
  public void translate(
      InputStream inputStream, String fileExtension, String sourceLang, String sourceEnc,
      String targetFileName, String targetLang, String targetEnc,
      boolean applySegmentation, TransId translatorId) {

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
    logger.info("          MIME type detected: {}", mimeType);
    logger.info("      configuration detected: {}", configId);

    try (RawDocument rawDoc =
        new RawDocument(
            inputStream,
            sourceEnc,
            LocaleId.fromString(sourceLang),
            LocaleId.fromString(targetLang))) {

      rawDoc.setFilterConfigId(configId);

      // create (pseudo) leveraging step for selected translator
      BasePipelineStep leveragingStep = null;
      switch (translatorId) {
        case MICROSOFT:
          leveragingStep = createMicrosoftLeveragingStep("microsoft-translator.cfg");
          break;
        case APERTIUM:
          leveragingStep = createApertiumLeveragingStep("apertium-translator.cfg");
          break;
        case UPPERCASE_DUMMY:
          leveragingStep = new UppercaseStep();
          break;
        default:
          logger.error("unknown translator id \"{}\"", translatorId);
          return;
      }

      // create pipeline driver
      PipelineDriver driver = createPipelineDriver(leveragingStep, applySegmentation);

      // set document to process
      driver.addBatchItem(rawDoc, new File(targetFileName).toURI(), targetEnc);

      // process
      driver.processBatch();
    }
  }


  /**
   * Get extension from the given file name.
   *
   * @param fileName
   *          the file name
   * @return the extension
   */
  private String getExtensionFromFileName(String fileName) {

    String ext = Util.getExtension(fileName);
    // remove dot from extension
    if (ext.length() > 0) {
      ext = ext.substring(1);
    }
    if (Util.isEmpty(ext)) {
      throw new OkapiException(
          String.format("No file extension detected in \"%s\".", fileName));
    }
    return ext;
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
      InputStream translatorConfigIn =
          getClass().getClassLoader().getResourceAsStream(translatorConfig);
      resourceParams.load(translatorConfigIn, false);
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
      InputStream translatorConfigIn =
          getClass().getClassLoader().getResourceAsStream(translatorConfig);
      resourceParams.load(translatorConfigIn, false);
    }
    levParams.setResourceParameters(resourceParams.toString());
    levParams.setFillTarget(true);

    return levStep;
  }


  /**
   * Create pipeline driver using the given (pseudo) leveraging step.
   *
   * @param leveragingStep
   *          the (pseudo) leveraging step
   * @param applySegmentation
   *          apply segmentation when {@code true}
   * @return the pipeline driver
   */
  private PipelineDriver createPipelineDriver(
      BasePipelineStep leveragingStep, boolean applySegmentation) {

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

    // add leveraging step
    driver.addStep(leveragingStep);

    // filter events to raw document final step
    FilterEventsToRawDocumentStep filterEventsToRawDocumentStep =
        new FilterEventsToRawDocumentStep();
    driver.addStep(filterEventsToRawDocumentStep);

    return driver;
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
    InputStream sourceSrxIn =
        getClass().getClassLoader().getResourceAsStream("okapi/defaultSegmentation.srx");
    InputStream targetSrxIn =
        getClass().getClassLoader().getResourceAsStream("okapi/defaultSegmentation.srx");
    segParams.setSourceSrxStream(sourceSrxIn);
    segParams.setTargetSrxStream(targetSrxIn);
    segParams.setCopySource(true);

    return segStep;
  }


  /**
   * Translate source document from the given source language to the given target language using
   * Marian NMT.
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
   * @param applySegmentation
   *          add segmentation when {@code true}
   * @param markupStrategy
   *          the markup re-insertion strategy to use
   * @param batchProcessing
   *          do batch processing when {@code true}
   * @param translationUrl
   *          Marian NMT server web socket URL
   * @param prePostHost
   *          pre-/postprocessing server host
   * @param prePostPort
   *          pre-/postprocessing server port
   */
  public void translateWithMarianNmt(
      String sourceFileName, String sourceLang, String sourceEnc,
      String targetFileName, String targetLang, String targetEnc,
      boolean applySegmentation, MarkupStrategy markupStrategy, boolean batchProcessing,
      String translationUrl, String prePostHost, int prePostPort) {

    // get file extension
    String ext = getExtensionFromFileName(sourceFileName);

    // create input stream and translate
    try (InputStream inputStream =
        Files.newInputStream(Path.of(new File(sourceFileName).toURI()))) {
      translateWithMarianNmt(inputStream, ext, sourceLang, sourceEnc,
          targetFileName, targetLang, targetEnc, applySegmentation,
          markupStrategy, batchProcessing, translationUrl, prePostHost, prePostPort);
    } catch (IOException e) {
      throw new OkapiException(
          String.format("could not read source file \"%s\"", sourceFileName), e);
    }
  }


  /**
   * Translate source document read from the given input stream from the given source language to
   * the given target language using Marian NMT.
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
   * @param applySegmentation
   *          apply segmentation when {@code true}
   * @param markupStrategy
   *          the markup re-insertion strategy to use
   * @param batchProcessing
   *          do batch processing when {@code true}
   * @param translationUrl
   *          Marian NMT server web socket URL
   * @param prePostHost
   *          pre-/postprocessing server host
   * @param prePostPort
   *          pre-/postprocessing server port
   */
  public void translateWithMarianNmt(
      InputStream inputStream, String fileExtension, String sourceLang, String sourceEnc,
      String targetFileName, String targetLang, String targetEnc,
      boolean applySegmentation, MarkupStrategy markupStrategy, boolean batchProcessing,
      String translationUrl, String prePostHost, int prePostPort) {

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
    logger.info("markup re-insertion strategy: {}", markupStrategy);
    logger.info("          MIME type detected: {}", mimeType);
    logger.info("      configuration detected: {}", configId);

    // set Marian NMT parameters
    MarianNmtParameters marianNmtResourceParams = new MarianNmtParameters();
    marianNmtResourceParams.setTranslationUrl(translationUrl);
    marianNmtResourceParams.setPrePostHost(prePostHost);
    marianNmtResourceParams.setPrePostPort(prePostPort);
    marianNmtResourceParams.setMarkupStrategy(markupStrategy);
    marianNmtResourceParams.setOkapiFilterConfigId(configId);

    if (batchProcessing) {
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

    // process document for the first time (if doing non-batch processing)
    // or for the second time (if doing batch processing); in the latter case, pre-/postprocessing
    // results will be returned from cache
    try (RawDocument rawDoc =
        new RawDocument(
            inputStream,
            sourceEnc,
            LocaleId.fromString(sourceLang),
            LocaleId.fromString(targetLang))) {

      rawDoc.setFilterConfigId(configId);

      // create leveraging step
      BasePipelineStep leveragingStep = createMarianLeveragingStep(marianNmtResourceParams);

      // create pipeline driver
      PipelineDriver driver = createPipelineDriver(leveragingStep, applySegmentation);

      // get filter events to raw document final step; it is assumed that this
      // is the last step of the pipeline; required later to release resources
      List<IPipelineStep> steps = driver.getPipeline().getSteps();
      FilterEventsToRawDocumentStep filterEventsToRawDocumentStep =
          (FilterEventsToRawDocumentStep)steps.get(steps.size() - 1);

      // set document to process
      driver.addBatchItem(rawDoc, new File(targetFileName).toURI(), targetEnc);

      // process
      try {
        driver.processBatch();
      } finally {
        // if an exception is thrown during processing, make sure all resources are released

        // this is a *HACK* to make sure that the output file is closed properly and
        // can be deleted later
        try {
          Field filterWriterField =
              FilterEventsToRawDocumentStep.class.getDeclaredField("filterWriter");
          filterWriterField.setAccessible(true);
          IFilterWriter filterWriter =
              (IFilterWriter)filterWriterField.get(filterEventsToRawDocumentStep);
          filterWriter.close();
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
          logger.error(e.getLocalizedMessage(), e);
        }

        // remove processed text fragments for this document from batch runner
        if (batchProcessing) {
          BatchRunner.INSTANCE.clear(marianNmtResourceParams.getDocumentId());
        }
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
   * Create leveraging step using Marian NMT translator.
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
