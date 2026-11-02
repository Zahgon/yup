/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.cli;

import com.github.jquense.yup.ISchema;
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
import com.github.jquense.yup.js.JsonWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Turns request JSON into JavaScript values: tagged values, schema expressions and definitions. */
final class Decoder {
  private Decoder() {}

  /**
   * @param value any value
   * @return whether it is an object that is not an array ({@code value !== null && typeof value === 'object'})
   */
  static boolean isObject(Object value) {
    return value != null && Js.typeOf(value).equals("object") && !(value instanceof JsArray);
  }

  /**
   * {@code `${JSON.stringify(value)}`}.
   *
   * @param value a request value
   * @return its JSON text
   */
  static String quote(Object value) {
    return Js.toJsString(JsonWriter.stringify(value));
  }

  /**
   * Decodes a request value.
   *
   * @param raw the JSON value
   * @param ctx the request
   * @param args the arguments of the enclosing {@code method} function, or null
   * @return the JavaScript value
   */
  static Object decode(Object raw, Context ctx, List<Object> args) {
    if (raw instanceof JsArray array) {
      JsArray out = JsArray.ofLength(array.length());
      for (int i = 0; i < array.length(); i++) {
        Object item = array.get(i);
        if (item instanceof JsObject object && object.has("$hole") && "$hole".equals(tagOf(object))) {
          continue;
        }
        out.set(i, decode(item, ctx, args));
      }
      return out;
    }
    if (!(raw instanceof JsObject object)) {
      return raw;
    }
    String tag = tagOf(object);
    if (tag == null) {
      JsObject out = new JsObject();
      for (String key : object.keys()) {
        out.put(key, decode(object.get(key), ctx, args));
      }
      return out;
    }
    Object value = object.get(tag);
    switch (tag) {
      case "$undefined":
        return Js.UNDEFINED;
      case "$number":
        if ("NaN".equals(value)) {
          return Double.NaN;
        }
        if ("Infinity".equals(value)) {
          return Double.POSITIVE_INFINITY;
        }
        if ("-Infinity".equals(value)) {
          return Double.NEGATIVE_INFINITY;
        }
        if ("-0".equals(value)) {
          return -0.0;
        }
        throw new RequestError("$number must be \"NaN\", \"Infinity\", \"-Infinity\" or \"-0\"");
      case "$date":
        if (value instanceof Double time) {
          return new JsDate(time);
        }
        if (value instanceof String text) {
          return JsDate.parse(text);
        }
        throw new RequestError("$date must be a number or a string");
      case "$regex": {
        if (!(value instanceof String pattern)) {
          throw new RequestError("$regex must be a string");
        }
        Object flags = object.get("flags") == Js.UNDEFINED ? "" : object.get("flags");
        if (!(flags instanceof String flagText)) {
          throw new RequestError("flags must be a string");
        }
        if (flagText.contains("u") || flagText.contains("v")) {
          throw new RequestError("the u and v flags are not supported");
        }
        return new JsRegExp(pattern, flagText);
      }
      case "$ref":
        return object.get("map") == Js.UNDEFINED ? Yup.ref(value, Js.UNDEFINED)
            : Yup.ref(value, JsObject.of("map", decode(object.get("map"), ctx, args)));
      case "$schema":
        return buildSchema(value, ctx, args);
      case "$def":
        if (!(value instanceof String name)) {
          throw new RequestError("$def must be a string");
        }
        return getDef(ctx, name);
      case "$fn":
        return Catalog.make(object, ctx, args);
      case "$object": {
        if (!(value instanceof JsObject members)) {
          throw new RequestError("$object must be an object");
        }
        JsObject out = new JsObject();
        for (String key : members.keys()) {
          out.put(key, decode(members.get(key), ctx, args));
        }
        return out;
      }
      case "$hole":
        throw new RequestError("$hole is only allowed as an array element");
      case "$symbol":
        if (!(value instanceof String description)) {
          throw new RequestError("$symbol must be a string");
        }
        return new JsSymbol(description);
      case "$bigint":
        if (!(value instanceof String digits) || !digits.matches("-?[0-9]+")) {
          throw new RequestError("$bigint must be a string of decimal digits");
        }
        return new BigInteger(digits);
      case "$error": {
        if (!(value instanceof String message)) {
          throw new RequestError("$error must be a string");
        }
        JsError error = JsError.error(message);
        Object name = object.get("name");
        if (name != Js.UNDEFINED) {
          if (!(name instanceof String)) {
            throw new RequestError("name must be a string");
          }
          error.setName(name);
        }
        return error;
      }
      case "$validationError": {
        if (!(value instanceof JsObject spec)) {
          throw new RequestError("$validationError must be an object");
        }
        return new ValidationError(decode(spec.get("errors"), ctx, args), decode(spec.get("value"), ctx, args),
            decode(spec.get("path"), ctx, args), decode(spec.get("type"), ctx, args),
            decode(spec.get("disableStack"), ctx, args));
      }
      case "$arg": {
        if (!(value instanceof Double index) || index < 0 || Math.floor(index) != index || index.isInfinite()) {
          throw new RequestError("$arg must be a non-negative integer");
        }
        if (args == null) {
          throw new RequestError("$arg is only allowed in the calls of a \"method\" function");
        }
        return index < args.size() ? args.get(index.intValue()) : Js.UNDEFINED;
      }
      default:
        throw new RequestError("unknown tag: " + quote(tag));
    }
  }

