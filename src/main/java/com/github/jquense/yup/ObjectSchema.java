/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsArray;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsonParser;
import com.github.jquense.yup.js.UnicodeCase;
import com.github.jquense.yup.util.PropertyExpr;
import com.github.jquense.yup.util.TinyCase;
import com.github.jquense.yup.util.Toposort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * {@code object(shape)}: plain objects whose fields are validated by their schemas, in dependency order; unknown keys
 * are kept, stripped or rejected.
 */
public class ObjectSchema extends Schema {
  private JsObject fields = new JsObject(true);
  private Comparator<Object> sortErrors = sortByKeyOrder(List.of());
  private List<String> nodes = new ArrayList<>();
  private List<Object> excludedEdges = new ArrayList<>();

  /**
   * {@code object(shape)}.
   *
   * @param shape an object of field schemas and references, or {@code undefined}
   */
  public ObjectSchema(Object shape) {
    super("object", null);
    withMutation(s -> {
      if (Js.truthy(shape)) {
        ((ObjectSchema) s).shape(shape, new JsArray());
      }
      return null;
    });
  }

  /** {@code object()}. */
  public ObjectSchema() {
    this(Js.UNDEFINED);
  }

  private ObjectSchema(boolean blank) {}

  @Override
  protected Schema blank() {
    return new ObjectSchema(true);
  }

  @Override
  protected void copyInto(Schema next) {
    ObjectSchema copy = (ObjectSchema) next;
    JsObject fieldsCopy = new JsObject();
    Js.spread(fieldsCopy, fields);
    copy.fields = fieldsCopy;
    copy.nodes = nodes;
    copy.excludedEdges = excludedEdges;
    copy.sortErrors = sortErrors;
  }

  @Override
  public Prototype prototype() {
    return Prototype.OBJECT_SCHEMA;
  }

  @Override
  protected Object typeCheck(Object value) {
    return isObject(value) || value instanceof JsFunction;
  }

  private static boolean isObject(Object value) {
    return Js.toStringTag(value).equals("Object");
  }

  /** @return the fields */
  public JsObject fields() {
    return fields;
  }

  /** @return the field names in dependency order */
  public List<String> nodes() {
    return nodes;
  }

  @Override
  protected Object castInternal(Object rawValue, Object options) {
    Object opts = options == Js.UNDEFINED ? new JsObject() : options;
    Object value = super.castInternal(rawValue, opts);
    if (value == Js.UNDEFINED) {
      return getDefault(opts);
    }
    if (!Js.truthy(typeCheck(value))) {
      return value;
    }
    Object stripOption = Js.get(opts, "stripUnknown");
    Object strip = Js.isNullish(stripOption) ? spec.get("noUnknown") : stripOption;
    List<String> props = new ArrayList<>(nodes);
    for (String key : Js.keys(value)) {
      if (!nodes.contains(key)) {
        props.add(key);
      }
    }
    JsObject intermediateValue = new JsObject();
    JsObject innerOptions = new JsObject();
    Js.spread(innerOptions, opts);
    innerOptions.put("parent", intermediateValue);
    Object validating = Js.get(opts, "__validating");
    innerOptions.put("__validating", Js.truthy(validating) ? validating : false);
    boolean isChanged = false;
    for (String prop : props) {
      Object field = fields.get(prop);
      boolean exists = Js.in(prop, value);
      if (Js.truthy(field)) {
        Object inputValue = Js.get(value, prop);
        Object path = Js.get(opts, "path");
        innerOptions.put("path", (Js.truthy(path) ? Js.toJsString(path) + "." : "") + prop);
        SchemaLike resolved = asSchemaLike(field).resolve(JsObject.of(
            "value", inputValue, "context", Js.get(opts, "context"), "parent", intermediateValue));
        JsObject fieldSpec = resolved instanceof Schema schema ? schema.spec() : null;
        Object strict = fieldSpec == null ? Js.UNDEFINED : fieldSpec.get("strict");
        if (fieldSpec != null && Js.truthy(fieldSpec.get("strip"))) {
          isChanged = isChanged || Js.in(prop, value);
          continue;
        }
        Object fieldValue = !Js.truthy(Js.get(opts, "__validating")) || !Js.truthy(strict)
            ? resolved.cast(Js.get(value, prop), innerOptions)
            : Js.get(value, prop);
        if (fieldValue != Js.UNDEFINED) {
          intermediateValue.put(prop, fieldValue);
        }
      } else if (exists && !Js.truthy(strip)) {
        intermediateValue.put(prop, Js.get(value, prop));
      }
      if (exists != intermediateValue.has(prop)
          || !Js.strictEquals(intermediateValue.get(prop), Js.get(value, prop))) {
        isChanged = true;
      }
    }
    return isChanged ? intermediateValue : value;
  }

