package de.dfki.mlt.transins.server;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * Utility class to create random strings.
 *
 * @author Jörg Steffen, DFKI
 */
public class RandomStringGenerator {

  private static final String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final String lower = upper.toLowerCase(Locale.ROOT);
  private static final String digits = "0123456789";
  private static final String alphanum = upper + lower + digits;

  private final Random random;
  private final char[] symbols;
  private final char[] resultBuffer;


  /**
   * Create a random string generator that generates random strings of the given length consisting
   * of the given symbols using the given random number generator.
   *
   * @param length
   *          the generated random string length
   * @param random
   *          the random number generator to use
   * @param symbols
   *          the symbols to use
   */
  public RandomStringGenerator(int length, Random random, String symbols) {

    if (length < 1) {
      throw new IllegalArgumentException();
    }
    if (symbols.length() < 2) {
      throw new IllegalArgumentException();
    }
    this.random = Objects.requireNonNull(random);
    this.symbols = symbols.toCharArray();
    this.resultBuffer = new char[length];
  }


  /**
   * Create a random string generator that generates random strings of the given length consisting
   * of alphanumeric characters using the given random number generator.
   *
   * @param length
   *          the generated random string length
   * @param random
   *          the random number generator to use
   */
  public RandomStringGenerator(int length, Random random) {

    this(length, random, alphanum);
  }


  /**
   * Create a random string generator that generates random strings of the given length consisting
   * of alphanumeric characters using a secure random generator.
   *
   * @param length
   *          the generated random string length
   */
  public RandomStringGenerator(int length) {

    this(length, new SecureRandom());
  }


  /**
   * Create a random string generator for secure alphanumeric identifiers of length 21.
   */
  public RandomStringGenerator() {

    this(21);
  }


  /**
   * @return random string
   */
  public String nextString() {

    for (int i = 0; i < this.resultBuffer.length; ++i) {
      this.resultBuffer[i] = this.symbols[this.random.nextInt(this.symbols.length)];
    }
    return new String(this.resultBuffer);
  }
}
