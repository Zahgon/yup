/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsPromise;

/** What every schema, eager or lazy, can do. */
public interface ISchema extends SchemaLike {
  /** @return the schema type */
  Object type();

  /** @return the spec */
  JsObject spec();

  /** @return the prototype {@code addMethod} extends */
  Prototype prototype();

  /**
   * {@code validate(value, options)}: casts (unless strict) and validates.
   *
   * @param value the value
   * @param options the validation options, or {@code undefined}
   * @return a promise of the validated value, rejected with a {@link ValidationError}
   */
  JsPromise validate(Object value, Object options);

  /**
   * {@code validateSync(value, options)}.
   *
   * @param value the value
   * @param options the validation options, or {@code undefined}
   * @return the validated value
   * @throws ValidationError when the value is invalid
   */
  Object validateSync(Object value, Object options);

  /**
   * {@code validateAt(path, value, options)}: validates the value at a path with the schema at that path.
   *
   * @param path the path
   * @param value the root value
   * @param options the validation options, or {@code undefined}
   * @return a promise of the validated value
   */
  JsPromise validateAt(String path, Object value, Object options);

  /**
   * {@code validateSyncAt(path, value, options)}.
   *
   * @param path the path
   * @param value the root value
   * @param options the validation options, or {@code undefined}
   * @return the validated value
   */
  Object validateSyncAt(String path, Object value, Object options);

  /**
   * {@code isValid(value, options)}.
   *
   * @param value the value
   * @param options the validation options, or {@code undefined}
   * @return a promise of whether the value is valid
   */
  JsPromise isValid(Object value, Object options);

  /**
   * {@code isValidSync(value, options)}.
   *
   * @param value the value
   * @param options the validation options, or {@code undefined}
   * @return whether the value is valid
   */
  boolean isValidSync(Object value, Object options);

  /**
   * {@code asNestedTest(config)}: the validation of the value at a key or index of a parent.
   *
   * @param config where the value is
   * @return the run step
   */
  RunTest asNestedTest(NestedTestConfig config);

  /** @return the {@code ~standard} Standard Schema properties */
  JsObject standard();

  /**
   * Where a nested value sits.
   *
   * @param options the parent's validation options
   * @param parent the parent value
   * @param originalParent the parent before casting
   * @param parentPath the parent's path
   * @param key the key, or {@code undefined}
   * @param index the index (a number), or {@code undefined}
   */
  record NestedTestConfig(Object options, Object parent, Object originalParent, Object parentPath, Object key,
      Object index) {}
}