  private static SchemaLike asSchemaLike(Object field) {
    if (!(field instanceof SchemaLike schemaLike)) {
      throw JsError.typeError("field.resolve is not a function");
    }
    return schemaLike;
  }

  @Override
  protected void validateInternal(Object value, Object options, RunTest.Callback panic, RunTest.Callback next) {
    Object opts = options == Js.UNDEFINED ? new JsObject() : options;
    Object from = Js.get(opts, "from");
    Object originalValue = Js.get(opts, "originalValue");
    if (originalValue == Js.UNDEFINED) {
      originalValue = value;
    }
    Object recursive = Js.get(opts, "recursive");
    if (recursive == Js.UNDEFINED) {
      recursive = spec.get("recursive");
    }
    JsArray ancestors = new JsArray();
    ancestors.push(JsObject.of("schema", this, "value", originalValue));
    if (from != Js.UNDEFINED) {
      for (Object ancestor : ((JsArray) from).toList()) {
        ancestors.push(ancestor);
      }
    }
    JsObject mutable = (JsObject) opts;
    mutable.put("from", ancestors);
    mutable.put("__validating", true);
    mutable.put("originalValue", originalValue);
    Object recursiveFlag = recursive;
    Object[] original = {originalValue};
    super.validateInternal(value, opts, panic, (objectErrors, castValue) -> {
      if (!Js.truthy(recursiveFlag) || !isObject(castValue)) {
        next.call(objectErrors, castValue);
        return;
      }
      if (!Js.truthy(original[0])) {
        original[0] = castValue;
      }
      List<RunTest> tests = new ArrayList<>();
      for (String key : nodes) {
        Object field = fields.get(key);
        if (!Js.truthy(field) || field instanceof Reference) {
          continue;
        }
        tests.add(((ISchema) field).asNestedTest(new NestedTestConfig(opts, castValue, original[0],
            Js.get(opts, "path"), key, Js.UNDEFINED)));
      }
      runTests(tests, castValue, original[0], Js.UNDEFINED, opts, panic, (fieldErrors, unused) -> {
        List<Object> sorted = new ArrayList<>((List<?>) fieldErrors);
        sorted.sort(sortErrors);
        sorted.addAll((List<?>) objectErrors);
        next.call(sorted, castValue);
      });
    });
  }

  @Override
  public ObjectSchema concat(Object other) {
    Schema concatenated = super.concat(other);
    if (Js.isNullish(other)) {
      throw JsError.typeError("Cannot read properties of " + (other == null ? "null" : "undefined")
          + " (reading '_excludedEdges')");
    }
    ObjectSchema next = (ObjectSchema) concatenated;
    JsObject nextFields = next.fields;
    for (String field : fields.keys()) {
      Object target = nextFields.get(field);
      nextFields.put(field, target == Js.UNDEFINED ? fields.get(field) : target);
    }
    List<Object> edges = new ArrayList<>(excludedEdges);
    edges.addAll(((ObjectSchema) other).excludedEdges);
    return next.withMutation(s -> ((ObjectSchema) s).setFields(nextFields, edges));
  }

  @Override
  protected Object getDefaultInternal(Object options) {
    if (spec.has("default")) {
      return super.getDefaultInternal(options);
    }
    if (nodes.isEmpty()) {
      return Js.UNDEFINED;
    }
    JsObject defaults = new JsObject();
    for (String key : nodes) {
      Object field = fields.get(key);
      Object innerOptions = options;
      Object optionValue = Js.get(innerOptions, "value");
      if (Js.truthy(optionValue)) {
        JsObject copy = new JsObject();
        Js.spread(copy, innerOptions);
        copy.put("parent", optionValue);
        copy.put("value", Js.get(optionValue, key));
        innerOptions = copy;
      }
      defaults.put(key, field instanceof Schema schema ? schema.getDefault(innerOptions) : Js.UNDEFINED);
    }
    return defaults;
  }

  private ObjectSchema setFields(JsObject shape, List<Object> edges) {
    ObjectSchema next = (ObjectSchema) copy();
    next.fields = shape;
    next.nodes = sortFields(shape, edges == null ? List.of() : edges);
    next.sortErrors = sortByKeyOrder(shape.keys());
    if (edges != null) {
      next.excludedEdges = edges;
    }
    return next;
  }