  // The tag of a tagged value, null for a plain object.
  private static String tagOf(JsObject raw) {
    String tag = null;
    for (String key : raw.keys()) {
      if (!key.startsWith("$")) {
        continue;
      }
      if (tag != null) {
        throw new RequestError("a tagged value must have exactly one member whose name starts with \"$\"");
      }
      tag = key;
    }
    return tag;
  }

  /**
   * Builds a schema expression: {@code [factory or [factory, ...args], ...calls]}.
   *
   * @param expr the expression
   * @param ctx the request
   * @param args the arguments of the enclosing {@code method} function, or null
   * @return the schema
   */
  static Object buildSchema(Object expr, Context ctx, List<Object> args) {
    if (!(expr instanceof JsArray list) || list.length() == 0) {
      throw new RequestError("a schema must be a non-empty array");
    }
    Object head = list.get(0);
    String name;
    List<Object> rawArgs = new ArrayList<>();
    if (head instanceof String text) {
      name = text;
    } else if (head instanceof JsArray headList && headList.get(0) instanceof String text) {
      name = text;
      for (int i = 1; i < headList.length(); i++) {
        rawArgs.add(headList.get(i));
      }
    } else {
      throw new RequestError(
          "a schema must start with a type name or with an array of a type name and its arguments");
    }
    if (!Methods.FACTORIES.contains(name)) {
      throw new RequestError("unknown schema type: " + quote(name));
    }
    List<Object> decoded = new ArrayList<>();
    for (Object raw : rawArgs) {
      decoded.add(decode(raw, ctx, args));
    }
    Object schema = Methods.create(name, decoded);
    JsArray calls = new JsArray();
    for (int i = 1; i < list.length(); i++) {
      calls.push(list.get(i));
    }
    return applyCalls(schema, calls, ctx, args);
  }

  /**
   * Applies method calls to a schema.
   *
   * @param schema the schema
   * @param calls an array of {@code [name, ...args]}
   * @param ctx the request
   * @param args the arguments of the enclosing {@code method} function, or null
   * @return the resulting schema
   */
  static Object applyCalls(Object schema, Object calls, Context ctx, List<Object> args) {
    if (!(calls instanceof JsArray list)) {
      throw new RequestError("method calls must be an array");
    }
    Object current = schema;
    for (Object call : list.toList()) {
      if (!(call instanceof JsArray callList) || !(callList.get(0) instanceof String name)) {
        throw new RequestError("a method call must be an array that starts with a method name");
      }
      if (!Methods.has((ISchema) current, name, ctx)) {
        throw new RequestError(Js.toJsString(((ISchema) current).type()) + " schema has no method " + quote(name));
      }
      List<Object> decoded = new ArrayList<>();
      for (int i = 1; i < callList.length(); i++) {
        decoded.add(decode(callList.get(i), ctx, args));
      }
      Object result = Methods.call((ISchema) current, name, decoded);
      if (!Yup.isSchema(result)) {
        throw new RequestError("method " + quote(name) + " did not return a schema");
      }
      current = result;
    }
    return current;
  }

  /**
   * The schema of a definition, built once per request.
   *
   * @param ctx the request
   * @param name the definition's name
   * @return the schema
   */
  static Object getDef(Context ctx, String name) {
    if (ctx.defs.containsKey(name)) {
      return ctx.defs.get(name);
    }
    if (ctx.defSpecs == Js.UNDEFINED || !((JsObject) ctx.defSpecs).has(name)) {
      throw new RequestError("unknown definition: " + quote(name));
    }
    if (ctx.building.contains(name)) {
      throw new RequestError("definition " + quote(name) + " refers to itself");
    }
    ctx.building.add(name);
    Object schema = buildSchema(((JsObject) ctx.defSpecs).get(name), ctx, null);
    ctx.building.remove(name);
    ctx.defs.put(name, schema);
    return schema;
  }

  /**
   * Requires a function.
   *
   * @param value a decoded value
   * @param key the parameter name, for the error
   * @return the function
   */
  static JsFunction function(Object value, String key) {
    if (!(value instanceof JsFunction function)) {
      throw new RequestError(key + " must be a function");
    }
    return function;
  }
}
