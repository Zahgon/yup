/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

/** Throws a JavaScript value that is not an {@link JsError} ({@code throw value}). */
public final class JsException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /** The thrown value. */
  private final transient Object value;

  private JsException(Object value) {
    super(null, null, false, false);
    this.value = value;
  }

  /**
   * The exception that throws a JavaScript value.
   *
   * @param value the value to throw
   * @return the value itself when it is an error, otherwise a wrapper
   */
  public static RuntimeException of(Object value) {
    return value instanceof JsError error ? error : new JsException(value);
  }

  /**
   * The JavaScript value an exception throws.
   *
   * @param exception a caught exception
   * @return the thrown value
   */
  public static Object valueOf(RuntimeException exception) {
    return exception instanceof JsException wrapper ? wrapper.value : exception;
  }

  @Override
  public String getMessage() {
    return Js.displayString(value);
  }
}