  /**
   * {@code shape(additions, excludes)}: adds fields; {@code excludes} lists pairs of fields whose dependency is ignored
   * (a single pair may be given on its own).
   *
   * @param additions an object of field schemas and references
   * @param excludes an array of {@code [a, b]} pairs, or one pair
   * @return the new schema
   */
  public ObjectSchema shape(Object additions, Object excludes) {
    Object excludeList = excludes == Js.UNDEFINED ? new JsArray() : excludes;
    return copy().withMutation(s -> {
      ObjectSchema next = (ObjectSchema) s;
      List<Object> edges = next.excludedEdges;
      if (Js.truthy(Js.get(excludeList, "length"))) {
        List<Object> pairs = new ArrayList<>();
        if (!(Js.get(excludeList, "0") instanceof JsArray)) {
          pairs.add(excludeList);
        } else {
          pairs.addAll(((JsArray) excludeList).toList());
        }
        edges = new ArrayList<>(next.excludedEdges);
        edges.addAll(pairs);
      }
      Js.spread(next.fields, additions);
      return next.setFields(next.fields, edges);
    });
  }

  /** @return {@code partial()}: every field optional */
  public ObjectSchema partial() {
    JsObject partial = new JsObject();
    for (String key : fields.keys()) {
      Object schema = fields.get(key);
      partial.put(key, schema instanceof Schema s ? s.optional() : schema instanceof Lazy l ? l.optional() : schema);
    }
    return setFields(partial, null);
  }

  /** @return {@code deepPartial()}: every field optional, through nested objects, arrays and tuples */
  public ObjectSchema deepPartial() {
    return (ObjectSchema) deepPartial(this);
  }

  private static Object deepPartial(Object schema) {
    if (schema instanceof ObjectSchema object) {
      JsObject partial = new JsObject();
      for (String key : object.fields.keys()) {
        partial.put(key, deepPartial(object.fields.get(key)));
      }
      return object.setFields(partial, null);
    }
    if (schema instanceof Schema s && Js.strictEquals(s.type, "array")) {
      Schema nextArray = s.optional();
      if (nextArray instanceof ArraySchema array && Js.truthy(array.innerType())) {
        array.setInnerType(deepPartial(array.innerType()));
      }
      return nextArray;
    }
    if (schema instanceof Schema s && Js.strictEquals(s.type, "tuple")) {
      JsArray types = new JsArray();
      for (Object type : ((JsArray) s.spec.get("types")).toList()) {
        types.push(deepPartial(type));
      }
      return s.optional().copy(JsObject.of("types", types));
    }
    if (schema instanceof Schema s) {
      return s.optional();
    }
    if (schema instanceof Lazy lazy) {
      return lazy.optional();
    }
    return schema;
  }

  /**
   * {@code pick(keys)}: only the given fields.
   *
   * @param keys the field names
   * @return the new schema
   */
  public ObjectSchema pick(Object keys) {
    List<Object> keyList = iterate(keys);
    JsObject picked = new JsObject();
    for (Object key : keyList) {
      String name = Js.toJsString(key);
      if (Js.truthy(fields.get(name))) {
        picked.put(name, fields.get(name));
      }
    }
    List<Object> edges = new ArrayList<>();
    for (Object edge : excludedEdges) {
      List<Object> pair = destructure(edge);
      if (containsSameValueZero(keyList, pair.get(0)) && containsSameValueZero(keyList, pair.get(1))) {
        edges.add(edge);
      }
    }
    return setFields(picked, edges);
  }

  /**
   * {@code omit(keys)}: every field but the given ones.
   *
   * @param keys the field names
   * @return the new schema
   */
  public ObjectSchema omit(Object keys) {
    List<Object> keyList = iterate(keys);
    JsArray remaining = new JsArray();
    for (String key : fields.keys()) {
      if (!containsSameValueZero(keyList, key)) {
        remaining.push(key);
      }
    }
    return pick(remaining);
  }

  private static boolean containsSameValueZero(List<Object> list, Object value) {
    for (Object item : list) {
      if (Js.sameValueZero(item, value)) {
        return true;
      }
    }
    return false;
  }

  private static List<Object> iterate(Object iterable) {
    if (iterable instanceof JsArray array) {
      return array.toList();
    }
    if (iterable instanceof String s) {
      List<Object> chars = new ArrayList<>();
      s.codePoints().forEach(cp -> chars.add(new String(Character.toChars(cp))));
      return chars;
    }
    throw JsError.typeError("keys is not iterable");
  }

  // [a, b] = edge: the first two elements of an array, or the first two code points of a string.
  private static List<Object> destructure(Object edge) {
    List<Object> items = new ArrayList<>(iterate(edge));
    while (items.size() < 2) {
      items.add(Js.UNDEFINED);
    }
    return items;
  }

