package de.dfki.mlt.transins;

import java.util.List;

/**
 * Common interface of hard and soft alignments.
 *
 * @author Jörg Steffen, DFKI
 */
public interface Alignments {

  /**
   * Shift all source indexes by the given offset.
   *
   * @param offset
   *          the offset to shift
   */
  void shiftSourceIndexes(int offset);


  /**
   * Shift all target indexes by the given offset.
   *
   * @param offset
   *          the offset to shift
   */
  void shiftTargetIndexes(int offset);


  /**
   * @param targetTokenIndex
   *          the target token index in the translation
   * @return the aligned source token indexes, sorted;
   *         empty if no source token is aligned with target token
   */
  List<Integer> getSourceTokenIndexes(int targetTokenIndex);


  /**
   * @return all source token indexes for which there is at least one target token pointing at them
   */
  List<Integer> getPointedSourceTokens();


  /**
   * @return the raw alignments, as provided by Marian NMT
   */
  @Override
  String toString();
}
