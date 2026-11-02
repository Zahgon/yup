/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * JavaScript's locale-independent case mapping ({@code toUpperCase}, {@code toLowerCase}) at Unicode 16.0, and the
 * {@code Canonicalize} operation of case-insensitive regular expressions. The JDK's tables are Unicode 15; the code
 * points whose simple case mapping changed in Unicode 16 are mapped here.
 */
public final class UnicodeCase {
  private static final Map<Integer, Integer> UPPER = new HashMap<>();
  private static final Map<Integer, Integer> LOWER = new HashMap<>();

  static {
    pair(0xA7DC, 0x019B);
    pair(0xA7CB, 0x0264);
    pair(0x1C89, 0x1C8A);
    pair(0xA7CC, 0xA7CD);
    pair(0xA7DA, 0xA7DB);
    for (int cp = 0x10D50; cp <= 0x10D65; cp++) {
      pair(cp, cp + 0x20);
    }
  }

  private static final char[] CANONICAL = new char[0x10000];

  static {
    for (int c = 0; c < 0x10000; c++) {
      CANONICAL[c] = computeCanonical((char) c);
    }
  }

  private UnicodeCase() {}

  private static void pair(int upper, int lower) {
    UPPER.put(lower, upper);
    LOWER.put(upper, lower);
  }

  /**
   * {@code String.prototype.toUpperCase}.
   *
   * @param s a string
   * @return its upper-case form
   */
  public static String toUpperCase(String s) {
    return map(s, true);
  }

  /**
   * {@code String.prototype.toLowerCase}.
   *
   * @param s a string
   * @return its lower-case form
   */
  public static String toLowerCase(String s) {
    return map(s, false);
  }

  // Maps runs with the JDK, but the code points whose mapping changed in Unicode 16, and (lower-casing) every capital
  // sigma, whose final form follows the Unicode Final_Sigma condition rather than the JDK's word-boundary test.
  private static String map(String s, boolean upper) {
    Map<Integer, Integer> delta = upper ? UPPER : LOWER;
    if (!needsCare(s, delta, upper)) {
      return upper ? s.toUpperCase(Locale.ROOT) : s.toLowerCase(Locale.ROOT);
    }
    StringBuilder out = new StringBuilder(s.length());
    int runStart = 0;
    int i = 0;
    while (i < s.length()) {
      int cp = s.codePointAt(i);
      int width = Character.charCount(cp);
      Integer mapped = delta.get(cp);
      if (!upper && cp == CAPITAL_SIGMA) {
        mapped = isFinalSigma(s, i) ? FINAL_SIGMA : SMALL_SIGMA;
      }
      if (mapped != null) {
        String run = s.substring(runStart, i);
        out.append(upper ? run.toUpperCase(Locale.ROOT) : run.toLowerCase(Locale.ROOT));
        out.appendCodePoint(mapped);
        runStart = i + width;
      }
      i += width;
    }
    String run = s.substring(runStart);
    out.append(upper ? run.toUpperCase(Locale.ROOT) : run.toLowerCase(Locale.ROOT));
    return out.toString();
  }

  private static final int CAPITAL_SIGMA = 0x03A3;
  private static final int SMALL_SIGMA = 0x03C3;
  private static final int FINAL_SIGMA = 0x03C2;

  private static boolean needsCare(String s, Map<Integer, Integer> delta, boolean upper) {
    for (int i = 0; i < s.length(); ) {
      int cp = s.codePointAt(i);
      if (delta.containsKey(cp) || (!upper && cp == CAPITAL_SIGMA)) {
        return true;
      }
      i += Character.charCount(cp);
    }
    return false;
  }

  // Final_Sigma: a cased letter before (skipping case-ignorable code points) and no cased letter after.
  private static boolean isFinalSigma(String s, int index) {
    boolean casedBefore = false;
    for (int i = index; i > 0; ) {
      int cp = s.codePointBefore(i);
      i -= Character.charCount(cp);
      if (!isCaseIgnorable(cp)) {
        casedBefore = isCased(cp);
        break;
      }
    }
    if (!casedBefore) {
      return false;
    }
    for (int i = index + 1; i < s.length(); ) {
      int cp = s.codePointAt(i);
      i += Character.charCount(cp);
      if (!isCaseIgnorable(cp)) {
        return !isCased(cp);
      }
    }
    return true;
  }

  private static boolean isCased(int cp) {
    return Character.isLowerCase(cp) || Character.isUpperCase(cp) || Character.isTitleCase(cp)
        || UPPER.containsKey(cp) || LOWER.containsKey(cp);
  }

  private static boolean isCaseIgnorable(int cp) {
    switch (Character.getType(cp)) {
      case Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.FORMAT, Character.MODIFIER_LETTER,
          Character.MODIFIER_SYMBOL -> {
        return true;
      }
      default -> {
        return switch (cp) {
          case 0x0027, 0x002E, 0x003A, 0x00B7, 0x0387, 0x055F, 0x05F4, 0x2018, 0x2019, 0x2024, 0x2027, 0xFE13, 0xFE52,
              0xFE55, 0xFF07, 0xFF0E, 0xFF1A -> true;
          default -> false;
        };
      }
    }
  }

  /**
   * {@code Canonicalize} for a regular expression without the {@code u} flag: the code unit's upper-case form when that
   * is a single code unit and does not map a non-ASCII code unit to ASCII, the code unit itself otherwise.
   *
   * @param c a code unit
   * @return its canonical form
   */
  public static char canonicalize(char c) {
    return CANONICAL[c];
  }

  private static char computeCanonical(char c) {
    if (Character.isSurrogate(c)) {
      return c;
    }
    String upper = toUpperCase(String.valueOf(c));
    if (upper.length() != 1) {
      return c;
    }
    char cu = upper.charAt(0);
    if (c >= 128 && cu < 128) {
      return c;
    }
    return cu;
  }
}
