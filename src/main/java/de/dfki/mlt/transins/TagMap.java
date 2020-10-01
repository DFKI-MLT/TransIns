package de.dfki.mlt.transins;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Bidirectional map from opening to closing tags and vice versa.
 *
 * @author Jörg Steffen, DFKI
 */
public class TagMap {

  private Map<String, String> closing2OpeningTag;
  private Map<String, String> opening2ClosingTag;


  /**
   * Create a new tag map.
   */
  public TagMap() {

    this.opening2ClosingTag = new LinkedHashMap<>();
    this.closing2OpeningTag = new LinkedHashMap<>();
  }


  /**
   * Add the given mapping between opening and closing tag.
   *
   * @param openingTag
   *          the opening tag
   * @param closingTag
   *          the closing tag
   */
  public void put(String openingTag, String closingTag) {

    this.opening2ClosingTag.put(openingTag, closingTag);
    this.closing2OpeningTag.put(closingTag, openingTag);
  }


  /**
   * Get the closing tag for the the given opening tag.
   *
   * @param openingTag
   *          the opening tag
   * @return the associated closing tag or {@code null} if not available
   */
  public String getClosingTag(String openingTag) {

    return this.opening2ClosingTag.get(openingTag);
  }


  /**
   * Get the closing tag for the the given opening tag.
   *
   * @param closingTag
   *          the closing tag
   * @return the associated opening tag or {@code null} if not available
   */
  public String getOpeningTag(String closingTag) {

    return this.closing2OpeningTag.get(closingTag);
  }


  /**
   * @return entry set where opening tags are the keys and closing tags are the values.
   */
  public Set<Entry<String, String>> entrySet() {

    return this.opening2ClosingTag.entrySet();
  }


  /**
   * @return number of stored tag pairs
   */
  public int size() {

    return this.opening2ClosingTag.size();
  }
}