  /**
   * {@code from(from, to, alias)}: moves (or copies, with {@code alias}) the value at a path to a key.
   *
   * @param from the source path
   * @param to the target key
   * @param alias whether the source is kept
   * @return the new schema
   */
  public ObjectSchema from(Object from, Object to, Object alias) {
    String fromPath = Js.toJsString(from);
    Function<Object, Object> fromGetter = PropertyExpr.getter(fromPath, true);
    String toKey = Js.toJsString(to);
    return (ObjectSchema) transform((JsFunction) (self, args) -> {
      Object obj = args[0];
      if (!Js.truthy(obj)) {
        return obj;
      }
      Object newObj = obj;
      if (deepHas(obj, fromPath)) {
        JsObject copy = new JsObject();
        Js.spread(copy, obj);
        if (!Js.truthy(alias)) {
          copy.delete(fromPath);
        }
        copy.put(toKey, fromGetter.apply(obj));
        newObj = copy;
      }
      return newObj;
    });
  }

  private static boolean deepHas(Object obj, String p) {
    List<String> path = new ArrayList<>(PropertyExpr.normalizePath(p));
    if (path.size() == 1) {
      return Js.in(path.get(0), obj);
    }
    String last = path.remove(path.size() - 1);
    Object parent = PropertyExpr.getter(PropertyExpr.join(path), true).apply(obj);
    return Js.truthy(parent) && Js.in(last, parent);
  }

  /** @return {@code json()}: parses JSON strings into objects */
  public ObjectSchema json() {
    return (ObjectSchema) transform(parseJson());
  }

  /**
   * The {@code parseJson} transform: a string becomes what it parses to when that has the schema's type.
   *
   * @return the transform
   */
  static JsFunction parseJson() {
    return (self, args) -> {
      Object value = args[0];
      if (!(value instanceof String text)) {
        return value;
      }
      Object parsed = value;
      try {
        parsed = JsonParser.parse(text);
      } catch (JsError e) {
        parsed = value;
      }
      return Js.truthy(((Schema) args[2]).isType(parsed)) ? parsed : value;
    };
  }

  private static List<String> unknown(ObjectSchema schema, Object value) {
    List<String> known = schema.fields.keys();
    List<String> unknownKeys = new ArrayList<>();
    for (String key : Js.keys(value)) {
      if (!known.contains(key)) {
        unknownKeys.add(key);
      }
    }
    return unknownKeys;
  }

  /**
   * {@code exact(message)}: rejects unknown keys without stripping them.
   *
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public ObjectSchema exact(Object message) {
    return (ObjectSchema) test(JsObject.of(
        "name", "exact",
        "exclusive", true,
        "message", Js.truthy(message) ? message : Locale.message("object", "exact"),
        "test", (JsFunction) (self, args) -> {
          Object value = JsFunction.arg(args, 0);
          if (Js.isNullish(value)) {
            return true;
          }
          TestContext ctx = (TestContext) self;
          List<String> unknownKeys = unknown((ObjectSchema) ctx.schema(), value);
          if (unknownKeys.isEmpty()) {
            return true;
          }
          return ctx.createError(JsObject.of("params", JsObject.of("properties", String.join(", ", unknownKeys))));
        }));
  }

  /** @return {@code stripUnknown()}: unknown keys are removed when casting */
  public ObjectSchema stripUnknown() {
    return (ObjectSchema) copy(JsObject.of("noUnknown", true));
  }

  /**
   * {@code noUnknown(noAllow = true, message)}: rejects (and strips) unknown keys; a non-boolean first argument is the
   * message.
   *
   * @param noAllow whether unknown keys are rejected, or the message
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public ObjectSchema noUnknown(Object noAllow, Object message) {
    Object allowFlag = noAllow == Js.UNDEFINED ? true : noAllow;
    Object msg = message == Js.UNDEFINED ? Locale.message("object", "noUnknown") : message;
    if (!(allowFlag instanceof Boolean)) {
      msg = allowFlag;
      allowFlag = true;
    }
    boolean flag = (Boolean) allowFlag;
    ObjectSchema next = (ObjectSchema) test(JsObject.of(
        "name", "noUnknown",
        "exclusive", true,
        "message", msg,
        "test", (JsFunction) (self, args) -> {
          Object value = JsFunction.arg(args, 0);
          if (Js.isNullish(value)) {
            return true;
          }
          TestContext ctx = (TestContext) self;
          List<String> unknownKeys = unknown((ObjectSchema) ctx.schema(), value);
          if (!flag || unknownKeys.isEmpty()) {
            return true;
          }
          return ctx.createError(JsObject.of("params", JsObject.of("unknown", String.join(", ", unknownKeys))));
        }));
    next.spec.put("noUnknown", flag);
    return next;
  }

  /**
   * {@code unknown(allow = true, message)}: {@code noUnknown(!allow, message)}.
   *
   * @param allow whether unknown keys are allowed
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public ObjectSchema unknown(Object allow, Object message) {
    Object allowed = allow == Js.UNDEFINED ? true : allow;
    return noUnknown(!Js.truthy(allowed), message == Js.UNDEFINED ? Locale.message("object", "noUnknown") : message);
  }

  /**
   * {@code transformKeys(fn)}: renames every key.
   *
   * @param fn the key mapping
   * @return the new schema
   */
  public ObjectSchema transformKeys(Object fn) {
    return transformKeys(key -> {
      if (!(fn instanceof JsFunction function)) {
        throw JsError.typeError("fn is not a function");
      }
      return Js.toJsString(function.call(Js.UNDEFINED, key));
    });
  }

