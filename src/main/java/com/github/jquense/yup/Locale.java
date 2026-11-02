/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsArray;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.util.PrintValue;

/**
 * The default messages ({@code defaultLocale}), one section per schema type, and {@code setLocale}. Schemas read a
 * message when the method that uses it is called.
 */
public final class Locale {
  private static final JsObject LOCALE = new JsObject(true);

  /** {@code mixed.notType}: the message of a failed type check. */
  private static final JsFunction MIXED_NOT_TYPE = JsFunction.named("notType", (self, args) -> {
    Object params = args[0];
    Object path = Js.get(params, "path");
    Object type = Js.get(params, "type");
    Object value = Js.get(params, "value");
    Object originalValue = Js.get(params, "originalValue");
    String castMessage = !Js.isNullish(originalValue) && !Js.strictEquals(originalValue, value)
        ? " (cast from the value `" + PrintValue.print(originalValue, true) + "`)."
        : ".";
    if (!Js.strictEquals(type, "mixed")) {
      return Js.toJsString(path) + " must be a `" + Js.toJsString(type) + "` type, but the final value was: `"
          + PrintValue.print(value, true) + "`" + castMessage;
    }
    return Js.toJsString(path) + " must match the configured type. The validated value was: `"
        + PrintValue.print(value, true) + "`" + castMessage;
  });

  /** {@code tuple.notType}: too few or too many items, otherwise the {@code mixed.notType} message. */
  private static final JsFunction TUPLE_NOT_TYPE = JsFunction.named("notType", (self, args) -> {
    Object params = args[0];
    Object path = Js.get(params, "path");
    Object value = Js.get(params, "value");
    Object spec = Js.get(params, "spec");
    Object types = Js.read(spec, "types");
    double typeLength = Js.toNumber(Js.read(types, "length"));
    if (value instanceof JsArray array) {
      String count = Js.toJsString(typeLength);
      if (array.length() < typeLength) {
        return Js.toJsString(path) + " tuple value has too few items, expected a length of " + count + " but got "
            + array.length() + " for value: `" + PrintValue.print(value, true) + "`";
      }
      if (array.length() > typeLength) {
        return Js.toJsString(path) + " tuple value has too many items, expected a length of " + count + " but got "
            + array.length() + " for value: `" + PrintValue.print(value, true) + "`";
      }
    }
    return ValidationError.formatError(message("mixed", "notType"), params);
  });

  static {
    reset();
  }

  private Locale() {}

  /** Restores every default message, dropping the ones {@link #setLocale} added. */
  public static synchronized void reset() {
    put("mixed",
        "default", "${path} is invalid",
        "required", "${path} is a required field",
        "defined", "${path} must be defined",
        "notNull", "${path} cannot be null",
        "oneOf", "${path} must be one of the following values: ${values}",
        "notOneOf", "${path} must not be one of the following values: ${values}",
        "notType", MIXED_NOT_TYPE);
    put("string",
        "length", "${path} must be exactly ${length} characters",
        "min", "${path} must be at least ${min} characters",
        "max", "${path} must be at most ${max} characters",
        "matches", "${path} must match the following: \"${regex}\"",
        "email", "${path} must be a valid email",
        "url", "${path} must be a valid URL",
        "uuid", "${path} must be a valid UUID",
        "datetime", "${path} must be a valid ISO date-time",
        "datetime_precision",
        "${path} must be a valid ISO date-time with a sub-second precision of exactly ${precision} digits",
        "datetime_offset", "${path} must be a valid ISO date-time with UTC \"Z\" timezone",
        "trim", "${path} must be a trimmed string",
        "lowercase", "${path} must be a lowercase string",
        "uppercase", "${path} must be a upper case string");
    put("number",
        "min", "${path} must be greater than or equal to ${min}",
        "max", "${path} must be less than or equal to ${max}",
        "lessThan", "${path} must be less than ${less}",
        "moreThan", "${path} must be greater than ${more}",
        "positive", "${path} must be a positive number",
        "negative", "${path} must be a negative number",
        "integer", "${path} must be an integer");
    put("date",
        "min", "${path} field must be later than ${min}",
        "max", "${path} field must be at earlier than ${max}");
    put("object",
        "noUnknown", "${path} field has unspecified keys: ${unknown}",
        "exact", "${path} object contains unknown properties: ${properties}");
    put("array",
        "min", "${path} field must have at least ${min} items",
        "max", "${path} field must have less than or equal to ${max} items",
        "length", "${path} must have ${length} items");
    put("boolean", "isValue", "${path} field must be ${value}");
    put("tuple", "notType", TUPLE_NOT_TYPE);
  }

  // Clears a section (keeping the object) and fills it with the given name/message pairs.
  private static void put(String section, Object... pairs) {
    JsObject target = LOCALE.get(section) instanceof JsObject existing ? existing : new JsObject();
    for (String key : target.keys()) {
      target.delete(key);
    }
    for (int i = 0; i < pairs.length; i += 2) {
      target.put((String) pairs[i], pairs[i + 1]);
    }
    LOCALE.put(section, target);
  }

  /**
   * {@code defaultLocale}: the live message sections.
   *
   * @return the locale object
   */
  public static JsObject defaultLocale() {
    return LOCALE;
  }

  /**
   * The current message of a section.
   *
   * @param section {@code mixed}, {@code string}, {@code number}, {@code date}, {@code object}, {@code array},
   *     {@code boolean} or {@code tuple}
   * @param name the message name
   * @return the message
   */
  public static Object message(String section, String name) {
    return Js.get(LOCALE.get(section), name);
  }

  /**
   * {@code setLocale(custom)}: copies every message of every section of {@code custom} into the locale.
   *
   * @param custom an object of sections
   * @throws JsError a {@code TypeError} as JavaScript fails on {@code null} sections or unknown section names
   */
  public static synchronized void setLocale(Object custom) {
    for (String type : Js.keys(custom)) {
      Object section = Js.get(custom, type);
      for (String method : Js.keys(section)) {
        Object target = LOCALE.get(type);
        if (!(target instanceof JsObject targetSection)) {
          throw JsError.typeError("Cannot set properties of undefined (setting '" + method + "')");
        }
        targetSection.put(method, Js.get(section, method));
      }
    }
  }
}
