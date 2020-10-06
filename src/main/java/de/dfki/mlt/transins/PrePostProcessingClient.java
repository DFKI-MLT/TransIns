package de.dfki.mlt.transins;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
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
   * Create a new pre-/postprocessing client.
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
      // send sentence and language to server
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
      } else {
        logger.error(EntityUtils.toString(response.getEntity()));
      }
    } catch (URISyntaxException | IOException e) {
      logger.error(e.getLocalizedMessage(), e);
    }

    return sentence;
  }


  /**
   * Process the given sentences in the given language.
   *
   * @param lang
   *          the language
   * @param sentences
   *          the sentences to process
   * @param mode
   *          the {@link Mode}
   * @param host
   *          the pre-/postprocessing host
   * @param port
   *          the pre-/postprocessing port
   * @return map from sentences to processing results
   */
  public Map<String, String> bulkProcess(
      String lang, List<String> sentences, Mode mode, String host, int port) {

    // remove duplicates, but keep order
    Set<String> controlSet = new HashSet<>();
    List<String> uniqueSentences = new ArrayList<>();
    for (String oneQuery : sentences) {
      if (controlSet.add(oneQuery)) {
        uniqueSentences.add(oneQuery);
      }
    }

    Map<String, String> resultMap = new LinkedHashMap<>();

    try {
      // add sentences to JSON array
      JSONArray sentencesAsJsonArray = new JSONArray();
      for (String oneSentence : uniqueSentences) {
        sentencesAsJsonArray.put(oneSentence);
      }

      // send sentences and language to server
      StringEntity requestEntity =
          new StringEntity(sentencesAsJsonArray.toString(), ContentType.APPLICATION_JSON);
      URI uri = new URI(
          "http",
          null,
          host,
          port,
          String.format(Locale.US, "/%s",
              mode == Mode.PREPROCESS ? "preprocess" : "postprocess"),
          String.format(Locale.US, "lang=%s", lang),
          null);
      HttpPost postMethod = new HttpPost(uri);
      postMethod.setEntity(requestEntity);

      HttpResponse response = this.httpClient.execute(postMethod);
      int status = response.getStatusLine().getStatusCode();
      if (status >= 200 && status < 300) {
        HttpEntity entity = response.getEntity();
        if (entity != null) {
          String jsonResultString = EntityUtils.toString(entity);
          JSONArray jsonArray = new JSONArray(jsonResultString);
          for (int i = 0; i < jsonArray.length(); i++) {
            resultMap.put(uniqueSentences.get(i), jsonArray.getString(i));
          }
        }
      } else {
        logger.error(EntityUtils.toString(response.getEntity()));
      }

    } catch (URISyntaxException | IOException e) {
      logger.error(e.getLocalizedMessage());
    }

    return resultMap;
  }
}
