/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup). The Standard Schema interface is from @standard-schema/spec
 * (https://github.com/standard-schema/standard-schema).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsArray;
import com.github.jquense.yup.js.JsException;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsPromise;
import com.github.jquense.yup.js.JsRegExp;
import java.util.ArrayList;
import java.util.List;

/** The {@code ~standard} properties: Standard Schema validation that reports issues instead of throwing. */
public final class StandardSchema {
  private static final JsRegExp DIGITS = new JsRegExp("^\\d+$");
  private static final JsRegExp QUOTES = new JsRegExp("^\"|\"$", "g");

  private StandardSchema() {}

  /**
   * The {@code ~standard} object of a schema: {@code {version: 1, vendor: 'yup', validate}}.
   *
   * @param schema the schema
   * @return the properties
   */
  static JsObject props(ISchema schema) {
    JsFunction validate = JsFunction.named("validate", (self, args) -> validate(schema, JsFunction.arg(args, 0)));
    return JsObject.of("version", 1.0, "vendor", "yup", "validate", validate);
  }

  /**
   * {@code schema['~standard'].validate(value)}: {@code {value}} on success, {@code {issues}} for a
   * {@link ValidationError} (validated with {@code abortEarly: false}).
   *
   * @param schema the schema
   * @param value the value
   * @return a promise of the result
   */
  public static JsPromise validate(ISchema schema, Object value) {
    JsPromise result;
    try {
      result = schema.validate(value, JsObject.of("abortEarly", false));
    } catch (RuntimeException e) {
      result = JsPromise.rejected(JsException.valueOf(e));
    }
    return result.then(validated -> JsObject.of("value", validated), err -> {
      if (err instanceof ValidationError error) {
        return JsObject.of("issues", issuesFromValidationError(error, Js.UNDEFINED));
      }
      throw JsException.of(err);
    });
  }

  /**
   * {@code createStandardPath(path)}: the path's segments, {@code undefined} for an empty path.
   *
   * @param path the path
   * @return an array of segments, or {@code undefined}
   */
  public static Object createStandardPath(Object path) {
    if (Js.isNullish(path) || !Js.truthy(Js.get(path, "length"))) {
      return Js.UNDEFINED;
    }
    String text = (String) path;
    List<String> segments = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inBrackets = false;
    boolean inQuotes = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '[' && !inQuotes) {
        if (current.length() > 0) {
          splitDots(current.toString(), segments);
          current.setLength(0);
        }
        inBrackets = true;
        continue;
      }
      if (c == ']' && !inQuotes) {
        if (current.length() > 0) {
          String segment = current.toString();
          segments.add(DIGITS.test(segment) ? segment : QUOTES.replace(segment, match -> ""));
          current.setLength(0);
        }
        inBrackets = false;
        continue;
      }
      if (c == '"') {
        inQuotes = !inQuotes;
        continue;
      }
      if (c == '.' && !inBrackets && !inQuotes) {
        if (current.length() > 0) {
          segments.add(current.toString());
          current.setLength(0);
        }
        continue;
      }
      current.append(c);
    }
    if (current.length() > 0) {
      splitDots(current.toString(), segments);
    }
    return new JsArray(segments);
  }

  private static void splitDots(String s, List<String> segments) {
    for (String piece : s.split("\\.", -1)) {
      if (!piece.isEmpty()) {
        segments.add(piece);
      }
    }
  }

  private static Object joinPath(Object parentPath, Object path) {
    return Js.truthy(parentPath) ? Js.toJsString(parentPath) + "." + Js.toJsString(path) : path;
  }

  /**
   * {@code issuesFromValidationError(error, parentPath)}.
   *
   * @param error the error
   * @param parentPath the path of the enclosing error, or {@code undefined}
   * @return the issues
   */
  public static JsArray issuesFromValidationError(Object error, Object parentPath) {
    Object inner = Js.get(error, "inner");
    Object innerLength = Js.isNullish(inner) ? Js.UNDEFINED : Js.get(inner, "length");
    JsArray errors = (JsArray) Js.get(error, "errors");
    if (!Js.truthy(innerLength) && errors.length() > 0) {
      Object path = joinPath(parentPath, Js.get(error, "path"));
      JsArray issues = new JsArray();
      for (Object message : errors.toList()) {
        issues.push(JsObject.of("message", message, "path", createStandardPath(path)));
      }
      return issues;
    }
    Object path = joinPath(parentPath, Js.get(error, "path"));
    JsArray issues = new JsArray();
    for (Object err : ((JsArray) inner).toList()) {
      for (Object issue : issuesFromValidationError(err, path).toList()) {
        issues.push(issue);
      }
    }
    return issues;
  }
}
