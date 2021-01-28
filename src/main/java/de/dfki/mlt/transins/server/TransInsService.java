package de.dfki.mlt.transins.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dfki.mlt.transins.MarkupInserter.MarkupStrategy;
import de.dfki.mlt.transins.Translator;

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

  // supported language pairs
  private static List<String> suppportedLangPairs;


  /**
   * Initialize TransIns service using the given configuration.
   *
   * @param config
   *          the configuration
   *
   * @throws IOException
   *           if creation of input or output folders fails
   */
  public static void init(PropertiesConfiguration config) throws IOException {

    translator = new Translator();
    jobManager = new JobManager(INPUT_FOLDER, OUTPUT_FOLDER);
    random = new RandomStringGenerator(40);
    executorService = Executors.newFixedThreadPool(1);
    Files.createDirectories(Paths.get(INPUT_FOLDER));
    Files.createDirectories(Paths.get(OUTPUT_FOLDER));
    suppportedLangPairs = config.getList(String.class, ConfigKeys.SUPPORTED_LANG_PAIRS);
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
   * @param sourceLang
   *          the source language,
   *          as given by the form parameter 'sl'
   * @param targetLang
   *          the target language,
   *          as given by the form parameter 'tl'
   * @param markupStrategyString
   *          the markup re-insertion strategy to use, defaults to COMPLETE_MAPPING,
   *          as given by the form parameter 'strategy'
   * @return an id to be used to retrieve the translation via {@link #getTranslation(String)}
   */
  @Path("/transins/translate")
  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.TEXT_PLAIN)
  @SuppressWarnings("checkstyle:IllegalCatch")
  public static Response translate(
      @FormDataParam("file") InputStream inputStream,
      @FormDataParam("file") FormDataContentDisposition fileDisposition,
      @FormDataParam("enc") String encoding,
      @FormDataParam("sl") String sourceLang, @FormDataParam("tl") String targetLang,
      @DefaultValue("COMPLETE_MAPPING") @FormDataParam("strategy") String markupStrategyString) {

    // extract file extension
    String originalFileName = fileDisposition.getFileName();
    String fileExtension = Utils.getFileExtension(originalFileName);

    // check for valid query parameters
    String errorMessage =
        checkQueryParameters(sourceLang, targetLang, fileExtension, encoding, markupStrategyString);
    if (errorMessage != null) {
      logger.error(errorMessage);
      return Response.status(400, errorMessage).build();
    }

    // create random job id; this is returned to caller and used to retrieve translation
    String jobId = random.nextString();
    // create input file name based on job id; this is only used internally
    String internalFileName =
        String.format("%s_%s-%s.%s", jobId, sourceLang, targetLang, fileExtension);
    logger.info("receiving source document \"{}\"", internalFileName);

    // write content from input stream to file
    final java.nio.file.Path sourcePath = Paths.get(INPUT_FOLDER).resolve(internalFileName);
    final java.nio.file.Path targetPath = Paths.get(OUTPUT_FOLDER).resolve(internalFileName);
    try (inputStream) {
      java.nio.file.Files.copy(
          inputStream,
          sourcePath,
          StandardCopyOption.REPLACE_EXISTING);
      // check if file actually contains content
      if (Files.size(sourcePath) == 0) {
        logger.error("empty file");
        return Response.status(400, "empty file").build();
      }
    } catch (IOException e) {
      logger.error(e.getLocalizedMessage(), e);
      return Response.status(Status.SERVICE_UNAVAILABLE).build();
    }

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

        try {
          jobManager.markJobAsInTranslation(jobId);
          translator.translate(
              sourcePath.toAbsolutePath().toString(), sourceLang, encoding,
              targetPath.toAbsolutePath().toString(),
              targetLang, encoding, Translator.TransId.MARIAN_BATCH,
              MarkupStrategy.valueOf(markupStrategyString), true);
          jobManager.markJobAsFinished(jobId);
        } catch (Throwable e) {
          jobManager.markJobAsFailed(jobId);
          logger.error(e.getLocalizedMessage(), e);
        }

        return true;
      }
    });

    return Response.accepted(jobId).build();
  }


  /**
   * Retrieve the translation of the document with the given job id.
   *
   * @param jobId
   *          the job id
   * @return the translated document
   */
  @Path("/transins/getTranslation/{jobId}")
  @GET
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public static Response getTranslation(@PathParam("jobId") String jobId) {

    Job.Status status = jobManager.getStatus(jobId);
    switch (status) {
      case QUEUED:
        return Response.status(404, "document still queued for translation").build();
      case IN_TRANSLATION:
        return Response.status(404, "document is currently being translated").build();
      case FAILED:
        return Response.status(404, "document translation failed").build();
      case UNKONWN:
        return Response.status(404, "unknown job id").build();
      case FINISHED:
        java.nio.file.Path translation =
            Paths.get(OUTPUT_FOLDER).resolve(jobManager.getInternalFileName(jobId));
        return Response.ok(translation.toFile(), MediaType.APPLICATION_OCTET_STREAM)
            .header("Content-Disposition",
                String.format("attachment; filename=\"%s\"", jobManager.getResultFileName(jobId)))
            .build();
      default:
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }
  }


  /**
   * Check validity of given query parameters.
   *
   * @param sourceLang
   *          the source language
   * @param targetLang
   *          the target language
   * @param fileExtension
   *          the document type, usually indicated by the file extension
   * @param encoding
   *          the encoding
   * @param markupStrategyString
   *          the markup re-insertion strategy
   * @return error message if a parameter is not valid, <code>null</code> if all parameters are
   *         valid
   */
  private static String checkQueryParameters(
      String sourceLang, String targetLang, String fileExtension, String encoding,
      String markupStrategyString) {

    // supported language pairs
    String queryLangPair =
        String.format("%s-%s", sourceLang.toLowerCase(), targetLang.toLowerCase());
    if (!suppportedLangPairs.contains(queryLangPair)) {
      return String.format("unsupported language pair %s", queryLangPair);
    }
    // document type
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
  @Path("/transins/alive")
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public static synchronized Response alive() {

    return Response.ok("TransIns server is alive").build();
  }
}
