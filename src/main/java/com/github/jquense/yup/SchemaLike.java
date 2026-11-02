/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

/** What an object shape can hold: a schema, a lazy schema or a reference to another value. */
public interface SchemaLike {
  /**
   * Resolves conditions and lazy builders against a value and its surroundings.
   *
   * @param options the resolve options ({@code value}, {@code parent}, {@code context}), a JavaScript object
   * @return the resolved schema, or this reference
   */
  SchemaLike resolve(Object options);

  /**
   * Casts a value.
   *
   * @param value the value
   * @param options the cast options, a JavaScript object or {@code undefined}
   * @return the cast value
   */
  Object cast(Object value, Object options);

  /**
   * Describes the schema.
   *
   * @param options resolve options, or {@code undefined}
   * @return the description, a JavaScript object
   */
  Object describe(Object options);
}
