/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.cli;

import com.github.jquense.yup.ISchema;
import com.github.jquense.yup.Lazy;
import com.github.jquense.yup.Locale;
import com.github.jquense.yup.Reach;
import com.github.jquense.yup.Reference;
import com.github.jquense.yup.Schema;
import com.github.jquense.yup.SchemaLike;
import com.github.jquense.yup.StandardSchema;
import com.github.jquense.yup.ValidationError;
import com.github.jquense.yup.Yup;
import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsException;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsPromise;
import com.github.jquense.yup.js.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Carries out one request line and builds its response. */
final class Requests {
  private static final Set<String> OPS = Set.of("cast", "validate", "validateSync", "validateAt", "validateSyncAt",
      "isValid", "isValidSync", "isType", "describe", "resolve", "getDefault", "default", "meta", "reach", "getIn",
      "printValue", "isSchema", "validationError", "formatError", "isError", "standardValidate", "locale", "ref");

  private static final List<String> OPTION_KEYS = List.of("strict", "abortEarly", "stripUnknown", "recursive",
      "disableStackTrace", "context", "assert", "path", "parent", "value");

  private static final Map<String, Yup.Constructor> SCHEMA_TYPES = Map.ofEntries(
      Map.entry("mixed", Yup.Constructor.MIXED),
      Map.entry("string", Yup.Constructor.STRING),
      Map.entry("number", Yup.Constructor.NUMBER),
      Map.entry("boolean", Yup.Constructor.BOOLEAN),
      Map.entry("bool", Yup.Constructor.BOOLEAN),
      Map.entry("date", Yup.Constructor.DATE),
      Map.entry("object", Yup.Constructor.OBJECT),
      Map.entry("array", Yup.Constructor.ARRAY),
      Map.entry("tuple", Yup.Constructor.TUPLE),
      Map.entry("lazy", Yup.Constructor.LAZY),
      Map.entry("ref", Yup.Constructor.REF),
      Map.entry("Schema", Yup.Constructor.SCHEMA),
      Map.entry("MixedSchema", Yup.Constructor.MIXED),
      Map.entry("StringSchema", Yup.Constructor.STRING),
      Map.entry("NumberSchema", Yup.Constructor.NUMBER),
      Map.entry("BooleanSchema", Yup.Constructor.BOOLEAN),
      Map.entry("DateSchema", Yup.Constructor.DATE),
      Map.entry("ObjectSchema", Yup.Constructor.OBJECT),
      Map.entry("ArraySchema", Yup.Constructor.ARRAY),
      Map.entry("TupleSchema", Yup.Constructor.TUPLE),
      Map.entry("LazySchema", Yup.Constructor.LAZY_SCHEMA));

  private Requests() {}

  /**
   * The response to one request line.
   *
   * @param line the line
   * @return the response object
   */
  static JsObject respond(String line) {
    Object parsed = JsonParser.parse(line);
    if (!(parsed instanceof JsObject request)) {
      throw new RequestError("a request must be a JSON object");
    }
    Object op = request.get("op");
    if (!(op instanceof String name) || !OPS.contains(name)) {
      throw new RequestError("unknown op: " + Decoder.quote(op));
    }
    Context ctx = Context.of(request);
    try {
      if (request.get("locale") != Js.UNDEFINED) {
        Locale.setLocale(Decoder.decode(request.get("locale"), ctx, null));
      }
      if (request.get("methods") != Js.UNDEFINED) {
        installMethods(request.get("methods"), ctx);
      }
      return handle(name, request, ctx);
    } finally {
      JsPromise.drain();
      ctx.uninstall();
      Locale.reset();
    }
  }

  private static void installMethods(Object raw, Context ctx) {
    if (!(raw instanceof com.github.jquense.yup.js.JsArray entries)) {
      throw new RequestError("methods must be an array");
    }
    for (Object item : entries.toList()) {
      if (!(item instanceof JsObject entry)) {
        throw new RequestError("a method must be an object");
      }
      Object rawType = entry.get("type");
      Object schemaType = rawType instanceof String text && SCHEMA_TYPES.containsKey(text)
          ? SCHEMA_TYPES.get(text)
          : Decoder.decode(rawType, ctx, null);
      Object name = Decoder.decode(entry.get("name"), ctx, null);
      Object fn = Decoder.decode(entry.get("fn"), ctx, null);
      if (schemaType instanceof Yup.Constructor constructor && constructor.prototype().isSchema()
          && name instanceof String methodName && fn instanceof JsFunction && constructor.prototype().has(methodName)) {
        throw new RequestError("addMethod would replace the existing member " + Decoder.quote(methodName));
      }
      Yup.addMethod(schemaType, name, fn);
      Yup.Constructor constructor = (Yup.Constructor) schemaType;
      ctx.installed.add(new Context.Installed(constructor.prototype(), (String) name));
      ctx.added.add((String) name);
    }
  }

