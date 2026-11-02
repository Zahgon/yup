/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

/** A Java object whose JavaScript properties can be read by name (property access on it). */
public interface JsProperties {
  /**
   * Reads a property.
   *
   * @param key the property name
   * @return its value, {@code undefined} when there is none
   */
  Object getProperty(String key);
}
