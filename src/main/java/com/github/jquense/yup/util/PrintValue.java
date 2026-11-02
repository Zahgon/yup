/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.util;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsDate;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsRegExp;
import com.github.jquense.yup.js.JsSymbol;
import com.github.jquense.yup.js.JsonWriter;

/** {@code printValue}: how values appear in messages. */
public final class PrintValue {
  private PrintValue() {}

  private static String printNumber(double value) {
    if (Double.isNaN(value)) {
      return "NaN";
    }
    boolean negativeZero = value == 0 && 1 / value < 0;
    return negativeZero ? "-0" : Js.toJsString(value);
  }

  private static String printSimpleValue(Object value, boolean quoteStrings) {
    if (Js.isNullish(value) || value instanceof Boolean) {
      return Js.toJsString(value);
    }
    if (value instanceof Double d) {
      return printNumber(d);
    }
    if (value instanceof String s) {
      return quoteStrings ? "\"" + s + "\"" : s;
    }
    if (value instanceof JsFunction function) {
      return "[Function " + (function.name().isEmpty() ? "anonymous" : function.name()) + "]";
    }
    if (value instanceof JsSymbol symbol) {
      return symbol.descriptiveString();
    }
    if (value instanceof JsDate date) {
      return date.isInvalid() ? date.toString() : date.toISOString();
    }
    if (value instanceof JsError error) {
      return "[" + error.errorToString() + "]";
    }
    if (value instanceof JsRegExp regex) {
      return regex.toString();
    }
    return null;
  }

  /**
   * {@code printValue(value, quoteStrings)}: primitives, dates, errors and regular expressions as text, anything else as
   * indented JSON in which every nested primitive is printed this way (and quoted as a JSON string).
   *
   * @param value any value
   * @param quoteStrings whether strings are wrapped in double quotes (JavaScript truthiness)
   * @return the text ({@code undefined} only when JSON cannot represent the value)
   */
  public static Object printValue(Object value, Object quoteStrings) {
    boolean quote = Js.truthy(quoteStrings);
    String result = printSimpleValue(value, quote);
    if (result != null) {
      return result;
    }
    JsFunction replacer = (holder, args) -> {
      String simple = printSimpleValue(Js.get(holder, (String) args[0]), quote);
      return simple != null ? simple : args[1];
    };
    return JsonWriter.stringify(value, replacer, "  ");
  }

  /**
   * {@code printValue(value)} as a Java string.
   *
   * @param value any value
   * @param quoteStrings whether strings are wrapped in double quotes
   * @return the text
   */
  public static String print(Object value, boolean quoteStrings) {
    return Js.toJsString(printValue(value, quoteStrings));
  }
}
