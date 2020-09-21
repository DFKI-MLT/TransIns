package de.dfki.mlt.transins;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    this.ws = HttpClient
        .newHttpClient()
        .newWebSocketBuilder()
        .buildAsync(URI.create(url), this)
        .join();

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
  public String send(String query)
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
   * Gracefully close socket connection.
   */
  public void close() {

    this.ws.sendClose(WebSocket.NORMAL_CLOSURE, "disconnect");
  }


  @Override
  public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {

    logger.info("Session closed because of {}", reason);
    return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
  }
}

