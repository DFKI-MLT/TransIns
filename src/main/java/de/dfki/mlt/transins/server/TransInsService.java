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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.configuration2.PropertiesConfiguration;
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
   * Translate the document with the given type and encoding that is read from the given stream from
   * the given source language to the given target language. Target document will use the same
   * encoding.
   *
   * @param inputStream
   *          the stream to read the document from
   * @param fileExtension
   *          the document type, usually indicated by the file extension,
   *          as given by the query parameter 'ext'
   * @param encoding
   *          the document encoding,
   *          as given by the query parameter 'enc'
   * @param sourceLang
   *          the source language,
   *          as given by the query parameter 'sl'
   * @param targetLang
   *          the target language,
   *          as given by the query parameter 'tl'
   * @param markupStrategyString
   *          the markup re-insertion strategy to use, defaults to COMPLETE_MAPPING
   * @return an id to be used to retrieve the translation via {@link #getTranslation(String)}
   */
  @Path("/transins/translate")
  @POST
  @Consumes(MediaType.APPLICATION_OCTET_STREAM)
  @Produces(MediaType.TEXT_PLAIN)
  @SuppressWarnings("checkstyle:IllegalCatch")
  public static Response translate(
      InputStream inputStream,
      @QueryParam("ext") String fileExtension, @QueryParam("enc") String encoding,
      @QueryParam("sl") String sourceLang, @QueryParam("tl") String targetLang,
      @DefaultValue("COMPLETE_MAPPING") @QueryParam("strategy") String markupStrategyString) {

    // create random document name;
    // this is returned to caller and used as file name to store document in input folder
    final String docId =
        String.format("%s_%s-%s.%s", random.nextString(), sourceLang, targetLang, fileExtension);
    logger.info("receiving source document \"{}\"", docId);

    // check for valid query parameters:
    // supported language pairs
    String queryLangPair =
        String.format("%s-%s", sourceLang.toLowerCase(), targetLang.toLowerCase());
    if (!suppportedLangPairs.contains(queryLangPair)) {
      String errorMessage = String.format("unsupported language pair %s", queryLangPair);
      logger.error(errorMessage);
      return Response.status(400, errorMessage).build();
    }
    // document type
    if (!translator.getSupportedExtensions().contains(fileExtension.toLowerCase())) {
      String errorMessage = String.format("unsupported file extension %s", fileExtension);
      logger.error(errorMessage);
      return Response.status(400, errorMessage).build();
    }
    // encoding
    try {
      Charset.forName(encoding);
    } catch (UnsupportedCharsetException e) {
      String errorMessage = String.format("unsupported encoding %s", encoding);
      logger.error(errorMessage);
      return Response.status(400, errorMessage).build();
    }
    // markup re-insertion strategy
    MarkupStrategy markupStrategy;
    try {
      markupStrategy = MarkupStrategy.valueOf(markupStrategyString);
    } catch (IllegalArgumentException e) {
      String errorMessage =
          String.format("unsupported markup re-insertion strategy %s", markupStrategyString);
      logger.error(errorMessage);
      return Response.status(400, errorMessage).build();
    }

    // write content from input stream to source file
    final java.nio.file.Path sourcePath = Paths.get(INPUT_FOLDER).resolve(docId);
    final java.nio.file.Path targetPath = Paths.get(OUTPUT_FOLDER).resolve(docId);
    try (inputStream) {
      java.nio.file.Files.copy(
          inputStream,
          sourcePath,
          StandardCopyOption.REPLACE_EXISTING);
      // check if file actually contains content
      if (Files.size(sourcePath) == 0) {
        String errorMessage = "empty file";
        logger.error(errorMessage);
        return Response.status(400, errorMessage).build();
      }
    } catch (IOException e) {
      logger.error(e.getLocalizedMessage(), e);
      return Response.status(Status.SERVICE_UNAVAILABLE).build();
    }


    jobManager.addJobToQueue(docId);

    // run translation in new thread;
    // because executor service thread pool only contains a single thread, it is guaranteed that
    // translations never run in parallel
    executorService.submit(new Callable<Boolean>() {

      @Override
      public Boolean call() {

        try {
          jobManager.markJobAsInTranslation(docId);
          translator.translate(
              sourcePath.toAbsolutePath().toString(), sourceLang, encoding,
              targetPath.toAbsolutePath().toString(),
              targetLang, encoding, Translator.TransId.MARIAN_BATCH, markupStrategy, true);
          jobManager.markJobAsFinished(docId);
        } catch (Throwable e) {
          jobManager.markJobAsFailed(docId);
          logger.error(e.getLocalizedMessage(), e);
        }

        return true;
      }
    });

    return Response.accepted(docId).build();
  }


  /**
   * Retrieve the translation of the document with the given id.
   *
   * @param docId
   *          the document id
   * @return the translated document
   */
  @Path("/transins/getTranslation/{docId}")
  @GET
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public static Response getTranslation(@PathParam("docId") String docId) {

    JobManager.Status status = jobManager.getStatus(docId);
    switch (status) {
      case QUEUED:
        return Response.status(404, "document still queued for translation").build();
      case IN_TRANSLATION:
        return Response.status(404, "document is currently being translated").build();
      case FAILED:
        return Response.status(404, "document translation failed").build();
      case UNKONWN:
        return Response.status(404, "unknown document").build();
      case FINISHED:
        java.nio.file.Path translation = Paths.get(OUTPUT_FOLDER).resolve(docId);
        return Response.ok(translation.toFile(), MediaType.APPLICATION_OCTET_STREAM)
            .header("Content-Disposition",
                String.format("attachment; filename=\"%s\"", translation.getFileName()))
            .build();
      default:
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }
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
