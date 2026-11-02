/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsArray;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsNumbers;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsRegExp;
import com.github.jquense.yup.js.UnicodeCase;
import com.github.jquense.yup.util.ParseIsoDate;

/** {@code string()}: strings; other values (except arrays and plain objects) are cast with {@code toString()}. */
public class StringSchema extends Schema {
  // Taken from HTML spec: https://html.spec.whatwg.org/multipage/input.html#valid-e-mail-address
  private static final JsRegExp EMAIL = new JsRegExp("^[a-zA-Z0-9.!#$%&'*+\\/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]"
      + "{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");
  private static final JsRegExp URL = new JsRegExp(
      "^((https?|ftp):)?\\/\\/(((([a-z]|\\d|-|\\.|_|~|[\\u00A0-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFEF])|(%["
      + "\\da-f]{2})|[!\\$&'\\(\\)\\*\\+,;=]|:)*@)?(((\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])\\.(\\d|[1-9]\\d"
      + "|1\\d\\d|2[0-4]\\d|25[0-5])\\.(\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])\\.(\\d|[1-9]\\d|1\\d\\d|2[0-4"
      + "]\\d|25[0-5]))|((([a-z]|\\d|[\\u00A0-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFEF])|(([a-z]|\\d|[\\u00A0-\\"
      + "uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFEF])([a-z]|\\d|-|\\.|_|~|[\\u00A0-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\u"
      + "FFEF])*([a-z]|\\d|[\\u00A0-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFEF])))\\.)+(([a-z]|[\\u00A0-\\uD7FF\\u"
      + "F900-\\uFDCF\\uFDF0-\\uFFEF])|(([a-z]|[\\u00A0-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFEF])([a-z]|\\d|-|"
      + "\\.|_|~|[\\u00A0-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFEF])*([a-z]|[\\u00A0-\\uD7FF\\uF900-\\uFDCF\\uFD"
      + "F0-\\uFFEF])))\\.?)(:\\d*)?)(\\/((([a-z]|\\d|-|\\.|_|~|[\\u00A0-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFE"
      + "F])|(%[\\da-f]{2})|[!\\$&'\\(\\)\\*\\+,;=]|:|@)+(\\/(([a-z]|\\d|-|\\.|_|~|[\\u00A0-\\uD7FF\\uF900-\\"
      + "uFDCF\\uFDF0-\\uFFEF])|(%[\\da-f]{2})|[!\\$&'\\(\\)\\*\\+,;=]|:|@)*)*)?)?(\\?((([a-z]|\\d|-|\\.|_|~|"
      + "[\\u00A0-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFEF])|(%[\\da-f]{2})|[!\\$&'\\(\\)\\*\\+,;=]|:|@)|[\\uE00"
      + "0-\\uF8FF]|\\/|\\?)*)?(\\#((([a-z]|\\d|-|\\.|_|~|[\\u00A0-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFEF])|(%"
      + "[\\da-f]{2})|[!\\$&'\\(\\)\\*\\+,;=]|:|@)|\\/|\\?)*)?$", "i");
  private static final JsRegExp UUID = new JsRegExp("^(?:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-"
      + "[0-9a-f]{12}|00000000-0000-0000-0000-000000000000)$", "i");
  private static final JsRegExp ISO_DATE_TIME = new JsRegExp(
      "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(([+-]\\d{2}(:?\\d{2})?)|Z)$");

  private static final JsFunction IS_TRIMMED = (self, args) -> {
    Object value = JsFunction.arg(args, 0);
    return Js.isNullish(value) || Js.strictEquals(value, JsNumbers.trim(Js.toJsString(value)));
  };

  /** {@code string()}. */
  public StringSchema() {
    super("string", null);
    withMutation(s -> s.transform((JsFunction) (self, args) -> {
      Object value = args[0];
      Schema ctx = (Schema) args[2];
      if (!Js.truthy(ctx.spec.get("coerce")) || Js.truthy(ctx.isType(value))) {
        return value;
      }
      if (value instanceof JsArray) {
        return value;
      }
      Object strValue = value;
      if (!Js.isNullish(value) && !(value instanceof JsObject object && object.isNullPrototype()
          && !object.has("toString"))) {
        strValue = Js.callToString(value);
      }
      if ("[object Object]".equals(strValue)) {
        return value;
      }
      return strValue;
    }));
  }

  private StringSchema(boolean blank) {}

  @Override
  protected Schema blank() {
    return new StringSchema(true);
  }

  @Override
  public Prototype prototype() {
    return Prototype.STRING;
  }

