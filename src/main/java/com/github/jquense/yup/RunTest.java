/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

/** One step of a validation run: a test, or the validation of a nested value. */
@FunctionalInterface
public interface RunTest {
  /** A continuation of a validation run, called with errors and the value. */
  @FunctionalInterface
  interface Callback {
    /**
     * @param errors an error, an array (list) of errors, or null
     * @param value the value being validated ({@code undefined} when a test reports)
     */
    void call(Object errors, Object value);
  }

  /** What a test runs on. */
  record Args(Object value, Object originalValue, Object path, Object options, Schema schema) {}

  /**
   * Runs the step; it ends by calling {@code next} with its errors (or null), or {@code panic} to stop the run.
   *
   * @param args the value and options
   * @param panic stops the run with an error
   * @param next continues the run
   */
  void run(Args args, Callback panic, Callback next);
}
