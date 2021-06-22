package de.dfki.mlt.transins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dfki.mlt.transins.MarkupInserter.MarkupStrategy;
import de.dfki.mlt.transins.server.ConfigKeys;
import de.dfki.mlt.transins.server.Utils;

/**
 * Translate German evaluation document to French and English for all re-insertion strategies,
 * with perfect alignments and Marian provided alignments.
 *
 * @author Jörg Steffen, DFKI
 */
public final class Evaluation {

  private static final Logger logger = LoggerFactory.getLogger(Evaluation.class);


  private Evaluation() {

    // private constructor to enforce noninstantiability
  }


  /**
   * Run evaluation for given source document.
   *
   * @param transInsConfig
   *          TransIns configuration
   * @param sourceFileName
   *          source document file name
   * @param sourceLang
   *          source document language
   * @param targetLang
   *          target document language
   * @param strategy
   *          markup re-insertion strategy
   * @param maxGapSize
   *          maximum gap size (only used for {@link MarkupStrategy#COMPLETE_MAPPING})
   * @param perfectAlignsFileName
   *          perfect alignments file name, use <code>null</code> for Marian alignments
   * @throws IOException
   *           if reading alignments fails
   */
  public static void evaluate(PropertiesConfiguration transInsConfig,
      String sourceFileName, String sourceLang, String targetLang,
      MarkupStrategy strategy, int maxGapSize, String perfectAlignsFileName)
      throws IOException {

    String evalPath = String.format("evaluation/%s-%s", sourceLang, targetLang);
    try {
      Files.createDirectories(Paths.get(evalPath));
    } catch (IOException e) {
      logger.error(e.getLocalizedMessage(), e);
      return;
    }

    String sourceFileNameWithoutExtension =
        sourceFileName.substring(
            sourceFileName.lastIndexOf("/") + 1,
            sourceFileName.lastIndexOf("."));
    String sourceFileNameExtension = Utils.getFileExtension(sourceFileName);
    String algns = perfectAlignsFileName == null ? "marian-algns" : "perf-algns";
    String gap = strategy == MarkupStrategy.COMPLETE_MAPPING ? ".gap" + maxGapSize : "";
    String targetFileName = String.format("%s/%s_%s.%s.%s%s.%s",
        evalPath, sourceFileNameWithoutExtension, targetLang, strategy, algns, gap,
        sourceFileNameExtension);

    String transDirPrefix = String.format("%s-%s.", sourceLang, targetLang);
    Translator translator = null;
    if (perfectAlignsFileName != null) {
      translator = new Translator(perfectAlignsFileName);
    } else {
      translator = new Translator();
    }
    translator.translateWithMarianNmt(
        sourceFileName, sourceLang, "utf-8",
        targetFileName, targetLang, "utf-8",
        true, strategy, maxGapSize, false,
        transInsConfig.getString(transDirPrefix + ConfigKeys.TRANSLATION_URL),
        transInsConfig.getString(transDirPrefix + ConfigKeys.PREPOST_HOST),
        transInsConfig.getInt(transDirPrefix + ConfigKeys.PREPOST_PORT),
        transInsConfig.getBoolean(transDirPrefix + ConfigKeys.USE_TARGET_LANG_TAG));
  }


  /**
   * Run evaluation for given source document for all re-insertion strategies and maximum gap sizes
   * from 0 to 3 for {@link MarkupStrategy#COMPLETE_MAPPING}.
   *
   * @param transInsConfig
   *          TransIns configuration
   * @param sourceFileName
   *          source document file name
   * @param sourceLang
   *          source document language
   * @param targetLang
   *          target document language
   * @param perfectAlignsFileName
   *          perfect alignments file name, use <code>null</code> for Marian alignments
   * @throws IOException
   *           if reading alignments fails
   */
  public static void evaluate(PropertiesConfiguration transInsConfig,
      String sourceFileName, String sourceLang, String targetLang,
      String perfectAlignsFileName)
          throws IOException {

    for (MarkupStrategy strategy : MarkupStrategy.values()) {
      if (strategy == MarkupStrategy.COMPLETE_MAPPING) {
        for (int maxGapSize = 0; maxGapSize <= 3; maxGapSize++) {
          evaluate(transInsConfig, sourceFileName, sourceLang, targetLang, strategy, maxGapSize,
              perfectAlignsFileName);
        }
      } else {
        evaluate(transInsConfig, sourceFileName, sourceLang, targetLang, strategy, 0,
            perfectAlignsFileName);
      }
    }
  }


  /**
   * @param args
   *          the arguments; not used here
   */
  public static void main(String[] args) {

    try {
      PropertiesConfiguration transInsConfig = Utils.readConfigFromClasspath("transIns.cfg");

      // de-fr
      evaluate(transInsConfig, "evaluation/News.01.de.docx", "de", "fr", null);
      evaluate(transInsConfig, "evaluation/News.01.de.docx", "de", "fr",
          "evaluation/de-fr/News.01.de-fr.aligns.txt");
      for (int i = 2; i <= 10; i++) {
        String sourceFileName = String.format("evaluation/News.%02d.de.docx", i);
        for (int maxGapSize = 0; maxGapSize <= 1; maxGapSize++) {
          evaluate(transInsConfig, sourceFileName, "de", "fr", MarkupStrategy.COMPLETE_MAPPING,
              maxGapSize, null);
        }
      }

      // de-en
      evaluate(transInsConfig, "evaluation/News.01.de.docx", "de", "en", null);
      evaluate(transInsConfig, "evaluation/News.01.de.docx", "de", "en",
          "evaluation/de-en/News.01.de-en.aligns.txt");
      for (int i = 2; i <= 10; i++) {
        String sourceFileName = String.format("evaluation/News.%02d.de.docx", i);
        for (int maxGapSize = 0; maxGapSize <= 1; maxGapSize++) {
          evaluate(transInsConfig, sourceFileName, "de", "en", MarkupStrategy.COMPLETE_MAPPING,
              maxGapSize, null);
        }
      }

    } catch (ConfigurationException | IOException e) {
      logger.error(e.getLocalizedMessage(), e);
    }
  }
}