  @Override
  protected Object typeCheck(Object value) {
    return value instanceof String;
  }

  @Override
  public StringSchema required(Object message) {
    // A new function for every call, as each call makes a new closure: two required() tests are not deduplicated.
    JsFunction nonEmpty = JsFunction.named("test", (self, args) -> Js.truthy(Js.get(JsFunction.arg(args, 0), "length")));
    return (StringSchema) super.required(message).withMutation(schema -> schema.test(JsObject.of(
        "message", Js.truthy(message) ? message : Locale.message("mixed", "required"),
        "name", "required",
        "skipAbsent", true,
        "test", nonEmpty)));
  }

  @Override
  public StringSchema notRequired() {
    return (StringSchema) super.notRequired().withMutation(schema -> {
      schema.tests.removeIf(test -> Js.strictEquals(test.name(), "required"));
      return schema;
    });
  }

  private StringSchema limit(String name, String paramName, Object javaLimit, Object message, String operator) {
    Object limit = Js.fromJava(javaLimit);
    return (StringSchema) test(JsObject.of(
        "message", message == Js.UNDEFINED ? Locale.message("string", name) : message,
        "name", name,
        "exclusive", true,
        "params", JsObject.of(paramName, limit),
        "skipAbsent", true,
        "test", (JsFunction) (self, args) -> {
          Object length = Js.get(JsFunction.arg(args, 0), "length");
          Object bound = ((TestContext) self).resolve(limit);
          return switch (operator) {
            case "===" -> Js.strictEquals(length, bound);
            case ">=" -> Js.ge(length, bound);
            default -> Js.le(length, bound);
          };
        }));
  }

  /**
   * {@code length(length, message)}.
   *
   * @param length the exact length (a number or reference)
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public StringSchema length(Object length, Object message) {
    return limit("length", "length", length, message, "===");
  }

  /**
   * {@code min(min, message)}.
   *
   * @param min the minimum length (a number or reference)
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public StringSchema min(Object min, Object message) {
    return limit("min", "min", min, message, ">=");
  }

  /**
   * {@code max(max, message)}.
   *
   * @param max the maximum length (a number or reference)
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public StringSchema max(Object max, Object message) {
    return limit("max", "max", max, message, "<=");
  }

  /**
   * {@code matches(regex, options)}: {@code options} is a message or {@code {excludeEmptyString, message, name}}.
   *
   * @param regex the regular expression (anything else is converted to one)
   * @param options the message or options
   * @return the new schema
   */
  public StringSchema matches(Object regex, Object options) {
    Object excludeEmptyString = false;
    Object message = Js.UNDEFINED;
    Object name = Js.UNDEFINED;
    if (Js.truthy(options)) {
      if (Js.typeOf(options).equals("object")) {
        excludeEmptyString = Js.get(options, "excludeEmptyString");
        if (excludeEmptyString == Js.UNDEFINED) {
          excludeEmptyString = false;
        }
        message = Js.get(options, "message");
        name = Js.get(options, "name");
      } else {
        message = options;
      }
    }
    Object exclude = excludeEmptyString;
    return (StringSchema) test(JsObject.of(
        "name", Js.truthy(name) ? name : "matches",
        "message", Js.truthy(message) ? message : Locale.message("string", "matches"),
        "params", JsObject.of("regex", regex),
        "skipAbsent", true,
        "test", (JsFunction) (self, args) -> {
          Object value = JsFunction.arg(args, 0);
          if ("".equals(value) && Js.truthy(exclude)) {
            return true;
          }
          JsRegExp expression = regex instanceof JsRegExp r ? r : JsRegExp.create(regex == null ? null : regex);
          return expression.search((String) value) != -1;
        }));
  }

  private StringSchema named(JsRegExp regex, String name, Object message, String localeName, boolean excludeEmpty) {
    return matches(regex, JsObject.of(
        "name", name,
        "message", message == Js.UNDEFINED ? Locale.message("string", localeName) : message,
        "excludeEmptyString", excludeEmpty));
  }

