package de.dfki.mlt.transins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide token based alignments based on Marian hard alignments.
 *
 * @author Jörg Steffen, DFKI
 */
public class HardAlignments implements Alignments {

  private static final Logger logger = LoggerFactory.getLogger(HardAlignments.class);

  // mapping of target token index to list of source token indexes
  private Map<Integer, List<Integer>> target2sourcesMapping;


  /**
   * Create a new hard alignment instance based on the given raw hard alignments.
   *
   * @param rawAlignments
   *          the raw hard alignments, as provided by Marian NMT,
   *          from source token index to target token index
   * @throws NumberFormatException
   *           if raw alignments not as excepted
   */
  public HardAlignments(String rawAlignments) {

    if (rawAlignments.trim().isEmpty()) {
      // no alignments provided
      return;
    }

    this.target2sourcesMapping = new TreeMap<>();
    String[] algnPairs = rawAlignments.split(" ");

    for (String oneAlgnPair : algnPairs) {
      String[] indices = oneAlgnPair.split("-");
      if (indices.length != 2) {
        continue;
      }
      int sourceIndex = Integer.parseInt(indices[0]);
      int targetIndex = Integer.parseInt(indices[1]);
      List<Integer> sourceIndexes = this.target2sourcesMapping.get(targetIndex);
      if (sourceIndexes == null) {
        sourceIndexes = new ArrayList<>();
        this.target2sourcesMapping.put(targetIndex, sourceIndexes);
      }
      sourceIndexes.add(sourceIndex);
    }

    // sort source token indexes
    for (List<Integer> oneSourceTokenIndexList : this.target2sourcesMapping.values()) {
      Collections.sort(oneSourceTokenIndexList);
    }
  }


  /**
   * Shift all source indexes by the given offset.
   *
   * @param offset
   *          the offset to shift
   */
  @Override
  public void shiftSourceIndexes(int offset) {

    Map<Integer, List<Integer>> tempTarget2sourcesMapping = new TreeMap<>();

    for (var oneEntry : this.target2sourcesMapping.entrySet()) {
      List<Integer> newSourceIndexes = new ArrayList<>();
      for (int oneOldSourceIndex : oneEntry.getValue()) {
        int sourceIndexWithOffset = oneOldSourceIndex + offset;
        if (sourceIndexWithOffset < 0) {
          logger.warn("shifting source index resulted in negative index, ignoring this index");
        } else {
          newSourceIndexes.add(sourceIndexWithOffset);
        }
      }
      if (!newSourceIndexes.isEmpty()) {
        tempTarget2sourcesMapping.put(oneEntry.getKey(), newSourceIndexes);
      }
    }

    this.target2sourcesMapping = tempTarget2sourcesMapping;
  }


  /**
   * Shift all target indexes by the given offset.
   *
   * @param offset
   *          the offset to shift
   */
  @Override
  public void shiftTargetIndexes(int offset) {

    Map<Integer, List<Integer>> tempTarget2sourcesMapping = new TreeMap<>();

    for (var oneEntry : this.target2sourcesMapping.entrySet()) {
      int targetIndexWithOffset = oneEntry.getKey() + offset;
      if (targetIndexWithOffset < 0) {
        logger.warn("shifting target index resulted in negative index, ignoring this index");
      } else {
        tempTarget2sourcesMapping.put(oneEntry.getKey() + offset, oneEntry.getValue());
      }
    }

    this.target2sourcesMapping = tempTarget2sourcesMapping;
  }


  /**
   * @return the mapping of target token index to list of source token indexes
   */
  public Map<Integer, List<Integer>> getTarget2SourcesMapping() {

    return this.target2sourcesMapping;
  }


  /**
   * @param targetTokenIndex
   *          the target token index in the translation
   * @return the aligned source token indexes, sorted;
   *         empty if no source token is aligned with target token
   */
  @Override
  public List<Integer> getSourceTokenIndexes(int targetTokenIndex) {

    List<Integer> sourceTokenIndexes = this.target2sourcesMapping.get(targetTokenIndex);
    if (sourceTokenIndexes == null) {
      return Collections.emptyList();
    }
    return sourceTokenIndexes;
  }


  @Override
  public List<Integer> getPointedSourceTokens() {

    Set<Integer> collectedPointedSourceTokens = new HashSet<>();
    for (var oneEntry : this.target2sourcesMapping.entrySet()) {
      collectedPointedSourceTokens.addAll(oneEntry.getValue());
    }
    List<Integer> sortedPointedSourceTokens = new ArrayList<>(collectedPointedSourceTokens);
    Collections.sort(sortedPointedSourceTokens);

    return sortedPointedSourceTokens;
  }


  /**
   * @return the raw hard alignments, from target token index to source token index;
   *         please note this this is vice versa as provided by Marian NMT
   */
  @Override
  public String toString() {

    StringBuilder result = new StringBuilder();
    for (var oneEntry : this.target2sourcesMapping.entrySet()) {
      int targetIndex = oneEntry.getKey();
      for (var oneSourceIndex : oneEntry.getValue()) {
        result.append(String.format("%d-%d ", targetIndex, oneSourceIndex));
      }
    }

    return result.toString().trim();
  }


  /**
   * @return the raw hard alignments, as provided by Marian NMT,
   *         from source token index to target token index
   */
  public String toStringAsProvidedByMarian() {

    Map<Integer, List<Integer>> source2targetsMapping = new TreeMap<>();

    for (var oneEntry : this.target2sourcesMapping.entrySet()) {
      int targetIndex = oneEntry.getKey();
      for (int oneSourceIndex : oneEntry.getValue()) {
        List<Integer> targetIndexes = source2targetsMapping.get(oneSourceIndex);
        if (targetIndexes == null) {
          targetIndexes = new ArrayList<>();
          source2targetsMapping.put(oneSourceIndex, targetIndexes);
        }
        targetIndexes.add(targetIndex);
      }
    }

    // sort target token indexes
    for (List<Integer> oneTargetTokenIndexList : source2targetsMapping.values()) {
      Collections.sort(oneTargetTokenIndexList);
    }

    StringBuilder result = new StringBuilder();
    for (var oneEntry : source2targetsMapping.entrySet()) {
      int sourceIndex = oneEntry.getKey();
      for (var oneTargetIndex : oneEntry.getValue()) {
        result.append(String.format("%d-%d ", sourceIndex, oneTargetIndex));
      }
    }

    return result.toString().trim();
  }
}