  private static Object buildOptions(Object raw, Context ctx) {
    if (raw == Js.UNDEFINED) {
      return Js.UNDEFINED;
    }
    if (!(raw instanceof JsObject members)) {
      throw new RequestError("options must be an object");
    }
    JsObject options = new JsObject();
    for (String key : OPTION_KEYS) {
      if (members.has(key)) {
        options.put(key, Decoder.decode(members.get(key), ctx, null));
      }
    }
    return options;
  }

  private static String pathMember(JsObject request) {
    if (!(request.get("path") instanceof String path)) {
      throw new RequestError("path must be a string");
    }
    return path;
  }

  private static ISchema schema(JsObject request, Context ctx) {
    return (ISchema) Decoder.buildSchema(request.get("schema"), ctx, null);
  }

  private static Object decode(JsObject request, String key, Context ctx) {
    return Decoder.decode(request.get(key), ctx, null);
  }

  private static void requireMethod(ISchema schema, String name) {
    if (!schema.prototype().hasBuiltin(name)) {
      throw new RequestError(Js.toJsString(schema.type()) + " schema has no method " + Decoder.quote(name));
    }
  }

  // {value: E(await result)}, or {invalid: EV(error)} for a ValidationError; anything else is thrown on. The result of
  // the synchronous operations is awaited too, so a thenable value is followed.
  private static JsObject validation(Supplier<Object> run) {
    try {
      Object result = run.get();
      if (JsPromise.isThenable(result)) {
        result = JsPromise.resolved(result).await();
      }
      return JsObject.of("value", Encoder.encode(result));
    } catch (RuntimeException e) {
      Object error = JsException.valueOf(e);
      if (ValidationError.isError(error)) {
        return JsObject.of("invalid", Encoder.encodeError(error));
      }
      throw e;
    }
  }

