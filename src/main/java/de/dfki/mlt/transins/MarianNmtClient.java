package de.dfki.mlt.transins;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.okapi.common.exceptions.OkapiException;

/**
 * Client for Marian NMT server.
 *
 * @author Jörg Steffen, DFKI
 */
public class MarianNmtClient implements WebSocket.Listener {

  private static final Logger logger = LoggerFactory.getLogger(MarianNmtClient.class);

  // number of translation to keep in cache
  private static final int TRANSLATION_CACHE_SIZE = 100;

  // the underlying socket
  private WebSocket ws;
  // the completable future holding the server's translation result
  private CompletableFuture<String> responseFuture;
  // here we build the server's translation result from potentially multiple server responses
  private StringBuilder serverResponse;
  // translation cache
  private Map<String, String> translationCache;


  /**
   * Create a new client instance for the given URL.
   *
   * @param url
   *          the url
   */
  public MarianNmtClient(String url) {

    // init web socket
    int connectionAttempts = 0;
    int maxConnectionAttempts = 3;
    while (true) {
      try {
        this.ws = HttpClient
            .newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(URI.create(url), this)
            .join();
      } catch (CompletionException e) {
        if (connectionAttempts == maxConnectionAttempts) {
          logger.error(e.getLocalizedMessage());
          throw new OkapiException(e);
        }
        connectionAttempts++;
        logger.warn("{}, retrying in 5 seconds...", e.getCause().getMessage());
        try {
          TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
        continue;
      }
      // connection successful
      break;
    }

    // init translation cache
    this.translationCache =
        Collections.synchronizedMap(
            new LinkedHashMap<String, String>(TRANSLATION_CACHE_SIZE + 1, .75F, true) {

              @Override
              public boolean removeEldestEntry(Map.Entry<String, String> eldest) {

                return size() > TRANSLATION_CACHE_SIZE;
              }
            });
  }


  @Override
  public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {

    this.serverResponse.append(data.toString());
    if (last) {
      this.responseFuture.complete(this.serverResponse.toString());
    }
    return WebSocket.Listener.super.onText(webSocket, data, last);
  }


  @Override
  public void onError(WebSocket webSocket, Throwable error) {

    logger.error(error.getLocalizedMessage(), error);
  }


  /**
   * Send the given query text for translation to the Marian NMT server.
   *
   * @param query
   *          the query text
   * @return the query's translation
   * @throws InterruptedException
   *           if current thread was interrupted while waiting for response future
   * @throws ExecutionException
   *           if response future completed exceptionally
   */
  public String translate(String query)
      throws InterruptedException, ExecutionException {

    // use cached translation if available
    String cachedTranslation = this.translationCache.get(query);
    if (cachedTranslation != null) {
      return cachedTranslation;
    }

    // create new completable future that will hold the translation result
    this.responseFuture = new CompletableFuture<String>();
    this.serverResponse = new StringBuilder();
    this.ws.sendText(query, true);
    // this call blocks until the translation is available
    String translation = this.responseFuture.get();
    this.translationCache.put(query, translation);
    return translation;
  }


  /**
   * Send the given query texts for translation to the Marian NMT server.
   *
   * @param queries
   *          the query texts
   * @return map of query texts to their translation
   * @throws InterruptedException
   *           if current thread was interrupted while waiting for response future
   * @throws ExecutionException
   *           if response future completed exceptionally
   */
  public Map<String, String> bulkTranslate(List<String> queries)
      throws InterruptedException, ExecutionException {

    // remove duplicates, but keep order
    Set<String> controlSet = new HashSet<>();
    List<String> uniqueQueries = new ArrayList<>();
    for (String oneQuery : queries) {
      if (controlSet.add(oneQuery)) {
        uniqueQueries.add(oneQuery);
      }
    }

    StringBuilder queryString = new StringBuilder();
    for (String oneQuery : uniqueQueries) {
      queryString.append(oneQuery).append("\n");
    }

    // create new completable future that will hold the translation result
    this.responseFuture = new CompletableFuture<String>();
    this.serverResponse = new StringBuilder();
    this.ws.sendText(queryString.toString().strip(), true);
    // this call blocks until the translation is available
    String[] translations = this.responseFuture.get().split("\n");

    Map<String, String> translationsMap = new LinkedHashMap<>();
    for (int i = 0; i < uniqueQueries.size(); i++) {
      translationsMap.put(uniqueQueries.get(i), translations[i]);
    }

    return translationsMap;
  }


  /**
   * Gracefully close socket connection.
   */
  public void close() {

    this.ws.sendClose(WebSocket.NORMAL_CLOSURE, "disconnect");
  }


  @Override
  public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {

    logger.info("Session closed because of {}", reason);
    // if future is not already completed, something went wrong, so complete with exception
    if (this.responseFuture.completeExceptionally(new OkapiException("translation failed"))) {
      logger.error("translation failed");
    }
    return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
  }
}

