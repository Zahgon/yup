/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.cli;

import com.github.jquense.yup.ArraySchema;
import com.github.jquense.yup.BooleanSchema;
import com.github.jquense.yup.DateSchema;
import com.github.jquense.yup.ISchema;
import com.github.jquense.yup.Lazy;
import com.github.jquense.yup.NumberSchema;
import com.github.jquense.yup.ObjectSchema;
import com.github.jquense.yup.Schema;
import com.github.jquense.yup.StringSchema;
import com.github.jquense.yup.Yup;
import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsFunction;
import java.util.List;
import java.util.Set;

/** The schema factories and builder methods a schema expression can name, called as JavaScript would call them. */
final class Methods {
  /** The factory names. */
  static final Set<String> FACTORIES = Set.of("mixed", "string", "number", "boolean", "bool", "date", "object",
      "array", "tuple", "lazy");

  /** The builder methods of the public API. */
  static final Set<String> METHODS = Set.of("label", "meta", "default", "strict", "optional", "defined", "nullable",
      "nonNullable", "required", "notRequired", "transform", "test", "when", "typeError", "oneOf", "equals", "is",
      "notOneOf", "not", "nope", "strip", "concat", "length", "min", "max", "matches", "email", "url", "uuid",
      "datetime", "ensure", "trim", "lowercase", "uppercase", "lessThan", "moreThan", "positive", "negative", "integer",
      "truncate", "round", "isTrue", "isFalse", "shape", "pick", "omit", "partial", "deepPartial", "from", "json",
      "exact", "stripUnknown", "noUnknown", "unknown", "transformKeys", "camelCase", "snakeCase", "constantCase", "of",
      "compact");

  private Methods() {}

  /**
   * Calls a factory with JavaScript arguments.
   *
   * @param name the factory name
   * @param args the arguments
   * @return the schema
   */
  static Object create(String name, List<Object> args) {
    Object first = arg(args, 0);
    return switch (name) {
      case "mixed" -> Yup.mixed(first);
      case "string" -> Yup.string();
      case "number" -> Yup.number();
      case "boolean", "bool" -> Yup.bool();
      case "date" -> Yup.date();
      case "object" -> Yup.object(first);
      case "array" -> Yup.array(first);
      case "tuple" -> Yup.tuple(first);
      default -> Yup.lazy(first);
    };
  }

  private static Object arg(List<Object> args, int index) {
    return index < args.size() ? args.get(index) : Js.UNDEFINED;
  }

  /**
   * Whether a schema expression may call the method: a public builder method or one the request installed, that the
   * schema has.
   *
   * @param schema the schema
   * @param name the method name
   * @param ctx the request
   * @return whether the call is allowed
   */
  static boolean has(ISchema schema, String name, Context ctx) {
    if (!METHODS.contains(name) && !ctx.added.contains(name)) {
      return false;
    }
    return schema.prototype().hasBuiltin(name) || schema.prototype().addedMethod(name) != null;
  }

  /**
   * Calls a method the schema has.
   *
   * @param schema the schema
   * @param name the method name
   * @param args the arguments
   * @return the result
   */
  static Object call(ISchema schema, String name, List<Object> args) {
    JsFunction added = schema.prototype().addedMethod(name);
    if (added != null) {
      return added.call(schema, args.toArray());
    }
    if (schema instanceof Lazy lazy) {
      return name.equals("optional") ? lazy.optional() : args.isEmpty() ? lazy.meta() : lazy.meta(args.get(0));
    }
    Schema s = (Schema) schema;
    Object a = arg(args, 0);
    Object b = arg(args, 1);
    return switch (name) {
      case "label" -> s.label(a);
      case "meta" -> args.isEmpty() ? s.meta() : s.meta(a);
      case "default" -> args.isEmpty() ? s.defaultValue() : s.defaultValue(a);
      case "strict" -> s.strict(a);
      case "optional" -> s.optional();
      case "defined" -> s.defined(a);
      case "nullable" -> s.nullable();
      case "nonNullable" -> s.nonNullable(a);
      case "required" -> s.required(a);
      case "notRequired" -> s.notRequired();
      case "transform" -> s.transform(a);
      case "test" -> s.test(args.toArray());
      case "when" -> s.when(a, b);
      case "typeError" -> s.typeError(a);
      case "oneOf", "equals", "is" -> s.oneOf(a, b);
      case "notOneOf", "not", "nope" -> s.notOneOf(a, b);
      case "strip" -> s.strip(a);
      case "concat" -> s.concat(a);
      default -> typed(s, name, args, a, b);
    };
  }

  private static Object typed(Schema s, String name, List<Object> args, Object a, Object b) {
    if (s instanceof StringSchema str) {
      return switch (name) {
        case "length" -> str.length(a, b);
        case "min" -> str.min(a, b);
        case "max" -> str.max(a, b);
        case "matches" -> str.matches(a, b);
        case "email" -> str.email(a);
        case "url" -> str.url(a);
        case "uuid" -> str.uuid(a);
        case "datetime" -> str.datetime(a);
        case "ensure" -> str.ensure();
        case "trim" -> str.trim(a);
        case "lowercase" -> str.lowercase(a);
        default -> str.uppercase(a);
      };
    }
    if (s instanceof NumberSchema num) {
      return switch (name) {
        case "min" -> num.min(a, b);
        case "max" -> num.max(a, b);
        case "lessThan" -> num.lessThan(a, b);
        case "moreThan" -> num.moreThan(a, b);
        case "positive" -> num.positive(a);
        case "negative" -> num.negative(a);
        case "integer" -> num.integer(a);
        case "truncate" -> num.truncate();
        default -> num.round(a);
      };
    }
    if (s instanceof BooleanSchema bool) {
      return name.equals("isTrue") ? bool.isTrue(a) : bool.isFalse(a);
    }
    if (s instanceof DateSchema date) {
      return name.equals("min") ? date.min(a, b) : date.max(a, b);
    }
    if (s instanceof ObjectSchema obj) {
      return switch (name) {
        case "shape" -> obj.shape(a, b);
        case "pick" -> obj.pick(a);
        case "omit" -> obj.omit(a);
        case "partial" -> obj.partial();
        case "deepPartial" -> obj.deepPartial();
        case "from" -> obj.from(a, b, arg(args, 2));
        case "json" -> obj.json();
        case "exact" -> obj.exact(a);
        case "stripUnknown" -> obj.stripUnknown();
        case "noUnknown" -> obj.noUnknown(a, b);
        case "unknown" -> obj.unknown(a, b);
        case "transformKeys" -> obj.transformKeys(a);
        case "camelCase" -> obj.camelCase();
        case "snakeCase" -> obj.snakeCase();
        default -> obj.constantCase();
      };
    }
    ArraySchema array = (ArraySchema) s;
    return switch (name) {
      case "of" -> array.of(a);
      case "json" -> array.json();
      case "length" -> array.length(a, b);
      case "min" -> array.min(a, b);
      case "max" -> array.max(a, b);
      case "ensure" -> array.ensure();
      default -> array.compact(a);
    };
  }
}