  private static JsObject handle(String op, JsObject request, Context ctx) {
    switch (op) {
      case "cast": {
        ISchema schema = schema(request, ctx);
        Object value = decode(request, "value", ctx);
        Object options = buildOptions(request.get("options"), ctx);
        return JsObject.of("value", Encoder.encode(schema.cast(value, options)));
      }
      case "validate":
      case "validateSync":
      case "isValid":
      case "isValidSync": {
        ISchema schema = schema(request, ctx);
        Object value = decode(request, "value", ctx);
        Object options = buildOptions(request.get("options"), ctx);
        return switch (op) {
          case "validate" -> validation(() -> schema.validate(value, options));
          case "validateSync" -> validation(() -> schema.validateSync(value, options));
          case "isValid" -> JsObject.of("result", Encoder.encode(schema.isValid(value, options).await()));
          default -> JsObject.of("result", schema.isValidSync(value, options));
        };
      }
      case "validateAt":
      case "validateSyncAt": {
        ISchema schema = schema(request, ctx);
        String path = pathMember(request);
        Object value = decode(request, "value", ctx);
        Object options = buildOptions(request.get("options"), ctx);
        return op.equals("validateAt")
            ? validation(() -> schema.validateAt(path, value, options))
            : validation(() -> schema.validateSyncAt(path, value, options));
      }
      case "isType": {
        ISchema schema = schema(request, ctx);
        Object value = decode(request, "value", ctx);
        requireMethod(schema, "isType");
        return JsObject.of("result", Encoder.encode(((Schema) schema).isType(value)));
      }
      case "describe": {
        ISchema schema = schema(request, ctx);
        Object options = buildOptions(request.get("options"), ctx);
        return JsObject.of("description", Encoder.encode(schema.describe(options)));
      }
      case "resolve": {
        ISchema schema = schema(request, ctx);
        Object options = buildOptions(request.get("options"), ctx);
        SchemaLike resolved = schema.resolve(options == Js.UNDEFINED ? new JsObject() : options);
        return JsObject.of("description", Encoder.encode(resolved.describe(Js.UNDEFINED)));
      }
      case "getDefault": {
        ISchema schema = schema(request, ctx);
        Object options = buildOptions(request.get("options"), ctx);
        requireMethod(schema, "getDefault");
        return JsObject.of("value", Encoder.encode(((Schema) schema).getDefault(options)));
      }
      case "default": {
        ISchema schema = schema(request, ctx);
        requireMethod(schema, "default");
        return JsObject.of("value", Encoder.encode(((Schema) schema).defaultValue()));
      }
      case "meta": {
        ISchema schema = schema(request, ctx);
        Object meta = schema instanceof Lazy lazy ? lazy.meta() : ((Schema) schema).meta();
        return JsObject.of("value", Encoder.encode(meta));
      }
      case "reach":
      case "getIn": {
        ISchema schema = schema(request, ctx);
        String path = pathMember(request);
        Object value = decode(request, "value", ctx);
        Object context = decode(request, "context", ctx);
        Reach.Result result = Reach.getIn(schema, path, value, context);
        if (!(result.schema() instanceof SchemaLike found)) {
          throw com.github.jquense.yup.js.JsError.typeError("Cannot read properties of "
              + (result.schema() == null ? "null" : "undefined") + " (reading 'describe')");
        }
        Object description = found.describe(Js.UNDEFINED);
        if (op.equals("reach")) {
          return JsObject.of("description", Encoder.encode(description));
        }
        return JsObject.of("parentPath", Encoder.encode(result.parentPath()), "parent", Encoder.encode(result.parent()),
            "description", Encoder.encode(description));
      }
      case "printValue": {
        Object value = decode(request, "value", ctx);
        Object quoteStrings = decode(request, "quoteStrings", ctx);
        return JsObject.of("result", Encoder.encode(Yup.printValue(value, quoteStrings)));
      }
      case "isSchema":
        return JsObject.of("result", Encoder.encode(Yup.isSchemaValue(decode(request, "value", ctx))));
      case "validationError": {
        Object errors = decode(request, "errors", ctx);
        Object value = decode(request, "value", ctx);
        Object path = decode(request, "path", ctx);
        Object type = decode(request, "type", ctx);
        Object disableStack = decode(request, "disableStack", ctx);
        return JsObject.of("error", Encoder.encodeError(new ValidationError(errors, value, path, type, disableStack)));
      }
      case "formatError": {
        Object message = decode(request, "message", ctx);
        Object params = decode(request, "params", ctx);
        return JsObject.of("result", Encoder.encode(ValidationError.formatError(message, params)));
      }
      case "isError":
        return JsObject.of("result", Encoder.encode(ValidationError.isErrorValue(decode(request, "value", ctx))));
      case "standardValidate": {
        ISchema schema = schema(request, ctx);
        Object value = decode(request, "value", ctx);
        JsObject standard = schema.standard();
        Object result = StandardSchema.validate(schema, value).await();
        return JsObject.of("vendor", standard.get("vendor"), "version", standard.get("version"), "result",
            Encoder.encode(result));
      }
      case "locale":
        return JsObject.of("locale", Encoder.encode(Locale.defaultLocale()));
      default: {
        Object key = decode(request, "key", ctx);
        Object options = request.get("map") == Js.UNDEFINED ? Js.UNDEFINED
            : JsObject.of("map", decode(request, "map", ctx));
        Object value = decode(request, "value", ctx);
        Object parent = decode(request, "parent", ctx);
        Object context = decode(request, "context", ctx);
        Reference reference = Yup.ref(key, options);
        return JsObject.of(
            "key", Encoder.encode(reference.key()),
            "isContext", Encoder.encode(reference.isContext()),
            "isValue", Encoder.encode(reference.isValue()),
            "isSibling", Encoder.encode(reference.isSibling()),
            "path", Encoder.encode(reference.path()),
            "string", Encoder.encode(reference.jsToString()),
            "description", Encoder.encode(reference.describe()),
            "value", Encoder.encode(reference.getValue(value, parent, context)));
      }
    }
  }
}
