/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.cli;

import com.github.jquense.yup.TestContext;
import com.github.jquense.yup.ValidationError;
import com.github.jquense.yup.Yup;
import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsArray;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsPromise;
import com.github.jquense.yup.js.JsRegExp;
import com.github.jquense.yup.js.UnicodeCase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/** The catalog of named functions that stand in for the user-written functions of yup's API. */
final class Catalog {
  private static final Map<String, BiPredicate<Object, Object>> COMPARE = Map.of(
      "<", Js::lt,
      "<=", Js::le,
      ">", Js::gt,
      ">=", Js::ge,
      "==", Js::looseEquals,
      "!=", (a, b) -> !Js.looseEquals(a, b),
      "===", Js::strictEquals,
      "!==", (a, b) -> !Js.strictEquals(a, b));

  private static final List<String> ERRORS = List.of("Error", "TypeError", "RangeError", "SyntaxError",
      "ValidationError");

  private Catalog() {}

  /**
   * Makes the function a {@code {"$fn": NAME, ...}} value names.
   *
   * @param spec the tagged value
   * @param ctx the request
   * @param args the arguments of the enclosing {@code method} function, or null
   * @return the function
   */
  static Object make(JsObject spec, Context ctx, List<Object> args) {
    if (!(spec.get("$fn") instanceof String name)) {
      throw new RequestError("$fn must be a string");
    }
    if (name.equals("use")) {
      return function(ctx, stringParam(spec, "name"));
    }
    JsFunction body = body(name, spec, ctx, args);
    return JsFunction.named(name, body);
  }

  private static JsFunction function(Context ctx, String name) {
    if (ctx.functions.containsKey(name)) {
      return (JsFunction) ctx.functions.get(name);
    }
    if (ctx.functionSpecs == Js.UNDEFINED || !((JsObject) ctx.functionSpecs).has(name)) {
      throw new RequestError("unknown function name: " + Decoder.quote(name));
    }
    if (ctx.making.contains(name)) {
      throw new RequestError("function " + Decoder.quote(name) + " refers to itself");
    }
    ctx.making.add(name);
    Object fn = Decoder.decode(((JsObject) ctx.functionSpecs).get(name), ctx, null);
    ctx.making.remove(name);
    if (!(fn instanceof JsFunction function)) {
      throw new RequestError("function " + Decoder.quote(name) + " is not a function");
    }
    ctx.functions.put(name, function);
    return function;
  }

  private static String stringParam(JsObject spec, String key) {
    if (!(spec.get(key) instanceof String value)) {
      throw new RequestError(key + " must be a string");
    }
    return value;
  }

  private static int intParam(JsObject spec, String key) {
    Object value = spec.get(key);
    if (!(value instanceof Double d) || Math.floor(d) != d || d.isInfinite() || d < 0 || d > 1000) {
      throw new RequestError(key + " must be an integer from 0 to 1000");
    }
    return d.intValue();
  }

  private static List<String> stringsParam(JsObject spec, String key) {
    if (!(spec.get(key) instanceof JsArray array)) {
      throw new RequestError(key + " must be an array of strings");
    }
    List<String> strings = new ArrayList<>();
    for (Object item : array.toList()) {
      if (!(item instanceof String s)) {
        throw new RequestError(key + " must be an array of strings");
      }
      strings.add(s);
    }
    return strings;
  }

  private static JsArray callsParam(JsObject spec, String key) {
    if (!(spec.get(key) instanceof JsArray calls)) {
      throw new RequestError(key + " must be an array of method calls");
    }
    return calls;
  }

  private static List<JsFunction> fnsParam(JsObject spec, Context ctx, List<Object> args) {
    String message = "fns must be an array of functions";
    if (!(spec.get("fns") instanceof JsArray raw)) {
      throw new RequestError(message);
    }
    List<Object> decoded = new ArrayList<>();
    for (Object item : raw.toList()) {
      decoded.add(Decoder.decode(item, ctx, args));
    }
    List<JsFunction> fns = new ArrayList<>();
    for (Object f : decoded) {
      if (!(f instanceof JsFunction function)) {
        throw new RequestError(message);
      }
      fns.add(function);
    }
    return fns;
  }

  private static List<JsArray> casesParam(JsObject spec) {
    String message = "cases must be an array of [value, result] pairs";
    if (!(spec.get("cases") instanceof JsArray cases)) {
      throw new RequestError(message);
    }
    List<JsArray> pairs = new ArrayList<>();
    for (Object item : cases.toList()) {
      if (!(item instanceof JsArray pair) || pair.length() != 2) {
        throw new RequestError(message);
      }
      pairs.add(pair);
    }
    return pairs;
  }