  private ObjectSchema transformKeys(UnaryOperator<String> fn) {
    return (ObjectSchema) transform((JsFunction) (self, args) -> {
      Object obj = args[0];
      if (!Js.truthy(obj)) {
        return obj;
      }
      JsObject result = new JsObject();
      for (String key : Js.keys(obj)) {
        result.put(fn.apply(key), Js.get(obj, key));
      }
      return result;
    });
  }

  /** @return {@code camelCase()}: keys in camel case */
  public ObjectSchema camelCase() {
    return transformKeys(TinyCase::camelCase);
  }

  /** @return {@code snakeCase()}: keys in snake case */
  public ObjectSchema snakeCase() {
    return transformKeys(TinyCase::snakeCase);
  }

  /** @return {@code constantCase()}: keys in upper snake case */
  public ObjectSchema constantCase() {
    return transformKeys(key -> UnicodeCase.toUpperCase(TinyCase.snakeCase(key)));
  }

  @Override
  public Object describe(Object options) {
    ObjectSchema next = (ObjectSchema) (Js.truthy(options) ? resolve(options) : this).copy();
    JsObject base = (JsObject) super.describe(options);
    JsObject described = new JsObject();
    for (String key : next.fields.keys()) {
      Object innerOptions = options;
      Object optionValue = Js.get(innerOptions, "value");
      if (Js.truthy(optionValue)) {
        JsObject copy = new JsObject();
        Js.spread(copy, innerOptions);
        copy.put("parent", optionValue);
        copy.put("value", Js.get(optionValue, key));
        innerOptions = copy;
      }
      described.put(key, asSchemaLike(next.fields.get(key)).describe(innerOptions));
    }
    base.put("fields", described);
    return base;
  }

  // sortFields: the field names in reverse topological order of their dependencies.
  private static List<String> sortFields(JsObject shape, List<Object> excludedEdges) {
    List<String[]> edges = new ArrayList<>();
    Set<String> nodeSet = new LinkedHashSet<>();
    Set<String> excludes = new LinkedHashSet<>();
    for (Object edge : excludedEdges) {
      List<Object> pair = destructure(edge);
      excludes.add(Js.toJsString(pair.get(0)) + "-" + Js.toJsString(pair.get(1)));
    }
    for (String key : shape.keys()) {
      Object value = shape.get(key);
      nodeSet.add(key);
      List<String> depPaths = new ArrayList<>();
      if (value instanceof Reference ref && ref.isSibling()) {
        depPaths.add(ref.path());
      } else if (value instanceof Schema schema) {
        depPaths.addAll(schema.deps());
      }
      for (String depPath : depPaths) {
        String node = PropertyExpr.split(depPath).get(0);
        nodeSet.add(node);
        if (!excludes.contains(key + "-" + node)) {
          edges.add(new String[] {key, node});
        }
      }
    }
    List<String> sorted = new ArrayList<>(Toposort.array(new ArrayList<>(nodeSet), edges));
    java.util.Collections.reverse(sorted);
    return sorted;
  }

  // sortByKeyOrder: errors ordered by the first key their path contains.
  private static Comparator<Object> sortByKeyOrder(List<String> keys) {
    return Comparator.comparingDouble(error -> {
      Object path = Js.get(error, "path");
      for (int i = 0; i < keys.size(); i++) {
        if (Js.isNullish(path)) {
          break;
        }
        if (!(path instanceof String text)) {
          throw JsError.typeError("err.path?.includes is not a function");
        }
        if (text.contains(keys.get(i))) {
          return i;
        }
      }
      return Double.POSITIVE_INFINITY;
    });
  }
}
