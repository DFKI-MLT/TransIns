package de.dfki.mlt.transins.server;

import de.dfki.mlt.transins.MarkupInserter.MarkupStrategy;
import lombok.Data;

/**
 * Data wrapper for translation jobs.
 *
 * @author Jörg Steffen, DFKI
 */
@Data
public class Job {

  /**
   * Job statuses.
   */
  public enum Status {

    /** status of jobs in queue, not translated yet */
    QUEUED,
    /** status of job currently in translation */
    IN_TRANSLATION,
    /** status successfully translated jobs */
    FINISHED,
    /** status of failed jobs */
    FAILED,
    /** status of unknown jobs */
    UNKONWN
  }


  private String id;
  private String originalFileName;
  private String internalFileName;
  private String sourceLanguage;
  private String targetLanguage;
  private String encoding;
  private MarkupStrategy markupStrategy;
  private Status status;


  /**
   * Create a new job.
   *
   * @param id
   *          the id
   * @param originalFileName
   *          the original file name
   * @param internalFileName
   *          the internal file name
   * @param sourceLanguage
   *          the source language
   * @param targetLanguage
   *          the target language
   * @param encoding
   *          the encoding
   * @param markupStrategy
   *          the markup re-insertion strategy
   */
  public Job(String id, String originalFileName, String internalFileName, String sourceLanguage,
      String targetLanguage, String encoding, MarkupStrategy markupStrategy) {

    this.id = id;
    this.originalFileName = originalFileName;
    this.internalFileName = internalFileName;
    this.sourceLanguage = sourceLanguage;
    this.targetLanguage = targetLanguage;
    this.encoding = encoding;
    this.markupStrategy = markupStrategy;
  }


  /**
   * @return file name of translated document as delivered to caller
   */
  public String getResultFileName() {

    int dotIndex = this.originalFileName.lastIndexOf('.');
    String originalFileNameWithoutExtension = this.originalFileName.substring(0, dotIndex);
    String fileExtension = this.originalFileName.substring(dotIndex + 1);
    return String.format("%s_%s-translation.%s",
        originalFileNameWithoutExtension, this.targetLanguage, fileExtension);
  }
}