  /**
   * {@code email(message)}.
   *
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public StringSchema email(Object message) {
    return named(EMAIL, "email", message, "email", true);
  }

  /**
   * {@code url(message)}.
   *
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public StringSchema url(Object message) {
    return named(URL, "url", message, "url", true);
  }

  /**
   * {@code uuid(message)}.
   *
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public StringSchema uuid(Object message) {
    return named(UUID, "uuid", message, "uuid", false);
  }

  /**
   * {@code datetime(options)}: an ISO date-time; {@code options} is a message or {@code {message, allowOffset,
   * precision}}.
   *
   * @param options the message or options
   * @return the new schema
   */
  public StringSchema datetime(Object options) {
    Object message = "";
    Object allowOffset = Js.UNDEFINED;
    Object precision = Js.UNDEFINED;
    if (Js.truthy(options)) {
      if (Js.typeOf(options).equals("object")) {
        message = Js.get(options, "message");
        if (message == Js.UNDEFINED) {
          message = "";
        }
        allowOffset = Js.get(options, "allowOffset");
        if (allowOffset == Js.UNDEFINED) {
          allowOffset = false;
        }
        precision = Js.get(options, "precision");
      } else {
        message = options;
      }
    }
    Object msg = message;
    Object offsetAllowed = allowOffset;
    Object digits = precision;
    return (StringSchema) matches(ISO_DATE_TIME, JsObject.of(
        "name", "datetime",
        "message", Js.truthy(msg) ? msg : Locale.message("string", "datetime"),
        "excludeEmptyString", true))
        .test(JsObject.of(
            "name", "datetime_offset",
            "message", Js.truthy(msg) ? msg : Locale.message("string", "datetime_offset"),
            "params", JsObject.of("allowOffset", offsetAllowed),
            "skipAbsent", true,
            "test", (JsFunction) (self, args) -> {
              Object value = JsFunction.arg(args, 0);
              if (!Js.truthy(value) || Js.truthy(offsetAllowed)) {
                return true;
              }
              ParseIsoDate.DateStruct struct = ParseIsoDate.parseDateStruct(value);
              if (struct == null) {
                return false;
              }
              return Js.truthy(struct.z());
            }))
        .test(JsObject.of(
            "name", "datetime_precision",
            "message", Js.truthy(msg) ? msg : Locale.message("string", "datetime_precision"),
            "params", JsObject.of("precision", digits),
            "skipAbsent", true,
            "test", (JsFunction) (self, args) -> {
              Object value = JsFunction.arg(args, 0);
              if (!Js.truthy(value) || Js.isNullish(digits)) {
                return true;
              }
              ParseIsoDate.DateStruct struct = ParseIsoDate.parseDateStruct(value);
              if (struct == null) {
                return false;
              }
              return Js.strictEquals(struct.precision(), digits);
            }));
  }

  /** @return {@code ensure()}: defaults to {@code ""} and turns {@code null} into {@code ""} */
  public StringSchema ensure() {
    return (StringSchema) defaultValue("").transform((JsFunction) (self, args) -> args[0] == null ? "" : args[0]);
  }

  /**
   * {@code trim(message)}: trims, and in strict mode requires a trimmed string.
   *
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public StringSchema trim(Object message) {
    return (StringSchema) transform((JsFunction) (self, args) -> {
      Object value = args[0];
      return !Js.isNullish(value) ? stringMethod(value, "trim") : value;
    }).test(JsObject.of(
        "message", message == Js.UNDEFINED ? Locale.message("string", "trim") : message,
        "name", "trim",
        "test", IS_TRIMMED));
  }

  /**
   * {@code lowercase(message)}.
   *
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public StringSchema lowercase(Object message) {
    return changeCase(false, message);
  }

  /**
   * {@code uppercase(message)}.
   *
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public StringSchema uppercase(Object message) {
    return changeCase(true, message);
  }

  private StringSchema changeCase(boolean upper, Object message) {
    String method = upper ? "toUpperCase" : "toLowerCase";
    return (StringSchema) transform((JsFunction) (self, args) -> {
      Object value = args[0];
      return !Js.isNullish(value) ? stringMethod(value, method) : value;
    }).test(JsObject.of(
        "message", message == Js.UNDEFINED ? Locale.message("string", upper ? "uppercase" : "lowercase") : message,
        "name", "string_case",
        "exclusive", true,
        "skipAbsent", true,
        "test", (JsFunction) (self, args) -> {
          Object value = JsFunction.arg(args, 0);
          return Js.isNullish(value) || Js.strictEquals(value, stringMethod(value, method));
        }));
  }

  // val.trim() / value.toUpperCase() / value.toLowerCase(): only strings have them.
  private static Object stringMethod(Object value, String method) {
    if (!(value instanceof String s)) {
      String variable = method.equals("trim") ? "val" : "value";
      throw com.github.jquense.yup.js.JsError.typeError(variable + "." + method + " is not a function");
    }
    return switch (method) {
      case "trim" -> JsNumbers.trim(s);
      case "toUpperCase" -> UnicodeCase.toUpperCase(s);
      default -> UnicodeCase.toLowerCase(s);
    };
  }
}
