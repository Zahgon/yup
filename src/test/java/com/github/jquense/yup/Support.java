/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsArray;
import com.github.jquense.yup.js.JsDate;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsException;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsonWriter;
import java.util.Objects;

/** Assertions and shorthands for the tests (plain JDK: the tests run with Surefire's POJO provider). */
public final class Support {
  /** {@code undefined}. */
  public static final Object U = Js.UNDEFINED;

  private Support() {}

  /**
   * Fails unless the values are equal; JavaScript values are compared like Jest's {@code toEqual}: by their JSON
   * form, ignoring the order of object members.
   *
   * @param expected the expected value
   * @param actual the actual value
   */
  public static void eq(Object expected, Object actual) {
    if (!Objects.equals(canonical(expected), canonical(actual))) {
      throw new AssertionError("expected " + show(expected) + " but was " + show(actual));
    }
  }

  private static String canonical(Object value) {
    if (value instanceof JsObject object) {
      java.util.List<String> keys = new java.util.ArrayList<>(object.keys());
      java.util.Collections.sort(keys);
      StringBuilder out = new StringBuilder("{");
      for (String key : keys) {
        out.append(out.length() > 1 ? "," : "").append(JsonWriter.quote(key)).append(':')
            .append(canonical(object.get(key)));
      }
      return out.append('}').toString();
    }
    if (value instanceof JsArray array) {
      StringBuilder out = new StringBuilder("[");
      for (int i = 0; i < array.length(); i++) {
        out.append(i > 0 ? "," : "").append(array.has(i) ? canonical(array.get(i)) : "<hole>");
      }
      return out.append(']').toString();
    }
    return show(value);
  }

  /**
   * Fails unless the condition holds.
   *
   * @param condition the condition
   * @param what what was checked
   */
  public static void check(boolean condition, String what) {
    if (!condition) {
      throw new AssertionError("expected " + what);
    }
  }

  private static String show(Object value) {
    if (value == U) {
      return "undefined";
    }
    if (value instanceof Integer i) {
      return Js.toJsString((double) i);
    }
    if (value instanceof JsDate date) {
      return "Date(" + date + ")";
    }
    try {
      return Js.toJsString(JsonWriter.stringify(value));
    } catch (RuntimeException e) {
      return String.valueOf(value);
    }
  }

  /**
   * Runs the code and returns what it throws.
   *
   * @param code the code
   * @return the thrown JavaScript value
   */
  public static Object thrown(Runnable code) {
    try {
      code.run();
    } catch (RuntimeException e) {
      return JsException.valueOf(e);
    }
    throw new AssertionError("expected an exception");
  }

  /**
   * Fails unless the code throws an error with the given name and a message containing the text.
   *
   * @param name the error name
   * @param message a part of the message
   * @param code the code
   */
  public static void throwsError(String name, String message, Runnable code) {
    Object error = thrown(code);
    eq(name, Js.get(error, "name"));
    String text = Js.toJsString(Js.get(error, "message"));
    check(text.contains(message), "a message containing \"" + message + "\" but was \"" + text + "\"");
  }

  /** @return a JavaScript object of name/value pairs (Java ints become numbers) */
  public static JsObject obj(Object... pairs) {
    JsObject object = new JsObject();
    for (int i = 0; i < pairs.length; i += 2) {
      object.put((String) pairs[i], js(pairs[i + 1]));
    }
    return object;
  }

  /** @return a JavaScript array (Java ints become numbers) */
  public static JsArray arr(Object... items) {
    JsArray array = new JsArray();
    for (Object item : items) {
      array.push(js(item));
    }
    return array;
  }

  /**
   * @param value a Java value
   * @return the JavaScript value (ints and longs become numbers)
   */
  public static Object js(Object value) {
    if (value instanceof Integer i) {
      return (double) i;
    }
    if (value instanceof Long l) {
      return (double) l;
    }
    return value;
  }

  /** @return a function returning a constant */
  public static JsFunction constant(Object value) {
    return (self, args) -> js(value);
  }

  /** @return {@code schema.cast(value)} */
  public static Object cast(ISchema schema, Object value) {
    return schema.cast(js(value), U);
  }

  /** @return {@code schema.cast(value, options)} */
  public static Object cast(ISchema schema, Object value, JsObject options) {
    return schema.cast(js(value), options);
  }

  /** @return {@code await schema.isValid(value)} */
  public static boolean isValid(ISchema schema, Object value) {
    return (Boolean) schema.isValid(js(value), U).await();
  }

  /** @return {@code await schema.isValid(value, options)} */
  public static boolean isValid(ISchema schema, Object value, JsObject options) {
    return (Boolean) schema.isValid(js(value), options).await();
  }

  /** @return {@code await schema.validate(value)} */
  public static Object validate(ISchema schema, Object value) {
    return schema.validate(js(value), U).await();
  }

  /** @return {@code await schema.validate(value, options)} */
  public static Object validate(ISchema schema, Object value, JsObject options) {
    return schema.validate(js(value), options).await();
  }

  /**
   * @return the validation error {@code schema.validate(value, options)} rejects with
   */
  public static ValidationError invalid(ISchema schema, Object value, Object options) {
    Object error = thrown(() -> schema.validate(js(value), options).await());
    check(error instanceof ValidationError, "a ValidationError but got " + error);
    return (ValidationError) error;
  }

  /** @return the validation error {@code schema.validate(value)} rejects with */
  public static ValidationError invalid(ISchema schema, Object value) {
    return invalid(schema, value, U);
  }

  /** @return the error messages of a failed validation */
  public static Object errors(ISchema schema, Object value, Object options) {
    return invalid(schema, value, options).errors();
  }

  /** @return a date in UTC, months 0-based */
  public static JsDate date(int year, int month, int day) {
    return new JsDate(JsDate.fromParts(year, month, day));
  }

  /** @return an error's name, for {@link #eq} */
  public static Object nameOf(JsError error) {
    return error.name();
  }
}
