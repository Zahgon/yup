/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsProperties;
import java.util.function.UnaryOperator;

/**
 * The {@code this} of a test function: where the value sits, the options, the schema, {@code createError} and
 * {@code resolve}.
 */
public final class TestContext implements JsProperties {
  private final Object path;
  private final Object parent;
  private final Object type;
  private final Object from;
  private final Object options;
  private final Object originalValue;
  private final Schema schema;
  private final UnaryOperator<Object> errorFactory;
  private final UnaryOperator<Object> resolver;

  TestContext(Object path, Object parent, Object type, Object from, Object options, Object originalValue, Schema schema,
      UnaryOperator<Object> errorFactory, UnaryOperator<Object> resolver) {
    this.path = path;
    this.parent = parent;
    this.type = type;
    this.from = from;
    this.options = options;
    this.originalValue = originalValue;
    this.schema = schema;
    this.errorFactory = errorFactory;
    this.resolver = resolver;
  }

  /** @return the path of the value */
  public Object path() {
    return path;
  }

  /** @return the parent value */
  public Object parent() {
    return parent;
  }

  /** @return the test's name */
  public Object type() {
    return type;
  }

  /** @return the ancestors ({@code {schema, value}} objects, nearest first), or {@code undefined} */
  public Object from() {
    return from;
  }

  /** @return the validation options */
  public Object options() {
    return options;
  }

  /** @return the value before casting */
  public Object originalValue() {
    return originalValue;
  }

  /** @return the schema being validated */
  public Schema schema() {
    return schema;
  }

  /**
   * {@code this.createError(overrides)}.
   *
   * @param overrides {@code undefined} or an object of {@code path}, {@code message}, {@code params}, {@code type},
   *     {@code disableStackTrace}
   * @return the validation error
   */
  public ValidationError createError(Object overrides) {
    return (ValidationError) errorFactory.apply(overrides);
  }

  /**
   * {@code this.resolve(item)}: the value of a reference, anything else as it is.
   *
   * @param item a value or reference
   * @return the resolved value
   */
  public Object resolve(Object item) {
    return resolver.apply(item);
  }

  @Override
  public Object getProperty(String key) {
    return switch (key) {
      case "path" -> path;
      case "parent" -> parent;
      case "type" -> type;
      case "from" -> from;
      case "options" -> options;
      case "originalValue" -> originalValue;
      case "schema" -> schema;
      default -> Js.UNDEFINED;
    };
  }
}
