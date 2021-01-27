package de.dfki.mlt.transins.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manage translation jobs.
 *
 * @author Jörg Steffen, DFKI
 */
public class JobManager {

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

  private static final Logger logger = LoggerFactory.getLogger(JobManager.class);

  private static final int JOB_LIST_SIZE = 100;
  private List<String> queuedJobs;
  private Set<String> inTranslationJobs;
  private List<String> finishedJobs;
  private List<String> failedJobs;

  private String inputFolder;
  private String outputFolder;

  /**
   * Create a new job manager. Files associated with the jobs are stored in the given input and
   * output folders.
   *
   * @param inputFolder
   *          the input folder
   * @param outputFolder
   *          the output folder
   */
  public JobManager(String inputFolder, String outputFolder) {

    this.inputFolder = inputFolder;
    this.outputFolder = outputFolder;

    this.inTranslationJobs = new HashSet<>();
    this.queuedJobs = new ArrayList<String>();
    this.finishedJobs = new ArrayList<String>(JOB_LIST_SIZE + 1);
    this.failedJobs = new ArrayList<String>(JOB_LIST_SIZE + 1);
  }


  /**
   * Add given job to queued jobs.
   *
   * @param jobId
   *          the document file name, used as job id
   */
  public synchronized void addJobToQueue(String jobId) {

    logger.info("add {} to queue", jobId);
    this.queuedJobs.add(0, jobId);
  }


  /**
   * Mark given job as in translation.
   *
   * @param jobId
   *          the document file name, used as job id
   */
  public synchronized void markJobAsInTranslation(String jobId) {

    if (this.queuedJobs.remove(jobId)) {
      logger.info("mark job {} as in translation", jobId);
      this.inTranslationJobs.add(jobId);
    } else {
      logger.error("unknown queued job {}", jobId);
    }
  }


  /**
   * Mark given job as finished.
   *
   * @param jobId
   *          the document file name, used as job id
   */
  public synchronized void markJobAsFinished(String jobId) {

    if (this.inTranslationJobs.remove(jobId)) {
      logger.info("mark job {} as finished", jobId);
      this.finishedJobs.add(0, jobId);
      cleanUpJobList(this.finishedJobs);
    } else {
      logger.error("unknown in translation job {}", jobId);
    }
  }


  /**
   * Mark given job as failed.
   *
   * @param jobId
   *          the document file name, used as job id
   */
  public synchronized void markJobAsFailed(String jobId) {

    if (this.inTranslationJobs.remove(jobId)) {
      logger.info("mark job {} as failed", jobId);
      this.failedJobs.add(0, jobId);
      cleanUpJobList(this.failedJobs);
    } else {
      logger.error("unknown in translation job {}", jobId);
    }
  }


  /**
   * Get status for given job.
   *
   * @param jobId
   *          the document file name, used as job id
   * @return the job's status
   */
  public synchronized Status getStatus(String jobId) {

    if (this.queuedJobs.contains(jobId)) {
      return Status.QUEUED;
    }
    if (this.inTranslationJobs.contains(jobId)) {
      return Status.IN_TRANSLATION;
    }
    if (this.finishedJobs.contains(jobId)) {
      return Status.FINISHED;
    }
    if (this.failedJobs.contains(jobId)) {
      return Status.FAILED;
    }
    return Status.UNKONWN;
  }


  /**
   * Check if the given job list contains more than the maximum number of entries. If yes, delete
   * files associated with the oldest jobs.
   *
   * @param jobList
   *          the job list to check
   */
  private void cleanUpJobList(List<String> jobList) {

    try {
      while (jobList.size() > JOB_LIST_SIZE) {
        String fileToDelete = jobList.remove(jobList.size() - 1);
        // delete file associated with oldest job in job list in input folder
        try {
          Files.delete(Paths.get(this.inputFolder).resolve(fileToDelete));
          logger.info("deleted old job input file {} ", fileToDelete);
        } catch (NoSuchFileException e) {
          // nothing to do
        }
        // delete file associated with oldest job in job list in output folder
        try {
          Files.delete(Paths.get(this.outputFolder).resolve(fileToDelete));
          logger.info("deleted old job output file {} ", fileToDelete);
        } catch (NoSuchFileException e) {
          // nothing to do
        }
      }
    } catch (IOException e) {
      logger.error(e.getLocalizedMessage(), e);
    }
  }
}
