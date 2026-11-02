/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of property-expr 2.0.5 (https://github.com/jquense/expr), yup's property path dependency, which is based
 * on Kendo UI Core expression code.
 */
package com.github.jquense.yup.util;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsRegExp;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Property paths such as {@code a.b[0]["c"]}: splitting, normalising, reading and walking them. */
public final class PropertyExpr {
  private static final JsRegExp SPLIT_REGEX = new JsRegExp("[^.^\\]^[]+|(?=\\[\\]|\\.\\.)", "g");
  private static final JsRegExp DIGIT_REGEX = new JsRegExp("^\\d+$");
  private static final JsRegExp LEAD_DIGIT_REGEX = new JsRegExp("^\\d");
  // A global expression used with test(): its lastIndex carries over from one call to the next, as in the original.
  private static final JsRegExp SPEC_CHAR_REGEX = new JsRegExp("[~`!#$%\\^&*+=\\-\\[\\]\\\\';,/{}|\\\\\":<>\\?]", "g");
  private static final JsRegExp CLEAN_QUOTES_REGEX = new JsRegExp("^\\s*(['\"]?)(.*?)(\\1)\\s*$");
  private static final JsRegExp ALL_DIGITS = new JsRegExp("^\\d+$");

  private PropertyExpr() {}

  /** The callback of {@link #forEach}. */
  @FunctionalInterface
  public interface PartVisitor {
    /**
     * Visits one part of a path.
     *
     * @param part the part, quoted when it needs quoting
     * @param isBracket whether it is a quoted (bracketed) part
     * @param isArray whether it is an array index
     */
    void visit(String part, boolean isBracket, boolean isArray);
  }

  /**
   * {@code split(path)}: the parts of a path.
   *
   * @param path a path
   * @return its parts ({@code [""]} when there are none)
   */
  public static List<String> split(String path) {
    List<String> parts = SPLIT_REGEX.matchAll(path);
    return parts == null ? List.of("") : parts;
  }

  /**
   * {@code normalizePath(path)}: the parts with surrounding quotes and white space removed.
   *
   * @param path a path
   * @return the normalised parts
   */
  public static List<String> normalizePath(String path) {
    List<String> parts = new ArrayList<>();
    for (String part : split(path)) {
      parts.add(CLEAN_QUOTES_REGEX.replace(part, match -> match.group(2) == null ? "" : match.group(2)));
    }
    return parts;
  }

  /**
   * {@code getter(path, safe)}: a function reading the path from a value.
   *
   * @param path a path
   * @param safe whether reading stops at {@code null} or {@code undefined} instead of failing
   * @return the getter
   */
  public static Function<Object, Object> getter(String path, boolean safe) {
    List<String> parts = normalizePath(path);
    return data -> {
      Object current = data;
      int index = 0;
      while (index < parts.size()) {
        if (!Js.isNullish(current) || !safe) {
          current = Js.get(current, parts.get(index++));
        } else {
          return Js.UNDEFINED;
        }
      }
      return current;
    };
  }

  /**
   * {@code join(segments)}: a path from its parts.
   *
   * @param segments the parts
   * @return the path
   */
  public static String join(List<String> segments) {
    String path = "";
    for (String part : segments) {
      path = path + (isQuoted(part) || DIGIT_REGEX.test(part) ? "[" + part + "]" : (path.isEmpty() ? "" : ".") + part);
    }
    return path;
  }

  /**
   * {@code forEach(path, visitor)}: visits every non-empty part of a path.
   *
   * @param path a path
   * @param visitor the visitor
   */
  public static void forEach(String path, PartVisitor visitor) {
    for (String original : split(path)) {
      String part = original;
      if (part.isEmpty()) {
        continue;
      }
      if (shouldBeQuoted(part)) {
        part = "\"" + part + "\"";
      }
      boolean isBracket = isQuoted(part);
      boolean isArray = !isBracket && ALL_DIGITS.test(part);
      visitor.visit(part, isBracket, isArray);
    }
  }

  private static boolean isQuoted(String s) {
    return !s.isEmpty() && (s.charAt(0) == '\'' || s.charAt(0) == '"');
  }

  private static boolean hasLeadingNumber(String part) {
    return LEAD_DIGIT_REGEX.exec(part) != null && DIGIT_REGEX.exec(part) == null;
  }

  private static boolean shouldBeQuoted(String part) {
    return !isQuoted(part) && (hasLeadingNumber(part) || SPEC_CHAR_REGEX.test(part));
  }
}
