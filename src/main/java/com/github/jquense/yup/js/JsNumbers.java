/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/** JavaScript number conversions: {@code Number::toString}, {@code StringToNumber}, {@code parseFloat} and Math. */
public final class JsNumbers {
  private static final int MAX_DIGITS = 17;

  private JsNumbers() {}

  /**
   * Whether a code unit is JavaScript white space or a line terminator (what {@code trim} and {@code \s} remove).
   *
   * @param c a code unit
   * @return whether it is white space
   */
  public static boolean isWhiteSpace(char c) {
    return switch (c) {
      case '\t', '\n', '\u000B', '\f', '\r', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
          '　', '﻿' -> true;
      default -> c >= ' ' && c <= ' ';
    };
  }

  /**
   * {@code String.prototype.trim}.
   *
   * @param s a string
   * @return the string without leading and trailing white space
   */
  public static String trim(String s) {
    int start = 0;
    int end = s.length();
    while (start < end && isWhiteSpace(s.charAt(start))) {
      start++;
    }
    while (end > start && isWhiteSpace(s.charAt(end - 1))) {
      end--;
    }
    return s.substring(start, end);
  }

  /**
   * {@code Number::toString(10)}: the shortest decimal that reads back as the same number.
   *
   * @param value a number
   * @return its JavaScript string form
   */
  public static String toString(double value) {
    if (Double.isNaN(value)) {
      return "NaN";
    }
    if (value == 0) {
      return "0";
    }
    if (Double.isInfinite(value)) {
      return value > 0 ? "Infinity" : "-Infinity";
    }
    if (value < 0) {
      return "-" + toString(-value);
    }
    BigDecimal shortest = shortest(value);
    String digits = shortest.unscaledValue().toString();
    int k = digits.length();
    int n = k - shortest.scale();
    if (k <= n && n <= 21) {
      return digits + "0".repeat(n - k);
    }
    if (0 < n && n <= 21) {
      return digits.substring(0, n) + "." + digits.substring(n);
    }
    if (-6 < n && n <= 0) {
      return "0." + "0".repeat(-n) + digits;
    }
    int exponent = n - 1;
    String sign = exponent < 0 ? "-" : "+";
    String mantissa = k == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
    return mantissa + "e" + sign + Math.abs(exponent);
  }

  // The decimal with the fewest digits that rounds to the value, the closest one when two of that length do (and the
  // even one when both are equally close), without trailing zeros.
  private static BigDecimal shortest(double value) {
    BigDecimal exact = new BigDecimal(value);
    for (int precision = 1; precision <= MAX_DIGITS; precision++) {
      BigDecimal low = exact.round(new MathContext(precision, RoundingMode.FLOOR));
      BigDecimal high = exact.round(new MathContext(precision, RoundingMode.CEILING));
      boolean lowOk = Double.parseDouble(low.toString()) == value;
      boolean highOk = Double.parseDouble(high.toString()) == value;
      BigDecimal pick = null;
      if (lowOk && highOk && low.compareTo(high) != 0) {
        int order = exact.subtract(low).compareTo(high.subtract(exact));
        if (order < 0) {
          pick = low;
        } else if (order > 0) {
          pick = high;
        } else {
          pick = low.unscaledValue().testBit(0) ? high : low;
        }
      } else if (lowOk) {
        pick = low;
      } else if (highOk) {
        pick = high;
      }
      if (pick != null) {
        return pick.stripTrailingZeros();
      }
    }
    return exact.round(new MathContext(MAX_DIGITS, RoundingMode.HALF_EVEN)).stripTrailingZeros();
  }

