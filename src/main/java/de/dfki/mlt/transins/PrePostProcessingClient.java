package de.dfki.mlt.transins;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Client to talk to the pre-/postprocessing server.
 *
 * @author Jörg Steffen, DFKI
 */
public class PrePostProcessingClient {

  private static final Logger logger = LoggerFactory.getLogger(PrePostProcessingClient.class);

  /** processing modes; either preprocess or postprocess */
  public enum Mode {

    /** preprocess */
    PREPROCESS,

    /** postprocess */
    POSTPROCESS
  }


  private HttpClient httpClient;


  /**
   * Creates a new pre-/postprocessing client.
   */
  public PrePostProcessingClient() {

    this.httpClient = HttpClients.createDefault();
  }


  /**
   * Process the given sentence in the given language.
   *
   * @param lang
   *          the language
   * @param sentence
   *          the sentence to process
   * @param mode
   *          the {@link Mode}
   * @param host
   *          the pre-/postprocessing host
   * @param port
   *          the pre-/postprocessing port
   * @return processing result
   */
  public String process(String lang, String sentence, Mode mode, String host, int port) {

    try {
      // send query and threshold to similarity server
      URL url = new URL(
          "http",
          host,
          port,
          String.format(Locale.US, "/%s?lang=%s&sentence=%s",
              mode == Mode.PREPROCESS ? "preprocess" : "postprocess",
              lang,
              URLEncoder.encode(sentence, StandardCharsets.UTF_8.toString())));
      HttpGet getMethod = new HttpGet(url.toURI());
      HttpResponse response = this.httpClient.execute(getMethod);
      int status = response.getStatusLine().getStatusCode();
      if (status >= 200 && status < 300) {
        HttpEntity entity = response.getEntity();
        if (entity != null) {
          return EntityUtils.toString(entity);
        }
      }
    } catch (URISyntaxException | IOException e) {
      logger.error(e.getLocalizedMessage(), e);
    }

    return sentence;
  }
}
