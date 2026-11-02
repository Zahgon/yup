/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.cli;

import com.github.jquense.yup.js.JsError;

/** A request the command cannot carry out: a malformed member, an unknown name or a misused catalog function. */
final class RequestError extends JsError {
  private static final long serialVersionUID = 1L;

  /**
   * @param message what is wrong with the request
   */
  RequestError(String message) {
    super("RequestError", message);
  }
}
