package de.dfki.mlt.transins.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.fileupload.util.LimitedInputStream;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.dfki.mlt.transins.MarkupInserter.MarkupStrategy;
import de.dfki.mlt.transins.Translator;
import de.dfki.mlt.transins.server.Job.Status;

/**
 * TransIns service providing methods to upload files to translate and retrieving the translation.
 *
 * @author Jörg Steffen, DFKI
 */
@Path("/")
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class TransInsService {

  private static final Logger logger = LoggerFactory.getLogger(TransInsService.class);

  private static final String INPUT_FOLDER = "incoming";
  private static final String OUTPUT_FOLDER = "outgoing";

  // the translator
  private static Translator translator;

  ///the job manager
  private static JobManager jobManager;

  // create random ids for uploaded files
  private static RandomStringGenerator random;

  // thread pool for running the translation
  private static ExecutorService executorService;

  // server configuration
  private static PropertiesConfiguration config;


  /**
   * Initialize TransIns service using the given configuration.
   *
   * @param serverConfig
   *          the configuration
   * @throws IOException
   *           if creation of input or output folders fails
   */
  public static void init(PropertiesConfiguration serverConfig)
      throws IOException {

    translator = new Translator();
    jobManager = new JobManager(INPUT_FOLDER, OUTPUT_FOLDER);
    random = new RandomStringGenerator(40);
    executorService = Executors.newFixedThreadPool(1);
    config = serverConfig;
    Files.createDirectories(Paths.get(INPUT_FOLDER));
    Files.createDirectories(Paths.get(OUTPUT_FOLDER));
  }


  /**
   * @return the supported translation directions as JSON array
   */
  @Path("/getTranslationDirections")
  @GET
  @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
  public static Response getTranslationDirections() {

    try {
      ObjectMapper mapper = new ObjectMapper();
      ResponseBuilder response =
          Response.accepted(mapper.writeValueAsString(
              config.getList(String.class, ConfigKeys.SUPPORTED_TRANS_DIRS)));
      if (config.getBoolean(ConfigKeys.DEVELOPMENT_MODE)) {
        response.header("Access-Control-Allow-Origin", "*");
      }
      return response.build();
    } catch (JsonProcessingException e) {
      logger.error(e.getLocalizedMessage(), e);
      return createResponse(500, e.getMessage());
    }
  }


  /**
   * Translate the document with the given encoding that is read from the given input stream from
   * the given source language to the given target language. Target document will use the same
   * encoding.
   *
   * @param inputStream
   *          the stream to read the document from
   * @param fileDisposition
   *          form-data content disposition header
   * @param encoding
   *          the document encoding,
   *          as given by the form parameter 'enc'
   * @param transDir
   *          the translation direction,
   *          as given by the form parameter 'transDir'
   * @param markupStrategyString
   *          the markup re-insertion strategy to use, defaults to COMPLETE_MAPPING,
   *          as given by the form parameter 'strategy'
   * @return a job id to be used to retrieve the translation via {@link #getTranslation(String)}
   */
  @Path("/translate")
  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.TEXT_PLAIN + ";charset=utf-8")
  @SuppressWarnings("checkstyle:IllegalCatch")
  public static Response translate(
      @FormDataParam("file") InputStream inputStream,
      @FormDataParam("file") FormDataContentDisposition fileDisposition,
      @FormDataParam("enc") String encoding,
      @FormDataParam("transDir") String transDir,
      @DefaultValue("COMPLETE_MAPPING") @FormDataParam("strategy") String markupStrategyString) {

    // reject job if too many jobs in queue
    if (jobManager.getQueuedJobsCount() > config.getInt(ConfigKeys.MAX_QUEUE_SIZE)) {
      logger.error("server busy");
      return createResponse(503, "server busy");
    }

    // check for valid query parameters
    String errorMessage =
        checkQueryParameters(
            fileDisposition, transDir, encoding, markupStrategyString);
    if (errorMessage != null) {
      logger.error(errorMessage);
      return createResponse(400, errorMessage);
    }

    // extract file extension
    String originalFileName = fileDisposition.getFileName();
    String fileExtension = Utils.getFileExtension(originalFileName);

    // create random job id; this is returned to caller and used to retrieve translation
    String jobId = random.nextString();
    // create input file name based on job id; this is only used internally
    String internalFileName =
        String.format("%s_%s.%s", jobId, transDir, fileExtension);
    logger.info("receiving source document \"{}\"", internalFileName);

    // write content from input stream to file
    final java.nio.file.Path sourcePath = Paths.get(INPUT_FOLDER).resolve(internalFileName);
    final java.nio.file.Path targetPath = Paths.get(OUTPUT_FOLDER).resolve(internalFileName);
    int maxFileSize = config.getInt(ConfigKeys.MAX_FILE_SIZE);
    LimitedInputStream limitedInputStream =
        new LimitedInputStream(inputStream, maxFileSize * 1024 * 1024) {

          @Override
          protected void raiseError(long sizeMax, long count)
              throws IOException {

            throw new FileTooLargeException(
                String.format("file too large, max size is %dMB ", maxFileSize));
          }
        };
    try (limitedInputStream) {
      java.nio.file.Files.copy(
          limitedInputStream,
          sourcePath,
          StandardCopyOption.REPLACE_EXISTING);
      // check if file actually contains content
      if (Files.size(sourcePath) == 0) {
        logger.error("empty file");
        return createResponse(400, "empty file");
      }
    } catch (FileTooLargeException e) {
      logger.error(e.getLocalizedMessage());
      // delete partially saved input file
      try {
        Files.delete(sourcePath);
        logger.info("too large, deleted input file: {} ", sourcePath);
      } catch (NoSuchFileException ex) {
        // nothing to do
      } catch (IOException ex) {
        logger.error(e.getLocalizedMessage(), ex);
      }
      return createResponse(400, e.getMessage());
    } catch (IOException e) {
      logger.error(e.getLocalizedMessage(), e);
      return createResponse(503, e.getMessage());
    }

    String[] transDirParts = transDir.split("-");
    String sourceLang = transDirParts[0];
    String targetLang = transDirParts[1];
    jobManager.addJobToQueue(
        new Job(
            jobId, originalFileName, internalFileName,
            sourceLang, targetLang, encoding,
            MarkupStrategy.valueOf(markupStrategyString)));

    // run translation in new thread;
    // because executor service thread pool only contains a single thread, it is guaranteed that
    // translations never run in parallel
    executorService.submit(new Callable<Boolean>() {

      @Override
      public Boolean call() {

        if (jobManager.getStatus(jobId) != Status.QUEUED) {
          // job was cancelled, nothing to do
          return true;
        }

        try {
          jobManager.markJobAsInTranslation(jobId);
          translator.translateWithMarianNmt(
              sourcePath.toAbsolutePath().toString(), sourceLang, encoding,
              targetPath.toAbsolutePath().toString(), targetLang, encoding,
              true, MarkupStrategy.valueOf(markupStrategyString), true);
          jobManager.markJobAsFinished(jobId);
        } catch (Throwable e) {
          jobManager.markJobAsFailed(jobId);
          logger.error(e.getLocalizedMessage(), e);
        }

        return true;
      }
    });

    ResponseBuilder response = Response.accepted(jobId);
    if (config.getBoolean(ConfigKeys.DEVELOPMENT_MODE)) {
      response.header("Access-Control-Allow-Origin", "*");
    }
    return response.build();
  }


  /**
   * Retrieve the translation of the document with the given job id.
   *
   * @param jobId
   *          the job id
   * @return the translated document
   */
  @Path("/getTranslation/{jobId}")
  @GET
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public static Response getTranslation(@PathParam("jobId") String jobId) {

    Job.Status status = jobManager.getStatus(jobId);
    switch (status) {
      case QUEUED:
        return createResponse(202, "document still queued for translation");
      case IN_TRANSLATION:
        return createResponse(202, "document is currently being translated");
      case FAILED:
        return createResponse(404, "document translation failed");
      case UNKONWN:
        return createResponse(404, "unknown job id");
      case FINISHED:
        java.nio.file.Path translation =
            Paths.get(OUTPUT_FOLDER).resolve(jobManager.getInternalFileName(jobId));
        ResponseBuilder response =
            Response.ok(translation.toFile(), MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition",
                    String.format("attachment; filename=\"%s\"",
                        jobManager.getResultFileName(jobId)));
        if (config.getBoolean(ConfigKeys.DEVELOPMENT_MODE)) {
          response.header("Access-Control-Allow-Origin", "*")
              .header("Access-Control-Expose-Headers", "*");
        }
        return response.build();
      default:
        return createResponse(500, String.format("unknown job status \"%s\"", status));
    }
  }


  /**
   * Delete files associated with the given job id. Cancel translation if job is still in queue.
   *
   * @param jobId
   *          the job id
   * @return the response
   */
  @Path("/deleteTranslation/{jobId}")
  @DELETE
  public static Response deleteTranslation(@PathParam("jobId") String jobId) {

    Job.Status status = jobManager.getStatus(jobId);
    switch (status) {
      case QUEUED:
      case FINISHED:
      case FAILED:
        jobManager.deleteJob(jobId);
        return createResponse(204, "job deleted");
      case IN_TRANSLATION:
        return createResponse(406, "document is currently being translated");
      case UNKONWN:
        return createResponse(404, "unknown job id");
      default:
        return createResponse(500, String.format("unknown job status \"%s\"", status));
    }
  }


  /**
   * Handle OPTIONS request sent prior to an DELETE request in case of cross-origin resource
   * sharing. Only used in development mode.
   *
   * @param jobId
   *          the job id
   * @return the response
   */
  @Path("/deleteTranslation/{jobId}")
  @OPTIONS
  public static Response deleteTranslationOptions(@PathParam("jobId") String jobId) {

    Response response = Response.ok("test3 with OPTIONS, body content")
        .header("Access-Control-Allow-Origin", "*")
        .header("Access-Control-Expose-Headers", "*")
        .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
        .build();
    return response;
  }


  /**
   * Check validity of given query parameters.
   *
   * @param fileDisposition
   *          the form data content disposition
   * @param transDir
   *          the translation direction
   * @param encoding
   *          the encoding
   * @param markupStrategyString
   *          the markup re-insertion strategy
   * @return error message if a parameter is not valid, <code>null</code> if all parameters are
   *         valid
   */
  private static String checkQueryParameters(
      FormDataContentDisposition fileDisposition,
      String transDir, String encoding, String markupStrategyString) {

    // translation direction
    if (!config.getList(String.class, ConfigKeys.SUPPORTED_TRANS_DIRS).contains(transDir)) {
      return String.format("unsupported translation direction %s", transDir);
    }
    // document type
    String originalFileName = fileDisposition.getFileName();
    if (originalFileName == null) {
      return "no file provided";
    }
    String fileExtension = Utils.getFileExtension(originalFileName);
    if (!translator.getSupportedExtensions().contains(fileExtension.toLowerCase())) {
      return String.format("unsupported file extension %s", fileExtension);
    }
    // encoding
    try {
      Charset.forName(encoding);
    } catch (UnsupportedCharsetException e) {
      return String.format("unsupported encoding %s", encoding);
    }
    // markup re-insertion strategy
    try {
      MarkupStrategy.valueOf(markupStrategyString);
    } catch (IllegalArgumentException e) {
      return String.format("unsupported markup re-insertion strategy %s", markupStrategyString);
    }

    // all parameters valid
    return null;
  }


  /**
   * Test method that checks if the server is still running.
   *
   * @return alive message
   */
  @Path("/alive")
  @GET
  @Produces(MediaType.TEXT_PLAIN + ";charset=utf-8")
  public static synchronized Response alive() {

    ResponseBuilder response = Response.ok("TransIns server is alive");
    if (config.getBoolean(ConfigKeys.DEVELOPMENT_MODE)) {
      response.header("Access-Control-Allow-Origin", "*");
    }
    return response.build();
  }


  /**
   * Create a response with the given status and message.
   *
   * @param status
   *          the status
   * @param message
   *          the message
   * @return the response
   */
  private static Response createResponse(int status, String message) {

    ResponseBuilder response = Response.status(status, message);
    if (config.getBoolean(ConfigKeys.DEVELOPMENT_MODE)) {
      response.header("Access-Control-Allow-Origin", "*");
    }
    return response.build();
  }


  /**
   * Custom IO exception to handle uploaded files that are too large.
   */
  static class FileTooLargeException extends IOException {

    private static final long serialVersionUID = 1L;


    /**
     * Create a new exception.
     */
    FileTooLargeException() {

      super("file too large");
    }


    /**
     * Create a new exception with the given message.
     *
     * @param message
     *          the message
     */
    FileTooLargeException(String message) {

      super(message);
    }
  }
}