  private static BiPredicate<Object, Object> compareParam(JsObject spec) {
    Object op = spec.get("op");
    BiPredicate<Object, Object> compare = op instanceof String text ? COMPARE.get(text) : null;
    if (compare == null) {
      throw new RequestError("op must be one of < <= > >= == != === !==");
    }
    return compare;
  }

  private static TestContext testContext(Object self, String name) {
    if (!(self instanceof TestContext ctx)) {
      throw new RequestError(name + " needs a test context");
    }
    return ctx;
  }

  private static void requireSchema(Object value, String name) {
    if (!Yup.isSchema(value)) {
      throw new RequestError(name + " needs a schema");
    }
  }

  // Own member `key` of an object or array, undefined for anything else.
  private static Object ownMember(Object value, String key) {
    if (value instanceof JsObject object) {
      return object.has(key) ? object.get(key) : Js.UNDEFINED;
    }
    if (value instanceof JsArray || value instanceof com.github.jquense.yup.js.JsProperties) {
      return Js.get(value, key);
    }
    return Js.UNDEFINED;
  }

  private static Object arg(Object[] args, int index) {
    return JsFunction.arg(args, index);
  }

  private static JsFunction body(String name, JsObject spec, Context ctx, List<Object> args) {
    switch (name) {
      case "const": {
        Object value = Decoder.decode(spec.get("value"), ctx, args);
        return (self, a) -> value;
      }
      case "truthy":
        return (self, a) -> Js.truthy(arg(a, 0));
      case "not": {
        JsFunction inner = Decoder.function(Decoder.decode(spec.get("fn"), ctx, args), "fn");
        return (self, a) -> !Js.truthy(inner.call(self, a));
      }
      case "equals": {
        Object expected = Decoder.decode(spec.get("value"), ctx, args);
        return (self, a) -> Js.strictEquals(arg(a, 0), expected);
      }
      case "typeof": {
        String type = stringParam(spec, "type");
        return (self, a) -> Js.typeOf(arg(a, 0)).equals(type);
      }
      case "isArray":
        return (self, a) -> arg(a, 0) instanceof JsArray;
      case "and": {
        List<JsFunction> fns = fnsParam(spec, ctx, args);
        return (self, a) -> {
          for (JsFunction f : fns) {
            if (!Js.truthy(f.call(self, a))) {
              return false;
            }
          }
          return true;
        };
      }
      case "or": {
        List<JsFunction> fns = fnsParam(spec, ctx, args);
        return (self, a) -> {
          for (JsFunction f : fns) {
            if (Js.truthy(f.call(self, a))) {
              return true;
            }
          }
          return false;
        };
      }
      case "argAt": {
        int index = intParam(spec, "index");
        JsFunction inner = Decoder.function(Decoder.decode(spec.get("fn"), ctx, args), "fn");
        return (self, a) -> inner.call(self, arg(a, index));
      }
      case "matches": {
        Object regex = Decoder.decode(spec.get("regex"), ctx, args);
        if (!(regex instanceof JsRegExp expression)) {
          throw new RequestError("regex must be a RegExp");
        }
        return (self, a) -> expression.test(arg(a, 0));
      }
      case "compare": {
        BiPredicate<Object, Object> op = compareParam(spec);
        Object to = Decoder.decode(spec.get("to"), ctx, args);
        return (self, a) -> op.test(arg(a, 0), to);
      }
      case "compareRef": {
        BiPredicate<Object, Object> op = compareParam(spec);
        Object reference = Yup.ref(stringParam(spec, "ref"));
        return (self, a) -> op.test(arg(a, 0), testContext(self, "compareRef").resolve(reference));
      }
      case "createError": {
        JsObject overrides = new JsObject();
        for (String key : List.of("path", "message", "params", "type", "disableStackTrace")) {
          if (spec.has(key)) {
            overrides.put(key, Decoder.decode(spec.get(key), ctx, args));
          }
        }
        return (self, a) -> testContext(self, "createError").createError(overrides);
      }
      case "inspect":
        return (self, a) -> {
          TestContext test = testContext(self, "inspect");
          Object from = test.from();
          Object ancestors = from;
          if (Js.truthy(from)) {
            JsArray values = new JsArray();
            for (Object ancestor : ((JsArray) from).toList()) {
              values.push(Js.get(ancestor, "value"));
            }
            ancestors = values;
          }
          JsObject params = JsObject.of(
              "ctxPath", test.path(),
              "ctxParent", test.parent(),
              "ctxOriginalValue", test.originalValue(),
              "ctxType", test.type(),
              "ctxFrom", ancestors,
              "ctxSchema", test.schema().type(),
              "ctxContext", Js.get(test.options(), "context"),
              "ctxIndex", Js.get(test.options(), "index"),
              "ctxKey", Js.get(test.options(), "key"));
          return test.createError(JsObject.of("params", params));
        };
      case "throw": {
        String message = stringParam(spec, "message");
        Object errorName = spec.get("name");
        if (errorName != Js.UNDEFINED && !(errorName instanceof String text && ERRORS.contains(text))) {
          throw new RequestError("name must be Error, TypeError, RangeError, SyntaxError or ValidationError");
        }
        String type = errorName == Js.UNDEFINED ? "Error" : (String) errorName;
        return (self, a) -> {
          if (type.equals("ValidationError")) {
            throw new ValidationError(message, Js.UNDEFINED, Js.UNDEFINED, Js.UNDEFINED, Js.UNDEFINED);
          }
          throw new JsError(type, message);
        };
      }
      case "async": {
        int ticks = intParam(spec, "ticks");
        JsFunction inner = Decoder.function(Decoder.decode(spec.get("fn"), ctx, args), "fn");
        return (self, a) -> {
          JsPromise promise = JsPromise.resolved(Js.UNDEFINED);
          for (int i = 0; i < ticks; i++) {
            promise = promise.then(v -> Js.UNDEFINED, null);
          }
          return promise.then(v -> inner.call(self, a), null);
        };
      }
      case "pick": {
        List<String> keys = stringsParam(spec, "keys");
        return (self, a) -> {
          Object params = arg(a, 0);
          JsObject out = new JsObject();
          for (String key : keys) {
            out.put(key, Js.isNullish(params) ? Js.UNDEFINED : Js.get(params, key));
          }
          return out;
        };
      }
      case "join": {
        List<String> keys = stringsParam(spec, "keys");
        String separator = spec.get("separator") == Js.UNDEFINED ? "," : stringParam(spec, "separator");
        return (self, a) -> {
          Object params = arg(a, 0);
          List<String> parts = new ArrayList<>();
          for (String key : keys) {
            parts.add(Js.string(Js.isNullish(params) ? Js.UNDEFINED : Js.get(params, key)));
          }
          return String.join(separator, parts);
        };
      }
      case "chain": {
        JsArray calls = callsParam(spec, "calls");
        return (self, a) -> {
          Object schema = arg(a, 0);
          requireSchema(schema, "chain");
          return Decoder.applyCalls(schema, calls, ctx, args);
        };
      }
      case "when":
        return when(spec, ctx, args);
      case "select":
        return select(spec, ctx, args);
      case "byType": {
        if (!(spec.get("cases") instanceof JsObject rawCases)) {
          throw new RequestError("cases must be an object");
        }
        Map<String, Object> cases = new LinkedHashMap<>();
        for (String key : rawCases.keys()) {
          cases.put(key, Decoder.decode(rawCases.get(key), ctx, args));
        }
        Object otherwise = Decoder.decode(spec.get("otherwise"), ctx, args);
        return (self, a) -> {
          Object value = arg(a, 0);
          String type = value == null ? "null" : value instanceof JsArray ? "array" : Js.typeOf(value);
          return cases.containsKey(type) ? cases.get(type) : otherwise;
        };
      }
      case "schema": {
        Object schema = Decoder.buildSchema(spec.get("schema"), ctx, args);
        return (self, a) -> schema;
      }
      case "def": {
        String defName = stringParam(spec, "name");
        if (ctx.defSpecs == Js.UNDEFINED || !((JsObject) ctx.defSpecs).has(defName)) {
          throw new RequestError("unknown definition: " + Decoder.quote(defName));
        }
        JsArray calls = spec.get("calls") == Js.UNDEFINED ? new JsArray() : callsParam(spec, "calls");
        return (self, a) -> Decoder.applyCalls(Decoder.getDef(ctx, defName), calls, ctx, args);
      }
      case "contextKey": {
        String key = stringParam(spec, "key");
        return (self, a) -> {
          Object options = arg(a, 0);
          return ownMember(Js.isNullish(options) ? Js.UNDEFINED : Js.get(options, "context"), key);
        };
      }
      case "original":
        return (self, a) -> arg(a, 1);
      case "toNumber":
        return (self, a) -> {
          Object numeric = Js.toNumeric(arg(a, 0));
          return numeric instanceof java.math.BigInteger big ? big.doubleValue() : numeric;
        };
      case "toString":
        return (self, a) -> Js.string(arg(a, 0));
      case "double":
        return (self, a) -> Js.multiply(arg(a, 0), 2.0);
      case "nullToUndefined":
        return (self, a) -> arg(a, 0) == null ? Js.UNDEFINED : arg(a, 0);
      case "emptyToNull":
        return (self, a) -> "".equals(arg(a, 0)) ? null : arg(a, 0);
      case "wrap":
        return (self, a) -> JsArray.of(arg(a, 0));
      case "split": {
        String separator = stringParam(spec, "separator");
        return (self, a) -> arg(a, 0) instanceof String text ? split(text, separator) : arg(a, 0);
      }
      case "merge": {
        Object props = Decoder.decode(spec.get("props"), ctx, args);
        if (!Decoder.isObject(props)) {
          throw new RequestError("props must be an object");
        }
        return (self, a) -> {
          Object value = arg(a, 0);
          if (!Js.toStringTag(value).equals("Object") || !(Js.isObject(value))) {
            return value;
          }
          JsObject merged = new JsObject();
          Js.spread(merged, value);
          Js.spread(merged, props);
          return merged;
        };
      }
      case "length":
        return (self, a) -> {
          Object value = arg(a, 0);
          return value instanceof String || value instanceof JsArray ? Js.get(value, "length") : Js.UNDEFINED;
        };
      case "upper":
        return (self, a) -> arg(a, 0) instanceof String text ? UnicodeCase.toUpperCase(text) : arg(a, 0);
      case "lower":
        return (self, a) -> arg(a, 0) instanceof String text ? UnicodeCase.toLowerCase(text) : arg(a, 0);
      case "prefix": {
        String prefix = stringParam(spec, "prefix");
        return (self, a) -> Js.add(prefix, arg(a, 0));
      }
      case "evenIndex":
        return (self, a) -> Js.toNumber(arg(a, 1)) % 2 == 0;
      case "method": {
        JsArray calls = callsParam(spec, "calls");
        return (self, a) -> {
          requireSchema(self, "method");
          return Decoder.applyCalls(self, calls, ctx, Arrays.asList(a));
        };
      }
      default:
        throw new RequestError("unknown function: " + Decoder.quote(name));
    }
  }

