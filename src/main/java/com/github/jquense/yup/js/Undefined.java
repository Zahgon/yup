/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

/** The JavaScript {@code undefined} value; Java {@code null} stands for JavaScript {@code null}. */
public enum Undefined {
  /** The single {@code undefined} value. */
  INSTANCE;

  @Override
  public String toString() {
    return "undefined";
  }
}
