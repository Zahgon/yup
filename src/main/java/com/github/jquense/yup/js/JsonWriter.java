/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** {@code JSON.stringify}, with an optional replacer function and indentation. */
public final class JsonWriter {
  private final JsFunction replacer;
  private final String gap;
  private String indent = "";

  private JsonWriter(JsFunction replacer, String gap) {
    this.replacer = replacer;
    this.gap = gap;
  }

  /**
   * {@code JSON.stringify(value)}.
   *
   * @param value any value
   * @return the JSON text, or {@code undefined} for a value JSON cannot represent
   */
  public static Object stringify(Object value) {
    return stringify(value, null, "");
  }

  /**
   * {@code JSON.stringify(value, replacer, gap)}.
   *
   * @param value any value
   * @param replacer the replacer, called with the holder as {@code this}, or null
   * @param gap the indentation of one level
   * @return the JSON text, or {@code undefined}
   */
  public static Object stringify(Object value, JsFunction replacer, String gap) {
    JsObject wrapper = new JsObject();
    wrapper.put("", value);
    String text = new JsonWriter(replacer, gap).property(wrapper, "");
    return text == null ? Js.UNDEFINED : text;
  }

  /**
   * {@code JSON.stringify} of a string ({@code QuoteJSONString}).
   *
   * @param s a string
   * @return the quoted string
   */
  public static String quote(String s) {
    StringBuilder out = new StringBuilder(s.length() + 2);
    out.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          boolean loneSurrogate = Character.isHighSurrogate(c)
              ? !(i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1)))
              : Character.isLowSurrogate(c) && !(i > 0 && Character.isHighSurrogate(s.charAt(i - 1)));
          if (c < 0x20 || loneSurrogate) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }

  // SerializeJSONProperty: the text of holder[key], or null for undefined.
  private String property(Object holder, String key) {
    Object value = Js.get(holder, key);
    if (value instanceof JsDate date) {
      value = date.isInvalid() ? null : date.toISOString();
    }
    if (replacer != null) {
      value = replacer.call(holder, key, value);
    }
    if (value == null) {
      return "null";
    }
    if (value instanceof Boolean b) {
      return b ? "true" : "false";
    }
    if (value instanceof String s) {
      return quote(s);
    }
    if (value instanceof Double d) {
      return Double.isNaN(d) || Double.isInfinite(d) ? "null" : JsNumbers.toString(d);
    }
    if (value instanceof BigInteger) {
      throw JsError.typeError("Do not know how to serialize a BigInt");
    }
    if (value == Js.UNDEFINED || value instanceof JsSymbol || value instanceof JsFunction) {
      return null;
    }
    if (value instanceof JsArray array) {
      return array(array);
    }
    return object(value);
  }

  private String object(Object value) {
    String stepback = indent;
    indent = indent + gap;
    List<String> members = new ArrayList<>();
    for (String key : Js.keys(value)) {
      String text = property(value, key);
      if (text != null) {
        members.add(quote(key) + ":" + (gap.isEmpty() ? "" : " ") + text);
      }
    }
    String result = wrap("{", "}", members, stepback);
    indent = stepback;
    return result;
  }

  private String array(JsArray array) {
    String stepback = indent;
    indent = indent + gap;
    List<String> elements = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) {
      String text = property(array, Integer.toString(i));
      elements.add(text == null ? "null" : text);
    }
    String result = wrap("[", "]", elements, stepback);
    indent = stepback;
    return result;
  }

  private String wrap(String open, String close, List<String> parts, String stepback) {
    if (parts.isEmpty()) {
      return open + close;
    }
    if (gap.isEmpty()) {
      return open + String.join(",", parts) + close;
    }
    String separator = ",\n" + indent;
    return open + "\n" + indent + String.join(separator, parts) + "\n" + stepback + close;
  }
}