  /**
   * {@code StringToNumber}: what {@code Number(string)} and unary plus make of a string.
   *
   * @param s a string
   * @return the number, NaN when the string is not a numeric literal
   */
  public static double stringToNumber(String s) {
    String t = trim(s);
    if (t.isEmpty()) {
      return 0;
    }
    if (t.length() > 2 && t.charAt(0) == '0') {
      int radix = switch (t.charAt(1)) {
        case 'x', 'X' -> 16;
        case 'o', 'O' -> 8;
        case 'b', 'B' -> 2;
        default -> 0;
      };
      if (radix != 0) {
        String body = t.substring(2);
        for (int i = 0; i < body.length(); i++) {
          if (asciiDigit(body.charAt(i)) >= radix) {
            return Double.NaN;
          }
        }
        return new BigInteger(body, radix).doubleValue();
      }
    }
    int i = 0;
    if (t.charAt(0) == '+' || t.charAt(0) == '-') {
      i = 1;
    }
    String rest = t.substring(i);
    if (rest.equals("Infinity")) {
      return t.charAt(0) == '-' ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    }
    int end = scanDecimal(t, i);
    if (end != t.length()) {
      return Double.NaN;
    }
    return Double.parseDouble(t);
  }

  // The end of the unsigned decimal literal starting at `from`, or -1 when there is none.
  private static int scanDecimal(String s, int from) {
    int n = s.length();
    int i = from;
    int intDigits = 0;
    while (i < n && isDigit(s.charAt(i))) {
      i++;
      intDigits++;
    }
    int fracDigits = 0;
    if (i < n && s.charAt(i) == '.') {
      int j = i + 1;
      while (j < n && isDigit(s.charAt(j))) {
        j++;
        fracDigits++;
      }
      if (intDigits > 0 || fracDigits > 0) {
        i = j;
      }
    }
    if (intDigits == 0 && fracDigits == 0) {
      return -1;
    }
    if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
      int j = i + 1;
      if (j < n && (s.charAt(j) == '+' || s.charAt(j) == '-')) {
        j++;
      }
      int k = j;
      while (k < n && isDigit(s.charAt(k))) {
        k++;
      }
      if (k > j) {
        i = k;
      }
    }
    return i;
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  // The value of an ASCII digit or letter digit, 99 for anything else.
  private static int asciiDigit(char c) {
    if (c >= '0' && c <= '9') {
      return c - '0';
    }
    if (c >= 'a' && c <= 'z') {
      return c - 'a' + 10;
    }
    if (c >= 'A' && c <= 'Z') {
      return c - 'A' + 10;
    }
    return 99;
  }

  /**
   * {@code parseFloat} of a string: the longest decimal prefix after leading white space.
   *
   * @param s a string
   * @return the number, NaN when there is no numeric prefix
   */
  public static double parseFloat(String s) {
    int n = s.length();
    int start = 0;
    while (start < n && isWhiteSpace(s.charAt(start))) {
      start++;
    }
    int i = start;
    if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
      i++;
    }
    if (s.startsWith("Infinity", i)) {
      return s.charAt(start) == '-' ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    }
    int end = scanDecimal(s, i);
    if (end < 0) {
      return Double.NaN;
    }
    return Double.parseDouble(s.substring(start, end));
  }

  /**
   * {@code Number.isInteger}.
   *
   * @param value any value
   * @return whether it is a number with an integral value
   */
  public static boolean isInteger(Object value) {
    return value instanceof Double d && !Double.isInfinite(d) && !Double.isNaN(d) && Math.floor(d) == d;
  }

  /**
   * {@code Math.round}: halves round up, and results between -0.5 and 0 are -0.
   *
   * @param x a number
   * @return the rounded number
   */
  public static double round(double x) {
    if (Double.isNaN(x) || Double.isInfinite(x) || Math.floor(x) == x) {
      return x;
    }
    double r = Math.floor(x);
    if (x - r >= 0.5) {
      r += 1;
    }
    return r == 0 && x < 0 ? -0.0 : r;
  }

  /**
   * {@code ToInt32}.
   *
   * @param x a number
   * @return the number wrapped into a signed 32-bit integer
   */
  public static double toInt32(double x) {
    if (Double.isNaN(x) || Double.isInfinite(x)) {
      return 0;
    }
    double truncated = x < 0 ? Math.ceil(x) : Math.floor(x);
    BigInteger big = new BigDecimal(truncated).toBigInteger();
    return big.intValue();
  }
}
