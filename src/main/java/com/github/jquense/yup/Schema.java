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
import com.github.jquense.yup.js.JsException;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsPromise;
import com.github.jquense.yup.js.JsProperties;
import com.github.jquense.yup.js.JsSymbol;
import com.github.jquense.yup.util.CloneDeep;
import com.github.jquense.yup.util.PrintValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The base of every schema: casting (transforms and defaults), validation (internal tests, then tests, with
 * {@code abortEarly}), conditions, allowed and forbidden values, and description. Schemas are immutable: every builder
 * method returns a new schema.
 *
 * <p>Arguments and results are JavaScript values (see {@link Js}); {@link Js#UNDEFINED} stands for an argument that
 * was not passed, so that JavaScript default parameters apply.
 */
public abstract class Schema implements ISchema, JsProperties {
  /** The schema type ({@code string}, {@code number}, ...), any value for a custom mixed schema. */
  protected Object type;
  /** The sibling keys the conditions depend on. */
  protected List<String> deps = new ArrayList<>();
  /** The tests, in order. */
  protected List<Test> tests = new ArrayList<>();
  /** The transforms, in order. */
  protected List<JsFunction> transforms = new ArrayList<>();
  private List<Condition> conditions = new ArrayList<>();
  private boolean mutate;
  private Map<String, Test> internalTests = new LinkedHashMap<>();
  /** The {@code oneOf} values. */
  protected com.github.jquense.yup.util.ReferenceSet whitelist = new com.github.jquense.yup.util.ReferenceSet();
  /** The {@code notOneOf} values. */
  protected com.github.jquense.yup.util.ReferenceSet blacklist = new com.github.jquense.yup.util.ReferenceSet();
  private Map<Object, Boolean> exclusiveTests = new HashMap<>();
  /** The spec: flags, label, meta, default and the subclass's own entries. */
  protected JsObject spec;

  /**
   * Initialises a new schema as yup's {@code Schema} constructor does.
   *
   * @param type the type
   * @param extraSpec entries added to the default spec, or null
   */
  protected Schema(Object type, JsObject extraSpec) {
    withMutation(s -> s.typeError(Locale.message("mixed", "notType")));
    this.type = type;
    spec = JsObject.of("strip", false, "strict", false, "abortEarly", true, "recursive", true, "disableStackTrace",
        false, "nullable", false, "optional", true, "coerce", true);
    if (extraSpec != null) {
      Js.spread(spec, extraSpec);
    }
    withMutation(s -> s.nonNullable(Js.UNDEFINED));
  }

  /** For {@link #blank()}: a schema whose fields {@link #copy} fills in. */
  protected Schema() {}

  /** @return an uninitialised instance of the same class, for {@link #copy} */
  protected abstract Schema blank();

  /**
   * The type check of the schema (its {@code _typeCheck}).
   *
   * @param value a non-null value
   * @return whether it has the schema's type (a JavaScript value; truthiness counts)
   */
  protected abstract Object typeCheck(Object value);

  @Override
  public Object type() {
    return type;
  }

  @Override
  public JsObject spec() {
    return spec;
  }

  @Override
  public Prototype prototype() {
    return Prototype.SCHEMA;
  }

  /** @return the sibling keys the conditions depend on */
  public List<String> deps() {
    return deps;
  }

  /** @return the tests */
  public List<Test> tests() {
    return tests;
  }

  /**
   * {@code clone(spec)}: a copy with the spec entries overridden (deep-copied); while mutating, the schema itself.
   *
   * @param specOverride spec entries to set, or {@code undefined}
   * @return the copy
   */
  public Schema copy(Object specOverride) {
    if (mutate) {
      if (Js.truthy(specOverride)) {
        Js.spread(spec, specOverride);
      }
      return this;
    }
    Schema next = blank();
    next.type = type;
    next.whitelist = whitelist.copy();
    next.blacklist = blacklist.copy();
    next.internalTests = new LinkedHashMap<>(internalTests);
    next.exclusiveTests = new HashMap<>(exclusiveTests);
    next.deps = new ArrayList<>(deps);
    next.conditions = new ArrayList<>(conditions);
    next.tests = new ArrayList<>(tests);
    next.transforms = new ArrayList<>(transforms);
    JsObject merged = spec.copy();
    Js.spread(merged, specOverride);
    next.spec = (JsObject) CloneDeep.clone(merged);
    copyInto(next);
    return next;
  }

  /** @return a copy ({@code clone()}) */
  public Schema copy() {
    return copy(Js.UNDEFINED);
  }

  /**
   * Copies the subclass's own state into a fresh copy (or onto itself while mutating).
   *
   * @param next the copy
   */
  protected void copyInto(Schema next) {}

  /**
   * {@code withMutation(fn)}: runs {@code fn} with copies disabled, so builder calls change this schema.
   *
   * @param fn the function
   * @param <T> its result type
   * @return its result
   */
  public <T> T withMutation(Function<Schema, T> fn) {
    boolean before = mutate;
    mutate = true;
    T result = fn.apply(this);
    mutate = before;
    return result;
  }

  /**
   * {@code label(label)}.
   *
   * @param label the label used in messages
   * @return the new schema
   */
  public Schema label(Object label) {
    Schema next = copy();
    next.spec.put("label", label);
    return next;
  }

  /** @return {@code meta()}: the metadata */
  public Object meta() {
    return spec.get("meta");
  }

  /**
   * {@code meta(obj)}: merges metadata.
   *
   * @param obj the metadata to merge
   * @return the new schema
   */
  public Schema meta(Object obj) {
    Schema next = copy();
    Object current = next.spec.get("meta");
    JsObject target = current instanceof JsObject existing && Js.truthy(current) ? existing : new JsObject();
    if (!(current instanceof JsObject) && Js.truthy(current)) {
      Js.spread(target, current);
    }
    Js.spread(target, obj);
    next.spec.put("meta", target);
    return next;
  }

  /**
   * {@code concat(schema)}: combines two schemas of the same type (or any schema into a mixed one); the other schema's
   * spec, tests and values win.
   *
   * @param other the other schema
   * @return the combined schema
   */
  public Schema concat(Object other) {
    if (!Js.truthy(other) || other == this) {
      return this;
    }
    Object otherType = Js.get(other, "type");
    if (!Js.strictEquals(otherType, type) && !Js.strictEquals(type, "mixed")) {
      throw JsError.typeError("You cannot `concat()` schema's of different types: " + Js.toJsString(type) + " and "
          + Js.toJsString(otherType));
    }
    Schema schema = (Schema) other;
    Schema base = this;
    Schema combined = schema.copy();
    JsObject mergedSpec = base.spec.copy();
    Js.spread(mergedSpec, combined.spec);
    combined.spec = mergedSpec;
    Map<String, Test> mergedInternal = new LinkedHashMap<>(base.internalTests);
    mergedInternal.putAll(combined.internalTests);
    combined.internalTests = mergedInternal;
    combined.whitelist = base.whitelist.merge(schema.whitelist, schema.blacklist);
    combined.blacklist = base.blacklist.merge(schema.blacklist, schema.whitelist);
    combined.tests = base.tests;
    combined.exclusiveTests = base.exclusiveTests;
    combined.withMutation(next -> {
      for (Test test : schema.tests) {
        next.test(test.options());
      }
      return null;
    });
    List<JsFunction> mergedTransforms = new ArrayList<>(base.transforms);
    mergedTransforms.addAll(combined.transforms);
    combined.transforms = mergedTransforms;
    return combined;
  }

  /**
   * {@code isType(value)}: {@code null} and {@code undefined} pass when nullable and optional, other values go to the
   * type check.
   *
   * @param value any value
   * @return the result (a JavaScript value)
   */
  public Object isType(Object value) {
    if (Js.isNullish(value)) {
      if (Js.truthy(spec.get("nullable")) && value == null) {
        return true;
      }
      if (Js.truthy(spec.get("optional")) && value == Js.UNDEFINED) {
        return true;
      }
      return false;
    }
    return typeCheck(value);
  }

  @Override
  public Schema resolve(Object options) {
    Schema schema = this;
    if (!schema.conditions.isEmpty()) {
      List<Condition> pending = schema.conditions;
      schema = schema.copy();
      schema.conditions = new ArrayList<>();
      SchemaLike current = schema;
      for (Condition condition : pending) {
        current = condition.resolve(current, options);
      }
      return (Schema) current.resolve(options);
    }
    return schema;
  }

  @Override
  public Object cast(Object value, Object options) {
    Object opts = options == Js.UNDEFINED ? new JsObject() : options;
    JsObject resolveOptions = JsObject.of("value", value);
    Js.spread(resolveOptions, opts);
    Schema resolved = resolve(resolveOptions);
    Object assertOption = Js.read(opts, "assert");
    boolean allowOptionality = "ignore-optionality".equals(assertOption);
    Object result = resolved.castInternal(value, opts);
    if (!Boolean.FALSE.equals(assertOption) && !Js.truthy(resolved.isType(result))) {
      if (allowOptionality && Js.isNullish(result)) {
        return result;
      }
      String formattedValue = PrintValue.print(value, false);
      String formattedResult = PrintValue.print(result, false);
      Object path = Js.get(opts, "path");
      throw JsError.typeError("The value of " + Js.toJsString(Js.truthy(path) ? path : "field")
          + " could not be cast to a value that satisfies the schema type: \"" + Js.toJsString(resolved.type)
          + "\". \n\nattempted value: " + formattedValue + " \n"
          + (!formattedResult.equals(formattedValue) ? "result of cast: " + formattedResult : ""));
    }
    return result;
  }

  /**
   * {@code _cast}: runs the transforms (not for {@code undefined}), then falls back to the default.
   *
   * @param rawValue the input
   * @param options the cast options
   * @return the cast value
   */
  protected Object castInternal(Object rawValue, Object options) {
    Object value = rawValue;
    if (rawValue != Js.UNDEFINED) {
      for (JsFunction transform : transforms) {
        value = transform.call(this, value, rawValue, this);
      }
    }
    if (value == Js.UNDEFINED) {
      value = getDefault(options);
    }
    return value;
  }

  /**
   * {@code _validate}: casts unless strict, runs the internal tests, then (when they pass) the tests.
   *
   * @param value the input
   * @param options the validation options (mutable; a fresh object for {@code undefined})
   * @param panic stops the run
   * @param next receives the errors and the value
   */
  protected void validateInternal(Object value, Object options, RunTest.Callback panic, RunTest.Callback next) {
    Object opts = options == Js.UNDEFINED ? new JsObject() : options;
    Object path = Js.get(opts, "path");
    Object originalValue = Js.get(opts, "originalValue");
    if (originalValue == Js.UNDEFINED) {
      originalValue = value;
    }
    Object strict = Js.get(opts, "strict");
    if (strict == Js.UNDEFINED) {
      strict = spec.get("strict");
    }
    Object castValue = value;
    if (!Js.truthy(strict)) {
      JsObject castOptions = JsObject.of("assert", false);
      Js.spread(castOptions, opts);
      castValue = castInternal(value, castOptions);
    }
    List<RunTest> initialTests = new ArrayList<>();
    for (Test test : internalTests.values()) {
      if (test != null) {
        initialTests.add(test);
      }
    }
    Object finalValue = castValue;
    Object original = originalValue;
    runTests(initialTests, finalValue, original, path, opts, panic, (initialErrors, unused) -> {
      if (!((List<?>) initialErrors).isEmpty()) {
        next.call(initialErrors, finalValue);
        return;
      }
      runTests(new ArrayList<>(tests), finalValue, original, path, opts, panic, next);
    });
  }

  /**
   * {@code runTests}: runs every test; the first panic, or the collected errors once every test finished, goes on.
   *
   * @param runList the tests
   * @param value the value
   * @param originalValue the original value
   * @param path the path
   * @param options the options
   * @param panic receives the first panic
   * @param next receives the list of errors once all tests finished
   */
  protected void runTests(List<? extends RunTest> runList, Object value, Object originalValue, Object path,
      Object options, RunTest.Callback panic, RunTest.Callback next) {
    boolean[] fired = {false};
    RunTest.Callback panicOnce = (error, unused) -> {
      if (fired[0]) {
        return;
      }
      fired[0] = true;
      panic.call(error, value);
    };
    RunTest.Callback nextOnce = (errors, unused) -> {
      if (fired[0]) {
        return;
      }
      fired[0] = true;
      next.call(errors, value);
    };
    int[] count = {runList.size()};
    List<Object> nestedErrors = new ArrayList<>();
    if (count[0] == 0) {
      nextOnce.call(nestedErrors, Js.UNDEFINED);
      return;
    }
    RunTest.Args args = new RunTest.Args(value, originalValue, path, options, this);
    for (RunTest test : runList) {
      test.run(args, panicOnce, (err, unused) -> {
        if (Js.truthy(err)) {
          if (err instanceof List<?> list) {
            nestedErrors.addAll(list);
          } else {
            nestedErrors.add(err);
          }
        }
        if (--count[0] <= 0) {
          nextOnce.call(nestedErrors, Js.UNDEFINED);
        }
      });
    }
  }

  @Override
  public RunTest asNestedTest(NestedTestConfig config) {
    Object k = config.key() != Js.UNDEFINED && config.key() != null ? config.key() : config.index();
    if (Js.isNullish(k)) {
      throw JsError.typeError("Must include `key` or `index` for nested validations");
    }
    boolean isIndex = k instanceof Double;
    String keyText = Js.toJsString(k);
    Object value = Js.read(config.parent(), keyText);
    JsObject testOptions = new JsObject();
    Js.spread(testOptions, config.options());
    testOptions.put("strict", true);
    testOptions.put("parent", config.parent());
    testOptions.put("value", value);
    testOptions.put("originalValue", Js.read(config.originalParent(), keyText));
    testOptions.put("key", Js.UNDEFINED);
    testOptions.put(isIndex ? "index" : "key", k);
    Object parentPath = config.parentPath();
    String path;
    if (isIndex || keyText.contains(".")) {
      path = (Js.truthy(parentPath) ? Js.toJsString(parentPath) : "") + "["
          + (isIndex ? keyText : "\"" + keyText + "\"") + "]";
    } else {
      path = (Js.truthy(parentPath) ? Js.toJsString(parentPath) + "." : "") + Js.toJsString(config.key());
    }
    testOptions.put("path", path);
    return (args, panic, next) -> resolve(testOptions).validateInternal(value, testOptions, panic, next);
  }

  @Override
  public JsPromise validate(Object value, Object options) {
    JsObject resolveOptions = new JsObject();
    Js.spread(resolveOptions, options);
    resolveOptions.put("value", value);
    Schema schema = resolve(resolveOptions);
    Object disableStackTrace = Js.get(options, "disableStackTrace");
    Object stack = Js.isNullish(disableStackTrace) ? schema.spec.get("disableStackTrace") : disableStackTrace;
    return JsPromise.create((resolveFn, rejectFn) -> schema.validateInternal(value, options, (error, parsed) -> {
      if (ValidationError.isError(error)) {
        ValidationError.assignValue(error, parsed);
      }
      rejectFn.apply(error);
    }, (errors, validated) -> {
      List<?> list = (List<?>) errors;
      if (!list.isEmpty()) {
        rejectFn.apply(new ValidationError(new JsArray(list), validated, Js.UNDEFINED, Js.UNDEFINED, stack));
      } else {
        resolveFn.apply(validated);
      }
    }));
  }

  @Override
  public Object validateSync(Object value, Object options) {
    JsObject resolveOptions = new JsObject();
    Js.spread(resolveOptions, options);
    resolveOptions.put("value", value);
    Schema schema = resolve(resolveOptions);
    Object disableStackTrace = Js.get(options, "disableStackTrace");
    Object stack = Js.isNullish(disableStackTrace) ? schema.spec.get("disableStackTrace") : disableStackTrace;
    JsObject syncOptions = new JsObject();
    Js.spread(syncOptions, options);
    syncOptions.put("sync", true);
    Object[] result = {Js.UNDEFINED};
    schema.validateInternal(value, syncOptions, (error, parsed) -> {
      if (ValidationError.isError(error)) {
        ValidationError.assignValue(error, parsed);
      }
      throw JsException.of(error);
    }, (errors, validated) -> {
      List<?> list = (List<?>) errors;
      if (!list.isEmpty()) {
        throw new ValidationError(new JsArray(list), value, Js.UNDEFINED, Js.UNDEFINED, stack);
      }
      result[0] = validated;
    });
    return result[0];
  }

  @Override
  public JsPromise isValid(Object value, Object options) {
    return validate(value, options).then(valid -> true, err -> {
      if (ValidationError.isError(err)) {
        return false;
      }
      throw JsException.of(err);
    });
  }

  @Override
  public boolean isValidSync(Object value, Object options) {
    try {
      validateSync(value, options);
      return true;
    } catch (RuntimeException e) {
      if (ValidationError.isError(JsException.valueOf(e))) {
        return false;
      }
      throw e;
    }
  }

  @Override
  public JsPromise validateAt(String path, Object value, Object options) {
    At at = at(path, value, options);
    return ((ISchema) at.schema()).validate(at.value(), at.options());
  }

  @Override
  public Object validateSyncAt(String path, Object value, Object options) {
    At at = at(path, value, options);
    return ((ISchema) at.schema()).validateSync(at.value(), at.options());
  }

  /** The schema, value and options {@code validateAt} hands on. */
  private record At(Object schema, Object value, Object options) {}

  private At at(String path, Object value, Object options) {
    Object opts = options == Js.UNDEFINED ? new JsObject() : options;
    Reach.Result found = Reach.getIn(this, path, value, Js.get(opts, "context"));
    Object parent = found.parent();
    Object target = Js.truthy(parent) ? Js.get(parent, Js.toJsString(found.parentPath())) : parent;
    JsObject nested = new JsObject();
    Js.spread(nested, opts);
    nested.put("parent", parent);
    nested.put("path", path);
    if (!(found.schema() instanceof ISchema)) {
      throw JsError.typeError("schema[method] is not a function");
    }
    return new At(found.schema(), target, nested);
  }

  /**
   * {@code _getDefault}: the spec's default, called when it is a function, deep-copied otherwise.
   *
   * @param options the options passed to a default function
   * @return the default value
   */
  protected Object getDefaultInternal(Object options) {
    Object defaultValue = spec.get("default");
    if (Js.isNullish(defaultValue)) {
      return defaultValue;
    }
    return defaultValue instanceof JsFunction function ? function.call(this, options) : CloneDeep.clone(defaultValue);
  }

  /**
   * {@code getDefault(options)}: the default of the resolved schema.
   *
   * @param options resolve options, or {@code undefined}
   * @return the default value
   */
  public Object getDefault(Object options) {
    Schema schema = resolve(Js.truthy(options) ? options : new JsObject());
    return schema.getDefaultInternal(options);
  }

  /** @return {@code getDefault()} without options */
  public Object getDefault() {
    return getDefault(Js.UNDEFINED);
  }

  /** @return {@code default()} without arguments: this schema's own default value, without resolving it */
  public Object defaultValue() {
    return getDefaultInternal(Js.UNDEFINED);
  }

  /**
   * {@code default(value)}: the value (or function producing it) used when the input is {@code undefined}.
   *
   * @param def the default
   * @return the new schema
   */
  public Schema defaultValue(Object def) {
    return copy(JsObject.of("default", Js.fromJava(def)));
  }

  /**
   * {@code strict(isStrict = true)}.
   *
   * @param isStrict whether casting is skipped during validation; {@code undefined} means true
   * @return the new schema
   */
  public Schema strict(Object isStrict) {
    return copy(JsObject.of("strict", isStrict == Js.UNDEFINED ? true : isStrict));
  }

  private Schema nullability(boolean nullable, Object message) {
    Schema next = copy(JsObject.of("nullable", nullable));
    next.internalTests.put("nullable", new Test(JsObject.of(
        "message", message,
        "name", "nullable",
        "test", (JsFunction) (self, args) -> {
          Object value = JsFunction.arg(args, 0);
          return value == null ? ((TestContext) self).schema().spec.get("nullable") : true;
        })));
    return next;
  }

  private Schema optionality(boolean optional, Object message) {
    Schema next = copy(JsObject.of("optional", optional));
    next.internalTests.put("optionality", new Test(JsObject.of(
        "message", message,
        "name", "optionality",
        "test", (JsFunction) (self, args) -> {
          Object value = JsFunction.arg(args, 0);
          return value == Js.UNDEFINED ? ((TestContext) self).schema().spec.get("optional") : true;
        })));
    return next;
  }

  /** @return {@code optional()}: {@code undefined} is allowed */
  public Schema optional() {
    return optionality(true, Js.UNDEFINED);
  }

  /**
   * {@code defined(message)}: {@code undefined} is not allowed.
   *
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public Schema defined(Object message) {
    return optionality(false, message == Js.UNDEFINED ? Locale.message("mixed", "defined") : message);
  }

  /** @return {@code nullable()}: {@code null} is allowed */
  public Schema nullable() {
    return nullability(true, Js.UNDEFINED);
  }

  /**
   * {@code nonNullable(message)}: {@code null} is not allowed.
   *
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public Schema nonNullable(Object message) {
    return nullability(false, message == Js.UNDEFINED ? Locale.message("mixed", "notNull") : message);
  }

  /**
   * {@code required(message)}: neither {@code undefined} nor {@code null}.
   *
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public Schema required(Object message) {
    Object msg = message == Js.UNDEFINED ? Locale.message("mixed", "required") : message;
    return copy().withMutation(next -> next.nonNullable(msg).defined(msg));
  }

  /** @return {@code notRequired()}: nullable and optional */
  public Schema notRequired() {
    return copy().withMutation(next -> next.nullable().optional());
  }

  /**
   * {@code transform(fn)}: adds a transform {@code (value, originalValue, schema) => value}.
   *
   * @param fn the transform
   * @return the new schema
   */
  public Schema transform(Object fn) {
    Schema next = copy();
    next.transforms.add(fn instanceof JsFunction function ? function : (self, args) -> {
      throw JsError.typeError("fn.call is not a function");
    });
    return next;
  }

  /**
   * {@code test(...)}: adds a test, given as {@code (config)}, {@code (fn)}, {@code (name, fn)} or
   * {@code (name, message, fn)}. An exclusive test replaces the tests of the same name; a non-exclusive one replaces
   * only the same function.
   *
   * @param args the arguments
   * @return the new schema
   */
  public Schema test(Object... args) {
    JsObject opts = testConfig(args);
    if (opts.get("message") == Js.UNDEFINED) {
      opts.put("message", Locale.message("mixed", "default"));
    }
    if (!(opts.get("test") instanceof JsFunction)) {
      throw JsError.typeError("`test` is a required parameters");
    }
    Schema next = copy();
    Test validate = new Test(opts);
    Object name = opts.get("name");
    Object exclusive = opts.get("exclusive");
    boolean isExclusive = Js.truthy(exclusive)
        || (Js.truthy(name) && Boolean.TRUE.equals(next.exclusiveTests.get(propertyKey(name))));
    if (Js.truthy(exclusive) && !Js.truthy(name)) {
      throw JsError.typeError("Exclusive tests must provide a unique `name` identifying the test");
    }
    if (Js.truthy(name)) {
      next.exclusiveTests.put(propertyKey(name), Js.truthy(exclusive));
    }
    List<Test> kept = new ArrayList<>();
    for (Test fn : next.tests) {
      if (Js.strictEquals(fn.name(), name)) {
        if (isExclusive || fn.testFunction() == validate.testFunction()) {
          continue;
        }
      }
      kept.add(fn);
    }
    kept.add(validate);
    next.tests = kept;
    return next;
  }

  private static Object propertyKey(Object name) {
    return name instanceof JsSymbol ? name : Js.toJsString(name);
  }

  private static JsObject testConfig(Object[] args) {
    if (args.length == 1) {
      Object arg = args[0];
      if (arg instanceof JsFunction) {
        return JsObject.of("test", arg);
      }
      if (arg instanceof JsObject object) {
        return object;
      }
      if (Js.isNullish(arg)) {
        throw JsError.typeError("Cannot read properties of " + (arg == null ? "null" : "undefined")
            + " (reading 'message')");
      }
      if (!Js.isObject(arg)) {
        throw JsError.typeError("Cannot create property 'message' on " + Js.typeOf(arg) + " '"
            + Js.displayString(arg) + "'");
      }
      throw JsError.typeError("`test` is a required parameters");
    }
    if (args.length == 2) {
      return JsObject.of("name", args[0], "test", args[1]);
    }
    return JsObject.of("name", JsFunction.arg(args, 0), "message", JsFunction.arg(args, 1), "test",
        JsFunction.arg(args, 2));
  }

  /**
   * {@code when(keys, options)} or {@code when(options)}: a condition on referenced values; {@code options} is a
   * builder function or {@code {is, then, otherwise}}.
   *
   * @param keys a key, an array of keys, or the options when there are no keys
   * @param options the builder or options
   * @return the new schema
   */
  public Schema when(Object keys, Object options) {
    Object refKeys = keys;
    Object opts = options;
    if (!(refKeys instanceof JsArray) && !(refKeys instanceof String)) {
      opts = refKeys;
      refKeys = ".";
    }
    Schema next = copy();
    List<Reference> refs = new ArrayList<>();
    if (refKeys instanceof JsArray array) {
      for (int i = 0; i < array.length(); i++) {
        if (array.has(i)) {
          refs.add(new Reference(array.get(i), Js.UNDEFINED));
        }
      }
    } else {
      refs.add(new Reference(refKeys, Js.UNDEFINED));
    }
    for (Reference dep : refs) {
      if (dep.isSibling()) {
        next.deps.add(dep.key());
      }
    }
    next.conditions.add(opts instanceof JsFunction function ? new Condition(refs, function)
        : Condition.fromOptions(refs, opts));
    return next;
  }

  /**
   * {@code typeError(message)}: the message of the type check.
   *
   * @param message the message
   * @return the new schema
   */
  public Schema typeError(Object message) {
    Schema next = copy();
    next.internalTests.put("typeError", new Test(JsObject.of(
        "message", message,
        "name", "typeError",
        "skipAbsent", true,
        "test", (JsFunction) (self, args) -> {
          TestContext ctx = (TestContext) self;
          if (!Js.truthy(ctx.schema().typeCheck(JsFunction.arg(args, 0)))) {
            return ctx.createError(JsObject.of("params", JsObject.of("type", ctx.schema().type)));
          }
          return true;
        })));
    return next;
  }

  /**
   * {@code oneOf(values, message)}: only these values (and references to them) are allowed.
   *
   * @param enums an array of values and references
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public Schema oneOf(Object enums, Object message) {
    Object msg = message == Js.UNDEFINED ? Locale.message("mixed", "oneOf") : message;
    Schema next = copy();
    for (Object value : elements(enums, "enums")) {
      next.whitelist.add(value);
      next.blacklist.delete(value);
    }
    next.internalTests.put("whiteList", new Test(JsObject.of(
        "message", msg,
        "name", "oneOf",
        "skipAbsent", true,
        "test", (JsFunction) (self, args) -> {
          TestContext ctx = (TestContext) self;
          var valids = ctx.schema().whitelist;
          JsArray resolved = valids.resolveAll(ctx::resolve);
          if (includes(resolved, JsFunction.arg(args, 0))) {
            return true;
          }
          return ctx.createError(JsObject.of("params", JsObject.of("values", valids.join(), "resolved", resolved)));
        })));
    return next;
  }

  /**
   * {@code notOneOf(values, message)}: these values (and references to them) are not allowed.
   *
   * @param enums an array of values and references
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public Schema notOneOf(Object enums, Object message) {
    Object msg = message == Js.UNDEFINED ? Locale.message("mixed", "notOneOf") : message;
    Schema next = copy();
    for (Object value : elements(enums, "enums")) {
      next.blacklist.add(value);
      next.whitelist.delete(value);
    }
    next.internalTests.put("blacklist", new Test(JsObject.of(
        "message", msg,
        "name", "notOneOf",
        "test", (JsFunction) (self, args) -> {
          TestContext ctx = (TestContext) self;
          var invalids = ctx.schema().blacklist;
          JsArray resolved = invalids.resolveAll(ctx::resolve);
          if (includes(resolved, JsFunction.arg(args, 0))) {
            return ctx.createError(JsObject.of("params", JsObject.of("values", invalids.join(), "resolved", resolved)));
          }
          return true;
        })));
    return next;
  }

  /**
   * The elements {@code array.forEach} visits (empty slots skipped).
   *
   * @param array an array
   * @param name the parameter name, for the error
   * @return the elements
   */
  static List<Object> elements(Object array, String name) {
    if (!(array instanceof JsArray list)) {
      Js.read(array, "forEach");
      throw JsError.typeError(name + ".forEach is not a function");
    }
    List<Object> elements = new ArrayList<>();
    for (int i = 0; i < list.length(); i++) {
      if (list.has(i)) {
        elements.add(list.get(i));
      }
    }
    return elements;
  }

  private static boolean includes(JsArray array, Object value) {
    for (Object item : array.toList()) {
      if (Js.sameValueZero(item, value)) {
        return true;
      }
    }
    return false;
  }

  /**
   * {@code strip(strip = true)}: the field is left out of the parent object.
   *
   * @param strip whether to strip; {@code undefined} means true
   * @return the new schema
   */
  public Schema strip(Object strip) {
    Schema next = copy();
    next.spec.put("strip", strip == Js.UNDEFINED ? true : strip);
    return next;
  }

  @Override
  public Object describe(Object options) {
    Schema next = (Js.truthy(options) ? resolve(options) : this).copy();
    JsObject description = new JsObject();
    description.put("meta", next.spec.get("meta"));
    description.put("label", next.spec.get("label"));
    description.put("optional", next.spec.get("optional"));
    description.put("nullable", next.spec.get("nullable"));
    description.put("default", next.getDefault(options));
    description.put("type", next.type);
    description.put("oneOf", next.whitelist.describe());
    description.put("notOneOf", next.blacklist.describe());
    JsArray testList = new JsArray();
    List<Test> all = next.tests;
    for (int i = 0; i < all.size(); i++) {
      Test fn = all.get(i);
      int first = -1;
      for (int j = 0; j < all.size(); j++) {
        if (Js.strictEquals(all.get(j).name(), fn.name())) {
          first = j;
          break;
        }
      }
      if (first != i) {
        continue;
      }
      Object params = fn.options().get("params");
      if (Js.truthy(params) && Js.truthy(options)) {
        JsObject copy = new JsObject();
        Js.spread(copy, params);
        params = Test.resolveParams(copy, options);
      }
      testList.push(JsObject.of("name", fn.name(), "params", params));
    }
    description.put("tests", testList);
    return description;
  }

  /** @return {@code describe()} without options */
  public Object describe() {
    return describe(Js.UNDEFINED);
  }

  @Override
  public JsObject standard() {
    return StandardSchema.props(this);
  }

  @Override
  public Object getProperty(String key) {
    return switch (key) {
      case "type" -> type;
      case "spec" -> spec;
      case "__isYupSchema__" -> true;
      default -> Js.UNDEFINED;
    };
  }

  /**
   * The internal tests by name ({@code typeError}, {@code nullable}, {@code optionality}, {@code whiteList},
   * {@code blacklist}).
   *
   * @return the internal tests
   */
  protected Map<String, Test> internalTests() {
    return internalTests;
  }
}
