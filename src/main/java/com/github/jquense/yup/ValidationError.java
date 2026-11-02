/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsArray;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsRegExp;
import com.github.jquense.yup.util.PrintValue;

/**
 * A failed validation: the messages ({@code errors}), the individual failures ({@code inner}), and the path, test type
 * and value of the failure. Created with {@code disableStack}, it is yup's stack-less variant, which is still an
 * instance of {@code ValidationError} but not of {@code Error}.
 */
public final class ValidationError extends JsError {
  private static final long serialVersionUID = 1L;
  private static final JsRegExp STR_REG = new JsRegExp("\\$\\{\\s*(\\w+)\\s*\\}", "g");

  /** The {@code value} property. */
  private transient Object value;
  /** The {@code path} property. */
  private transient Object path;
  /** The {@code type} property. */
  private transient Object type;
  /** The {@code params} property. */
  private transient Object params = Js.UNDEFINED;
  /** The {@code errors} property: the messages. */
  private transient Object errors = new JsArray();
  /** The {@code inner} property: the individual failures. */
  private transient Object inner = new JsArray();
  private final boolean nativeError;

  /**
   * {@code new ValidationError(errorOrErrors, value, field, type, disableStack)}: collects the messages of strings and of
   * errors (taking an error's own failures, or the error itself when it has none).
   *
   * @param errorOrErrors a message, an error, or an array of them
   * @param value the value
   * @param field the path
   * @param type the test type
   * @param disableStack whether it is the stack-less variant (JavaScript truthiness)
   */
  public ValidationError(Object errorOrErrors, Object value, Object field, Object type, Object disableStack) {
    super("ValidationError", Js.UNDEFINED);
    this.value = value;
    this.path = field;
    this.type = type;
    this.nativeError = !Js.truthy(disableStack);
    JsArray messages = new JsArray();
    JsArray failures = new JsArray();
    for (Object err : toArray(errorOrErrors)) {
      if (isError(err)) {
        for (Object message : spreadable(Js.get(err, "errors"))) {
          messages.push(message);
        }
        Object errInner = Js.read(Js.get(err, "inner"), "length");
        Object innerErrors = Js.truthy(errInner) ? Js.get(err, "inner") : JsArray.of(err);
        for (Object failure : spreadable(innerErrors)) {
          failures.push(failure);
        }
      } else {
        messages.push(err);
      }
    }
    this.errors = messages;
    this.inner = failures;
    setMessageValue(messages.length() > 1 ? messages.length() + " errors occurred" : messages.get(0));
  }

  /**
   * A validation error with a single message.
   *
   * @param message the message
   */
  public ValidationError(String message) {
    this(message, Js.UNDEFINED, Js.UNDEFINED, Js.UNDEFINED, false);
  }

  // toArray(): [] for undefined/null, the elements of an array (skipping empty slots), [value] otherwise.
  private static java.util.List<Object> toArray(Object value) {
    java.util.List<Object> list = new java.util.ArrayList<>();
    if (Js.isNullish(value)) {
      return list;
    }
    if (value instanceof JsArray array) {
      for (int i = 0; i < array.length(); i++) {
        if (array.has(i)) {
          list.add(array.get(i));
        }
      }
      return list;
    }
    list.add(value);
    return list;
  }

  // The elements of a value spread into an argument list.
  private static java.util.List<Object> spreadable(Object value) {
    if (value instanceof JsArray array) {
      return array.toList();
    }
    if (value instanceof String s) {
      java.util.List<Object> chars = new java.util.ArrayList<>();
      s.codePoints().forEach(cp -> chars.add(new String(Character.toChars(cp))));
      return chars;
    }
    throw JsError.typeError("Spread syntax requires ...iterable[Symbol.iterator] to be a function");
  }

  /**
   * {@code ValidationError.isError(err)} as JavaScript computes it: {@code err && err.name === 'ValidationError'}.
   *
   * @param err any value
   * @return {@code err} itself when it is falsy, otherwise whether its {@code name} is {@code "ValidationError"}
   */
  public static Object isErrorValue(Object err) {
    if (!Js.truthy(err)) {
      return err;
    }
    return "ValidationError".equals(Js.get(err, "name"));
  }

  /**
   * @param err any value
   * @return whether it is (duck-typed as) a validation error
   */
  public static boolean isError(Object err) {
    return Js.truthy(isErrorValue(err));
  }

  /**
   * {@code ValidationError.formatError(message, params)}: {@code ${key}} placeholders of a string message are replaced
   * with the printed params ({@code path} being the label or path, {@code this} when both are empty); a function message
   * is called with the params; anything else is returned as it is.
   *
   * @param message the message
   * @param params the params
   * @return the formatted message
   */
  public static Object formatError(Object message, Object params) {
    Object label = Js.read(params, "label");
    Object path = Js.truthy(label) ? label : Js.get(params, "path");
    if (!Js.truthy(path)) {
      path = "this";
    }
    JsObject all = new JsObject();
    Js.spread(all, params);
    all.put("path", path);
    all.put("originalPath", Js.get(params, "path"));
    if (message instanceof String text) {
      return STR_REG.replace(text, match -> PrintValue.print(all.get(match.group(1)), false));
    }
    if (message instanceof JsFunction function) {
      return function.call(Js.UNDEFINED, all);
    }
    return message;
  }

  /** @return the {@code value} property */
  public Object value() {
    return value;
  }

  /**
   * Sets the {@code value} property.
   *
   * @param value the value
   */
  public void setValue(Object value) {
    this.value = value;
  }

  /** @return the {@code path} property */
  public Object path() {
    return path;
  }

  /** @return the {@code type} property */
  public Object type() {
    return type;
  }

  /** @return the {@code params} property */
  public Object params() {
    return params;
  }

  /**
   * Sets the {@code params} property.
   *
   * @param params the params
   */
  public void setParams(Object params) {
    this.params = params;
  }

  /** @return the {@code errors} property */
  public Object errors() {
    return errors;
  }

  /** @return the {@code inner} property */
  public Object inner() {
    return inner;
  }

  @Override
  public boolean isNativeError() {
    return nativeError;
  }

  @Override
  public Object getProperty(String key) {
    return switch (key) {
      case "value" -> value;
      case "path" -> path;
      case "type" -> type;
      case "params" -> params;
      case "errors" -> errors;
      case "inner" -> inner;
      default -> super.getProperty(key);
    };
  }

  /**
   * Sets the {@code value} property of an error value: a validation error or a plain object duck-typed as one.
   *
   * @param error the error
   * @param value the value
   */
  static void assignValue(Object error, Object value) {
    if (error instanceof ValidationError validationError) {
      validationError.setValue(value);
    } else if (error instanceof JsObject object) {
      object.put("value", value);
    }
  }
}
