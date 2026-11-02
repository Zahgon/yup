/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsDate;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.util.ParseIsoDate;

/** {@code date()}: valid dates; other values are parsed as ISO dates, then with {@code Date.parse}. */
public class DateSchema extends Schema {
  /** {@code DateSchema.INVALID_DATE}: the invalid date every failed cast returns. */
  public static final JsDate INVALID_DATE = new JsDate(Double.NaN);

  /** {@code date()}. */
  public DateSchema() {
    super("date", null);
    withMutation(s -> s.transform((JsFunction) (self, args) -> {
      Object value = args[0];
      Schema ctx = (Schema) args[2];
      if (!Js.truthy(ctx.spec.get("coerce")) || Js.truthy(ctx.isType(value)) || value == null) {
        return value;
      }
      double time = ParseIsoDate.parseIsoDate(value);
      return !Double.isNaN(time) ? new JsDate(time) : INVALID_DATE;
    }));
  }

  private DateSchema(boolean blank) {}

  @Override
  protected Schema blank() {
    return new DateSchema(true);
  }

  @Override
  public Prototype prototype() {
    return Prototype.DATE;
  }

  @Override
  protected Object typeCheck(Object value) {
    return value instanceof JsDate date && !date.isInvalid();
  }

  private Object prepareParam(Object ref, String name) {
    if (ref instanceof Reference) {
      return ref;
    }
    Object cast = cast(ref, Js.UNDEFINED);
    if (!Js.truthy(typeCheck(cast))) {
      throw JsError.typeError("`" + name + "` must be a Date or a value that can be `cast()` to a Date");
    }
    return cast;
  }

  private DateSchema bound(String name, Object javaRaw, Object message, boolean lower) {
    Object raw = Js.fromJava(javaRaw);
    Object limit = prepareParam(raw, name);
    return (DateSchema) test(JsObject.of(
        "message", message == Js.UNDEFINED ? Locale.message("date", name) : message,
        "name", name,
        "exclusive", true,
        "params", JsObject.of(name, raw),
        "skipAbsent", true,
        "test", (JsFunction) (self, args) -> {
          Object value = JsFunction.arg(args, 0);
          Object resolved = ((TestContext) self).resolve(limit);
          return lower ? Js.ge(value, resolved) : Js.le(value, resolved);
        }));
  }

  /**
   * {@code min(min, message)}.
   *
   * @param min the earliest date (or a value cast to one, or a reference)
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   * @throws JsError a {@code TypeError} when {@code min} cannot be cast to a date
   */
  public DateSchema min(Object min, Object message) {
    return bound("min", min, message, true);
  }

  /**
   * {@code max(max, message)}.
   *
   * @param max the latest date (or a value cast to one, or a reference)
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   * @throws JsError a {@code TypeError} when {@code max} cannot be cast to a date
   */
  public DateSchema max(Object max, Object message) {
    return bound("max", max, message, false);
  }
}
