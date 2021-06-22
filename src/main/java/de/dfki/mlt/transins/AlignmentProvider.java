package de.dfki.mlt.transins;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton for providing hand annotated alignments between source sentence and its translation.
 * Used in evaluation.
 *
 * @author Jörg Steffen, DFKI
 */
public enum AlignmentProvider {

  /** the only instance of this class */
  INSTANCE;


  private static final Logger logger = LoggerFactory.getLogger(AlignmentProvider.class);

  // mapping of the concatenated source sentence and its translation to alignments;
  // alignments are index based from source sentence to translation in the format x-y
  private Map<String, String> alignmentsMap = new HashMap<>();


  /**
   * Load alignments from the given file.
   *
   * @param alignmentFileName
   *          the alignment file
   * @throws IOException
   *           if reading alignments fails
   */
  public void loadAlignments(String alignmentFileName)
      throws IOException {

    try (BufferedReader in = Files.newBufferedReader(
        Paths.get(alignmentFileName), StandardCharsets.UTF_8)) {
      String line;
      String sourceSentence = null;
      String translation = null;
      String alignments = null;
      while ((line = in.readLine()) != null) {
        line = line.strip();
        if (line.isEmpty() || line.startsWith("###")) {
          continue;
        }
        // alignments to load have the following format:
        // 1. the source sentence
        // 2. the translation
        // 3. the alignments in format as provided by Marian NMT:
        //    source sentence token index to translation token index in the format x-y
        if (sourceSentence == null) {
          sourceSentence = line;
          continue;
        }
        if (translation == null) {
          translation = line;
          continue;
        }
        alignments = line;
        // at this point, the whole alignment block has been read
        this.alignmentsMap.put(sourceSentence + translation, alignments);
        sourceSentence = null;
        translation = null;
        alignments = null;
      }
    }
  }


  /**
   * @return a flag indicating if hand annotated alignments have been loaded
   */
  public boolean isInitialized() {

    return !this.alignmentsMap.isEmpty();
  }


  /**
   * Get hand annotated alignments for the given source sentence and translation.
   *
   * @param sourceSentence
   *          the source sentence
   * @param translation
   *          the translation
   * @return the alignments as provided by Marian NMT; <code>null</code> if not available
   */
  public String getAlignments(String sourceSentence, String translation) {

    return this.alignmentsMap.get(sourceSentence.strip() + translation.strip());
  }
}
