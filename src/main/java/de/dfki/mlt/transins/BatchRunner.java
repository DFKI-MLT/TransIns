package de.dfki.mlt.transins;

import static de.dfki.mlt.transins.TagUtils.removeTags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dfki.mlt.transins.MarkupInserter.MarkupStrategy;
import de.dfki.mlt.transins.PrePostProcessingClient.Mode;
import lombok.Data;
import net.sf.okapi.common.exceptions.OkapiException;
import net.sf.okapi.common.resource.TextFragment;

/**
 * Singleton for batch processing of Okapi text fragments
 * i.e. preprocessing, translation with MarianNMT and postprocessing.
 *
 * @author Jörg Steffen, DFKI
 */
public enum BatchRunner {

  /** the only instance of this class */
  INSTANCE;


  private static final Logger logger = LoggerFactory.getLogger(BatchRunner.class);

  // map of document id to list of text fragments to batch process
  private Map<String, List<TextFragment>> batchInputs = new HashMap<>();

  // map of document id to batch processing result;
  // processing result maps text fragment surface string
  // to pre/postprocessed translation
  private Map<String, Map<String, String>> batchResults = new HashMap<>();

  // client for pre/postprocessing
  private PrePostProcessingClient prePostClient = new PrePostProcessingClient();


  /**
   * Get batch input list for document with the given id.
   * If document id is unknown, a new list will be initialized and returned.
   *
   * @param docId
   *          the document id
   * @return the batch input list
   */
  public List<TextFragment> getBatchInput(String docId) {

    List<TextFragment> batchInputList = this.batchInputs.get(docId);
    if (batchInputList == null) {
      batchInputList = new ArrayList<>();
      this.batchInputs.put(docId, batchInputList);
    }
    return batchInputList;
  }


  /**
   * Process batch input list stored under the id (as set in the given Marian NMT configuration)
   * using Marian NMT with the given configuration.
   *
   * @param sourceLang
   *          the source language
   * @param targetLang
   *          the target language
   * @param marianNmtResourceParams
   *          the Marian NMT configuration
   */
  public void processBatch(String sourceLang, String targetLang,
      MarianNmtParameters marianNmtResourceParams) {

    String docId = marianNmtResourceParams.getDocumentId();
    List<TextFragment> batchInputList = this.batchInputs.get(docId);
    if (batchInputList == null) {
      // nothing to do
      logger.warn("no batch found for id \"{}\"", docId);
      return;
    }

    StopWatch watch = new StopWatch();
    watch.start();

    MarianNmtClient translatorClient =
        new MarianNmtClient(marianNmtResourceParams.getTranslationUrl());
    String prePostHost = marianNmtResourceParams.getPrePostHost();
    int prePostPort = marianNmtResourceParams.getPrePostPort();
    MarkupStrategy markupStrategy = marianNmtResourceParams.getMarkupStrategy();

    List<BatchItem> batchItems = createBatchItems(batchInputList);
    preprocess(batchItems, prePostHost, prePostPort, sourceLang, targetLang);
    translate(batchItems, translatorClient, markupStrategy);
    postProcess(batchItems, prePostHost, prePostPort, sourceLang, targetLang);
    createBatchResult(batchItems, docId);

    watch.stop();
    logger.info(String.format("batch processing time: %s", watch));
  }


  /**
   * Get batch result for the given id.
   *
   * @param docId
   *          the document id
   * @return the batch result
   */
  public Map<String, String> getBatchResult(String docId) {

    Map<String, String> batchResultMap = this.batchResults.get(docId);
    if (null == batchResultMap) {
      logger.warn("no batch found for id \"{}\"", docId);
      return Collections.emptyMap();
    }
    return batchResultMap;
  }


  /**
   * Remove batch list and result for the given id.
   *
   * @param docId
   *          the document id
   */
  public void clear(String docId) {

    this.batchInputs.remove(docId);
    this.batchResults.remove(docId);
  }


  /**
   * Get statistics for the given id.
   *
   * @param docId
   *          the document id
   * @return statistics with total number of inputs and results
   */
  public String getStats(String docId) {

    int batchInputSize = 0;
    List<TextFragment> batchInput = this.batchInputs.get(docId);
    if (batchInput != null) {
      batchInputSize = batchInput.size();
    }
    int batchResultSize = 0;
    Map<String, String> batchResult = this.batchResults.get(docId);
    if (batchResult != null) {
      batchResultSize = batchResult.size();
    }

    return String.format("doc id: %s%ninput text fragments: %,d%nresult translations: %,d",
        docId, batchInputSize, batchResultSize);
  }


  /**
   * Create batch items with text fragment and preprocessing input set.
   *
   * @param textFragments
   *          the text fragments
   * @return the batch items
   */
  private List<BatchItem> createBatchItems(List<TextFragment> textFragments) {

    logger.debug("creating batch items...");
    List<BatchItem> batchItems = new ArrayList<>();
    for (TextFragment oneTextFragment : textFragments) {
      batchItems.add(new BatchItem(oneTextFragment));
    }
    return batchItems;
  }