  private static JsFunction when(JsObject spec, Context ctx, List<Object> args) {
    int index = spec.get("index") == Js.UNDEFINED ? 0 : intParam(spec, "index");
    List<Object[]> cases = new ArrayList<>();
    for (JsArray pair : casesParam(spec)) {
      cases.add(new Object[] {Decoder.decode(pair.get(0), ctx, args), pair.get(1)});
    }
    for (Object[] pair : cases) {
      if (!(pair[1] instanceof JsArray)) {
        throw new RequestError("cases must be an array of [value, result] pairs");
      }
    }
    JsArray otherwise = spec.get("otherwise") == Js.UNDEFINED ? null : callsParam(spec, "otherwise");
    return (self, a) -> {
      Object values = arg(a, 0);
      Object schema = arg(a, 1);
      requireSchema(schema, "when");
      Object value = values instanceof JsArray array ? array.get(index) : Js.UNDEFINED;
      for (Object[] pair : cases) {
        if (Js.strictEquals(value, pair[0])) {
          return Decoder.applyCalls(schema, pair[1], ctx, args);
        }
      }
      return otherwise == null ? Js.UNDEFINED : Decoder.applyCalls(schema, otherwise, ctx, args);
    };
  }

  private static JsFunction select(JsObject spec, Context ctx, List<Object> args) {
    int argIndex = spec.get("arg") == Js.UNDEFINED ? 0 : intParam(spec, "arg");
    List<String> path = spec.get("path") == Js.UNDEFINED ? List.of()
        : List.of(stringParam(spec, "path").split("\\.", -1));
    List<Object[]> cases = new ArrayList<>();
    for (JsArray pair : casesParam(spec)) {
      cases.add(new Object[] {Decoder.decode(pair.get(0), ctx, args), Decoder.decode(pair.get(1), ctx, args)});
    }
    Object otherwise = Decoder.decode(spec.get("otherwise"), ctx, args);
    return (self, a) -> {
      Object value = arg(a, argIndex);
      for (String part : path) {
        value = ownMember(value, part);
      }
      for (Object[] pair : cases) {
        if (Js.strictEquals(value, pair[0])) {
          return pair[1];
        }
      }
      return otherwise;
    };
  }

  // String.prototype.split with a string separator.
  private static JsArray split(String text, String separator) {
    JsArray parts = new JsArray();
    if (separator.isEmpty()) {
      for (int i = 0; i < text.length(); i++) {
        parts.push(String.valueOf(text.charAt(i)));
      }
      return parts;
    }
    int start = 0;
    while (true) {
      int at = text.indexOf(separator, start);
      if (at < 0) {
        parts.push(text.substring(start));
        return parts;
      }
      parts.push(text.substring(start, at));
      start = at + separator.length();
    }
  }
}
