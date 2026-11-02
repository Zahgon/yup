/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.cli;

import com.github.jquense.yup.Reference;
import com.github.jquense.yup.ValidationError;
import com.github.jquense.yup.Yup;
import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsArray;
import com.github.jquense.yup.js.JsDate;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsRegExp;
import com.github.jquense.yup.js.JsSymbol;
import java.math.BigInteger;

/** Encodes JavaScript values for responses (E) and validation errors (EV), as the reference command does. */
final class Encoder {
  private Encoder() {}

  private static JsObject tag(String name, Object value) {
    return JsObject.of(name, value);
  }

  /**
   * E(value).
   *
   * @param value any value
   * @return its JSON form
   */
  static Object encode(Object value) {
    if (value == Js.UNDEFINED) {
      return tag("$undefined", true);
    }
    if (value == null || value instanceof Boolean || value instanceof String) {
      return value;
    }
    if (value instanceof Double d) {
      if (Double.isNaN(d)) {
        return tag("$number", "NaN");
      }
      if (d == Double.POSITIVE_INFINITY) {
        return tag("$number", "Infinity");
      }
      if (d == Double.NEGATIVE_INFINITY) {
        return tag("$number", "-Infinity");
      }
      if (d == 0 && 1 / d < 0) {
        return tag("$number", "-0");
      }
      return d;
    }
    if (value instanceof BigInteger big) {
      return tag("$bigint", big.toString());
    }
    if (value instanceof JsSymbol symbol) {
      return tag("$symbol", encode(symbol.description() == null ? Js.UNDEFINED : symbol.description()));
    }
    if (value instanceof JsFunction function) {
      return tag("$function", function.name());
    }
    if (value instanceof JsDate date) {
      return tag("$date", date.isInvalid() ? "Invalid Date" : date.toISOString());
    }
    if (value instanceof JsRegExp regex) {
      return JsObject.of("$regex", regex.source(), "flags", regex.flags());
    }
    if (Yup.isSchema(value)) {
      return tag("$schema", Js.get(value, "type"));
    }
    if (value instanceof Reference reference) {
      return tag("$ref", reference.key());
    }
    if (value instanceof ValidationError error) {
      return tag("$validationError", encodeError(error));
    }
    if (value instanceof JsError error) {
      return JsObject.of("$error", encode(error.messageValue()), "name", encode(error.name()));
    }
    if (value instanceof JsArray array) {
      JsArray out = new JsArray();
      for (int i = 0; i < array.length(); i++) {
        out.push(array.has(i) ? encode(array.get(i)) : tag("$hole", true));
      }
      return out;
    }
    JsObject out = new JsObject();
    boolean tagged = false;
    for (String key : Js.keys(value)) {
      if (key.startsWith("$")) {
        tagged = true;
      }
      out.put(key, encode(Js.get(value, key)));
    }
    return tagged ? tag("$object", out) : out;
  }

  /**
   * EV(error).
   *
   * @param error any value thrown or reported as a validation error
   * @return its JSON form
   */
  static Object encodeError(Object error) {
    if (!Decoder.isObject(error) && !(error instanceof JsArray)) {
      return encode(error);
    }
    Object params = Js.get(error, "params");
    if (Js.toStringTag(params).equals("Object") && params instanceof JsObject paramsObject) {
      JsObject copy = paramsObject.copy();
      copy.delete("spec");
      params = copy;
    }
    Object inner = Js.get(error, "inner");
    Object innerOut;
    if (inner instanceof JsArray array) {
      JsArray list = new JsArray();
      for (Object item : array.toList()) {
        list.push(encodeError(item));
      }
      innerOut = list;
    } else {
      innerOut = encode(inner);
    }
    return JsObject.of(
        "name", encode(Js.get(error, "name")),
        "message", encode(Js.get(error, "message")),
        "path", encode(Js.get(error, "path")),
        "type", encode(Js.get(error, "type")),
        "value", encode(Js.get(error, "value")),
        "errors", encode(Js.get(error, "errors")),
        "params", encode(params),
        "inner", innerOut,
        "native", error instanceof JsError jsError && jsError.isNativeError());
  }
}
