/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

/** A JavaScript function value: called with a receiver ({@code this}) and arguments. */
@FunctionalInterface
public interface JsFunction {
  /**
   * Calls the function.
   *
   * @param thisArg the receiver
   * @param args the arguments; a missing argument reads as {@code undefined}
   * @return the result
   */
  Object call(Object thisArg, Object... args);

  /** @return the function's {@code name} */
  default String name() {
    return "";
  }

  /**
   * Gives a function a name.
   *
   * @param name the name
   * @param body the function
   * @return the named function
   */
  static JsFunction named(String name, JsFunction body) {
    return new Named(name, body);
  }

  /**
   * An argument, {@code undefined} when it was not passed.
   *
   * @param args the arguments
   * @param index the position
   * @return the argument
   */
  static Object arg(Object[] args, int index) {
    return index < args.length ? args[index] : Js.UNDEFINED;
  }

  /** A function with a name. */
  record Named(String name, JsFunction body) implements JsFunction {
    @Override
    public Object call(Object thisArg, Object... args) {
      return body.call(thisArg, args);
    }
  }
}
