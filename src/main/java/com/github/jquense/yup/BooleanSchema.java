/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsRegExp;

/** {@code boolean()}: booleans; {@code "true"}, {@code "1"}, {@code 1} (and false likewise) are cast. */
public class BooleanSchema extends Schema {
  private static final JsRegExp TRUE = new JsRegExp("^(true|1)$", "i");
  private static final JsRegExp FALSE = new JsRegExp("^(false|0)$", "i");

  /** {@code boolean()}. */
  public BooleanSchema() {
    super("boolean", null);
    withMutation(s -> s.transform((JsFunction) (self, args) -> {
      Object value = args[0];
      Schema ctx = (Schema) args[2];
      if (Js.truthy(ctx.spec.get("coerce")) && !Js.truthy(ctx.isType(value))) {
        if (TRUE.test(Js.string(value))) {
          return true;
        }
        if (FALSE.test(Js.string(value))) {
          return false;
        }
      }
      return value;
    }));
  }

  private BooleanSchema(boolean blank) {}

  @Override
  protected Schema blank() {
    return new BooleanSchema(true);
  }

  @Override
  public Prototype prototype() {
    return Prototype.BOOLEAN;
  }

  @Override
  protected Object typeCheck(Object value) {
    return value instanceof Boolean;
  }

  /**
   * {@code default()} without arguments. yup's boolean schema overrides {@code default(def)} by calling
   * {@code super.default(def)}, always with one argument, so this sets an {@code undefined} default rather than
   * returning the default value.
   *
   * @return the new schema
   */
  @Override
  public Object defaultValue() {
    return defaultValue(Js.UNDEFINED);
  }

  /**
   * {@code isTrue(message)}: only {@code true} (or no value).
   *
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public BooleanSchema isTrue(Object message) {
    return isValue(true, message);
  }

  /**
   * {@code isFalse(message)}: only {@code false} (or no value).
   *
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public BooleanSchema isFalse(Object message) {
    return isValue(false, message);
  }

  private BooleanSchema isValue(boolean expected, Object message) {
    return (BooleanSchema) test(JsObject.of(
        "message", message == Js.UNDEFINED ? Locale.message("boolean", "isValue") : message,
        "name", "is-value",
        "exclusive", true,
        "params", JsObject.of("value", expected ? "true" : "false"),
        "test", (JsFunction) (self, args) -> {
          Object value = JsFunction.arg(args, 0);
          return Js.isNullish(value) || Boolean.valueOf(expected).equals(value);
        }));
  }
}
