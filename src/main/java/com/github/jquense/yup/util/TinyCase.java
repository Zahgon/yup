/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of tiny-case 1.0.3 (https://github.com/jquense/tiny-case), yup's key-casing dependency.
 */
package com.github.jquense.yup.util;

import com.github.jquense.yup.js.JsRegExp;
import com.github.jquense.yup.js.UnicodeCase;
import java.util.List;

/** Word splitting and case conversion of object keys. */
public final class TinyCase {
  private static final String UPPER = "A-Z\\xc0-\\xd6\\xd8-\\xde";
  private static final String LOWER = "a-z\\xdf-\\xf6\\xf8-\\xff";
  private static final String BREAK = "\\xac\\xb1\\xd7\\xf7\\x00-\\x2f\\x3a-\\x40\\x5b-\\x60\\x7b-\\xbf\\u2000-\\u206f"
      + " \\t\\x0b\\f\\xa0\\ufeff\\n\\r\\u2028\\u2029\\u1680\\u180e\\u2000\\u2001\\u2002\\u2003\\u2004\\u2005\\u2006"
      + "\\u2007\\u2008\\u2009\\u200a\\u202f\\u205f\\u3000";
  private static final String MISC = "[^\\ud800-\\udfff" + BREAK + "\\d+\\u2700-\\u27bf" + LOWER + UPPER + "]";
  private static final String EMOJI_TAIL = "[\\ufe0e\\ufe0f]?(?:[\\u0300-\\u036f\\ufe20-\\ufe2f\\u20d0-\\u20ff]"
      + "|\\ud83c[\\udffb-\\udfff])?";
  private static final String WORDS = "[" + UPPER + "]?[" + LOWER + "]+(?:['\\u2019](?:d|ll|m|re|s|t|ve))?(?=[" + BREAK
      + "]|[" + UPPER + "]|$)|(?:[" + UPPER + "]|" + MISC + ")+(?:['\\u2019](?:D|LL|M|RE|S|T|VE))?(?=[" + BREAK + "]|["
      + UPPER + "](?:[" + LOWER + "]|" + MISC + ")|$)|[" + UPPER + "]?(?:[" + LOWER + "]|" + MISC
      + ")+(?:['\\u2019](?:d|ll|m|re|s|t|ve))?|[" + UPPER + "]+(?:['\\u2019](?:D|LL|M|RE|S|T|VE))?"
      + "|\\d*(?:1ST|2ND|3RD|(?![123])\\dTH)(?=\\b|[a-z_])|\\d*(?:1st|2nd|3rd|(?![123])\\dth)(?=\\b|[A-Z_])|\\d+"
      + "|(?:[\\u2700-\\u27bf]|(?:\\ud83c[\\udde6-\\uddff]){2}|[\\ud800-\\udbff][\\udc00-\\udfff])" + EMOJI_TAIL
      + "(?:\\u200d(?:[^\\ud800-\\udfff]|(?:\\ud83c[\\udde6-\\uddff]){2}|[\\ud800-\\udbff][\\udc00-\\udfff])"
      + EMOJI_TAIL + ")*";
  private static final JsRegExp RE_WORDS = new JsRegExp(WORDS, "g");

  private TinyCase() {}

  /**
   * {@code words(str)}.
   *
   * @param s a string
   * @return its words
   */
  public static List<String> words(String s) {
    List<String> words = RE_WORDS.matchAll(s);
    return words == null ? List.of() : words;
  }

  /**
   * {@code camelCase(str)}.
   *
   * @param s a string
   * @return the words joined in camel case
   */
  public static String camelCase(String s) {
    StringBuilder out = new StringBuilder();
    for (String word : words(s)) {
      if (out.isEmpty()) {
        out.append(UnicodeCase.toLowerCase(word));
      } else {
        out.append(UnicodeCase.toUpperCase(word.substring(0, 1)));
        out.append(UnicodeCase.toLowerCase(word.substring(1)));
      }
    }
    return out.toString();
  }

  /**
   * {@code snakeCase(str)}.
   *
   * @param s a string
   * @return the words joined with underscores, in lower case
   */
  public static String snakeCase(String s) {
    return UnicodeCase.toLowerCase(String.join("_", words(s)));
  }
}
