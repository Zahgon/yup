/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

/** A Java object with its own JavaScript {@code toString} method (what string conversion of it calls). */
public interface JsToString {
  /** @return the result of the object's JavaScript {@code toString()} */
  String jsToString();
}
