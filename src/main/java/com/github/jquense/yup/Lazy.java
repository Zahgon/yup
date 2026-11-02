/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsException;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsPromise;
import com.github.jquense.yup.js.JsProperties;

/** {@code lazy(builder)}: a schema chosen by a function of the value each time it is used. */
public final class Lazy implements ISchema, JsProperties {
  private final Object builder;
  private JsObject spec = JsObject.of("meta", Js.UNDEFINED, "optional", false);

  /**
   * {@code lazy(builder)}.
   *
   * @param builder {@code (value, options) => schema}
   */
  public Lazy(Object builder) {
    this.builder = builder;
  }

  @Override
  public Object type() {
    return "lazy";
  }

  @Override
  public JsObject spec() {
    return spec;
  }

  @Override
  public Prototype prototype() {
    return Prototype.LAZY;
  }

  private Lazy copy(JsObject override) {
    Lazy next = new Lazy(builder);
    JsObject merged = spec.copy();
    Js.spread(merged, override);
    next.spec = merged;
    return next;
  }

  // _resolve: the builder's schema (made optional when this is), resolved.
  private Schema resolveFor(Object value, Object options) {
    Object opts = options == Js.UNDEFINED ? new JsObject() : options;
    if (!(builder instanceof JsFunction function)) {
      throw JsError.typeError("this.builder is not a function");
    }
    Object schema = function.call(this, value, opts);
    if (!Yup.isSchema(schema)) {
      throw JsError.typeError("lazy() functions must return a valid schema");
    }
    ISchema built = (ISchema) schema;
    if (Js.truthy(spec.get("optional"))) {
      built = built instanceof Schema s ? s.optional() : ((Lazy) built).optional();
    }
    return (Schema) built.resolve(opts);
  }

  /** @return {@code optional()}: the built schema is made optional */
  public Lazy optional() {
    return copy(JsObject.of("optional", true));
  }

  @Override
  public Schema resolve(Object options) {
    return resolveFor(Js.read(options, "value"), options);
  }

  @Override
  public Object cast(Object value, Object options) {
    return resolveFor(value, options).cast(value, options);
  }

  @Override
  public RunTest asNestedTest(NestedTestConfig config) {
    Object k = config.index() != Js.UNDEFINED && config.index() != null ? config.index() : config.key();
    Object value = Js.read(config.parent(), Js.toJsString(k));
    JsObject options = new JsObject();
    Js.spread(options, config.options());
    options.put("value", value);
    options.put("parent", config.parent());
    return resolveFor(value, options).asNestedTest(config);
  }

  private static JsPromise catchValidationError(java.util.function.Supplier<JsPromise> fn) {
    try {
      return fn.get();
    } catch (RuntimeException e) {
      Object error = JsException.valueOf(e);
      if (ValidationError.isError(error)) {
        return JsPromise.rejected(error);
      }
      throw e;
    }
  }

  @Override
  public JsPromise validate(Object value, Object options) {
    return catchValidationError(() -> resolveFor(value, options).validate(value, options));
  }

  @Override
  public Object validateSync(Object value, Object options) {
    return resolveFor(value, options).validateSync(value, options);
  }

  @Override
  public JsPromise validateAt(String path, Object value, Object options) {
    return catchValidationError(() -> resolveFor(value, options).validateAt(path, value, options));
  }

  @Override
  public Object validateSyncAt(String path, Object value, Object options) {
    return resolveFor(value, options).validateSyncAt(path, value, options);
  }

  @Override
  public JsPromise isValid(Object value, Object options) {
    try {
      return resolveFor(value, options).isValid(value, options);
    } catch (RuntimeException e) {
      if (ValidationError.isError(JsException.valueOf(e))) {
        return JsPromise.resolved(false);
      }
      throw e;
    }
  }

  @Override
  public boolean isValidSync(Object value, Object options) {
    return resolveFor(value, options).isValidSync(value, options);
  }

  @Override
  public Object describe(Object options) {
    if (Js.truthy(options)) {
      return resolve(options).describe(options);
    }
    return JsObject.of("type", "lazy", "meta", spec.get("meta"), "label", Js.UNDEFINED);
  }

  /** @return {@code meta()}: the metadata */
  public Object meta() {
    return spec.get("meta");
  }

  /**
   * {@code meta(obj)}: merges metadata (into the metadata object this lazy shares with the one it was copied from).
   *
   * @param obj the metadata to merge
   * @return the new lazy schema
   */
  public Lazy meta(Object obj) {
    Lazy next = copy(new JsObject());
    Object current = next.spec.get("meta");
    JsObject target = current instanceof JsObject existing ? existing : new JsObject();
    Js.spread(target, obj);
    next.spec.put("meta", target);
    return next;
  }

  @Override
  public JsObject standard() {
    return StandardSchema.props(this);
  }

  @Override
  public Object getProperty(String key) {
    return switch (key) {
      case "type" -> "lazy";
      case "spec" -> spec;
      case "__isYupSchema__" -> true;
      default -> Js.UNDEFINED;
    };
  }
}