  /**
   * Preprocess given batch items and further process them to translator input.
   *
   * @param batchItems
   *          the batch items
   * @param prePostHost
   *          the host of the pre/postprocessing server
   * @param prePostPort
   *          the port of the pre/postprocessing server
   * @param sourceLang
   *          the source language
   * @param targetLang
   *          the target language
   */
  private void preprocess(
      List<BatchItem> batchItems, String prePostHost, int prePostPort,
      String sourceLang, String targetLang) {

    logger.debug("preprocessing batch items...");

    List<String> preprocessingInput = new ArrayList<>();
    for (BatchItem oneBatchItem : batchItems) {
      preprocessingInput.add(oneBatchItem.getPreInput());
    }

    Map<String, String> preprocessingResult =
        this.prePostClient.bulkProcess(
            String.format("%s-%s", sourceLang, targetLang),
            preprocessingInput,
            Mode.PREPROCESS,
            prePostHost,
            prePostPort);

    for (BatchItem oneBatchItem : batchItems) {
      oneBatchItem.setPreResult(preprocessingResult.get(oneBatchItem.getPreInput()));
      oneBatchItem.setTransInput(
          String.format("<to%s> %s", targetLang, removeTags(oneBatchItem.getPreResult())));
    }
  }


  /**
   * Translate given batch items using the given Marian NMT client and further process them to
   * postprocessing input. This includes markup re-insertion.
   *
   * @param batchItems
   *          the batch items
   * @param translatorClient
   *          the translator client to use
   * @param markupStrategy
   *          the markup re-insertion strategy to use
   */
  private void translate(List<BatchItem> batchItems, MarianNmtClient translatorClient,
      MarkupStrategy markupStrategy) {

    logger.debug("translating batch items...");

    try {
      List<String> translatorInput = new ArrayList<>();
      for (BatchItem oneBatchItem : batchItems) {
        translatorInput.add(oneBatchItem.getTransInput());
      }

      Map<String, String> translatorResult = translatorClient.bulkTranslate(translatorInput);
      translatorClient.close();

      for (BatchItem oneBatchItem : batchItems) {
        String rawTranslation = translatorResult.get(oneBatchItem.getTransInput());
        oneBatchItem.setPostInput(MarianNmtConnector.processRawTranslation(
            rawTranslation, oneBatchItem.getTextFragment(), oneBatchItem.getPreResult(),
            markupStrategy));
      }
    } catch (InterruptedException | ExecutionException e) {
      throw new OkapiException("Error querying the translation server." + e.getMessage(), e);
    }
  }


  /**
   * Postprocess given batch items.
   *
   * @param batchItems
   *          the batch items
   * @param prePostHost
   *          the host of the pre/postprocessing server
   * @param prePostPort
   *          the port of the pre/postprocessing server
   * @param sourceLang
   *          the source language
   * @param targetLang
   *          the target language
   */
  private void postProcess(
      List<BatchItem> batchItems, String prePostHost, int prePostPort,
      String sourceLang, String targetLang) {

    logger.debug("postprocessing batch items...");

    List<String> postprocessingInput = new ArrayList<>();
    for (BatchItem oneBatchItem : batchItems) {
      postprocessingInput.add(oneBatchItem.getPostInput());
    }

    Map<String, String> postprocessingResult =
        this.prePostClient.bulkProcess(
            String.format("%s-%s", sourceLang, targetLang),
            postprocessingInput,
            Mode.POSTPROCESS,
            prePostHost,
            prePostPort);

    for (BatchItem oneBatchItem : batchItems) {
      String postRes = postprocessingResult.get(oneBatchItem.getPostInput());
      if (oneBatchItem.getTextFragment().hasCode()) {
        postRes = MarianNmtConnector.cleanPostProcessedSentence(postRes);
      }
      oneBatchItem.setPostResult(postRes);
    }
  }


  /**
   * Create batch result from the given batch items and store it under the given id.
   *
   * @param batchItems
   *          the batch items
   * @param docId
   *          the document id
   */
  private void createBatchResult(List<BatchItem> batchItems, String docId) {

    logger.debug("creating batch results...");
    Map<String, String> batchResult = new LinkedHashMap<>();
    this.batchResults.put(docId, batchResult);
    int duplicates = 0;
    for (BatchItem oneBatchItem : batchItems) {
      if (null != batchResult.put(
          oneBatchItem.getTextFragment().toString(), oneBatchItem.getPostResult())) {
        duplicates++;
      }
    }
    logger.debug("found {} duplicates", duplicates);
  }


  /**
   * Data wrapper for batch items.
   */
  @Data
  static class BatchItem {

    private TextFragment textFragment;
    private String preInput;
    private String preResult;
    private String transInput;
    private String postInput;
    private String postResult;


    /**
     * Create a new batch item for the given text fragment and set the preprocessing input.
     *
     * @param textFragment
     *          the text fragment
     */
    BatchItem(TextFragment textFragment) {

      this.textFragment = textFragment;
      this.preInput = textFragment.getCodedText();
    }
  }
}
