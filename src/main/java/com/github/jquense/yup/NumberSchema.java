/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsNumbers;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsRegExp;
import com.github.jquense.yup.js.UnicodeCase;
import java.util.List;

/** {@code number()}: numbers other than NaN; strings (without white space) and other values are cast. */
public class NumberSchema extends Schema {
  private static final JsRegExp WHITE_SPACE = new JsRegExp("\\s", "g");
  private static final List<String> ROUNDING = List.of("ceil", "floor", "round", "trunc");

  /** {@code number()}. */
  public NumberSchema() {
    super("number", null);
    withMutation(s -> s.transform((JsFunction) (self, args) -> {
      Object value = args[0];
      Schema ctx = (Schema) args[2];
      if (!Js.truthy(ctx.spec.get("coerce"))) {
        return value;
      }
      Object parsed = value;
      if (parsed instanceof String text) {
        String compact = WHITE_SPACE.replace(text, match -> "");
        if (compact.isEmpty()) {
          return Double.NaN;
        }
        parsed = JsNumbers.stringToNumber(compact);
      }
      if (Js.truthy(ctx.isType(parsed)) || parsed == null) {
        return parsed;
      }
      return JsNumbers.parseFloat(Js.toJsString(parsed));
    }));
  }

  private NumberSchema(boolean blank) {}

  @Override
  protected Schema blank() {
    return new NumberSchema(true);
  }

  @Override
  public Prototype prototype() {
    return Prototype.NUMBER;
  }

  @Override
  protected Object typeCheck(Object value) {
    return value instanceof Double d && !Double.isNaN(d);
  }

  private NumberSchema bound(String name, String paramName, Object javaLimit, Object message, String operator) {
    Object limit = Js.fromJava(javaLimit);
    return (NumberSchema) test(JsObject.of(
        "message", message,
        "name", name,
        "exclusive", true,
        "params", JsObject.of(paramName, limit),
        "skipAbsent", true,
        "test", (JsFunction) (self, args) -> {
          Object value = JsFunction.arg(args, 0);
          Object resolved = ((TestContext) self).resolve(limit);
          return switch (operator) {
            case ">=" -> Js.ge(value, resolved);
            case "<=" -> Js.le(value, resolved);
            case "<" -> Js.lt(value, resolved);
            default -> Js.gt(value, resolved);
          };
        }));
  }

  private static Object messageOr(Object message, String name) {
    return message == Js.UNDEFINED ? Locale.message("number", name) : message;
  }

  /**
   * {@code min(min, message)}.
   *
   * @param min the minimum (a number or reference)
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public NumberSchema min(Object min, Object message) {
    return bound("min", "min", min, messageOr(message, "min"), ">=");
  }

  /**
   * {@code max(max, message)}.
   *
   * @param max the maximum (a number or reference)
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public NumberSchema max(Object max, Object message) {
    return bound("max", "max", max, messageOr(message, "max"), "<=");
  }

  /**
   * {@code lessThan(less, message)} (a test named {@code max}).
   *
   * @param less the exclusive maximum
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public NumberSchema lessThan(Object less, Object message) {
    return bound("max", "less", less, messageOr(message, "lessThan"), "<");
  }

  /**
   * {@code moreThan(more, message)} (a test named {@code min}).
   *
   * @param more the exclusive minimum
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public NumberSchema moreThan(Object more, Object message) {
    return bound("min", "more", more, messageOr(message, "moreThan"), ">");
  }

  /**
   * {@code positive(message)}: {@code moreThan(0)}.
   *
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public NumberSchema positive(Object message) {
    return moreThan(0.0, messageOr(message, "positive"));
  }

  /**
   * {@code negative(message)}: {@code lessThan(0)}.
   *
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public NumberSchema negative(Object message) {
    return lessThan(0.0, messageOr(message, "negative"));
  }

  /**
   * {@code integer(message)}.
   *
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public NumberSchema integer(Object message) {
    // A new function for every call, as each call makes a new closure: two integer() tests are not deduplicated.
    JsFunction isInteger = JsFunction.named("test", (self, args) -> JsNumbers.isInteger(JsFunction.arg(args, 0)));
    return (NumberSchema) test(JsObject.of(
        "name", "integer",
        "message", messageOr(message, "integer"),
        "skipAbsent", true,
        "test", isInteger));
  }

  /** @return {@code truncate()}: {@code value | 0} */
  public NumberSchema truncate() {
    return (NumberSchema) transform((JsFunction) (self, args) -> !Js.isNullish(args[0]) ? Js.toInt32(args[0]) : args[0]);
  }

  /**
   * {@code round(method)}: {@code ceil}, {@code floor}, {@code round} (the default) or {@code trunc}.
   *
   * @param method the rounding method
   * @return the new schema
   * @throws JsError a {@code TypeError} for another method
   */
  public NumberSchema round(Object method) {
    String name;
    if (Js.isNullish(method)) {
      name = "round";
    } else {
      if (!(method instanceof String text)) {
        throw JsError.typeError("method?.toLowerCase is not a function");
      }
      String lower = UnicodeCase.toLowerCase(text);
      name = lower.isEmpty() ? "round" : lower;
    }
    if (name.equals("trunc")) {
      return truncate();
    }
    if (!ROUNDING.contains(UnicodeCase.toLowerCase(name))) {
      throw JsError.typeError("Only valid options for round() are: " + String.join(", ", ROUNDING));
    }
    String chosen = name;
    return (NumberSchema) transform((JsFunction) (self, args) -> {
      Object value = args[0];
      if (Js.isNullish(value)) {
        return value;
      }
      Object numeric = Js.toNumeric(value);
      if (!(numeric instanceof Double d)) {
        throw JsError.typeError("Cannot convert a BigInt value to a number");
      }
      return switch (chosen) {
        case "ceil" -> Math.ceil(d);
        case "floor" -> Math.floor(d);
        default -> JsNumbers.round(d);
      };
    });
  }
}
