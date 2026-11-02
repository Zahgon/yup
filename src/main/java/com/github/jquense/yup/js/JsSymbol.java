/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

/** A JavaScript symbol; every instance is a distinct symbol. */
public final class JsSymbol {
  private final String description;

  /**
   * A new symbol.
   *
   * @param description its description, or {@code null} for none
   */
  public JsSymbol(String description) {
    this.description = description;
  }

  /** @return the description, or {@code null} when there is none */
  public String description() {
    return description;
  }

  /** @return {@code Symbol(description)}, as {@code String(symbol)} writes it */
  public String descriptiveString() {
    return "Symbol(" + (description == null ? "" : description) + ")";
  }

  @Override
  public String toString() {
    return descriptiveString();
  }
}
