/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

/**
 * A JavaScript {@code Error} object. It is both a value the library passes around and the exception that throws it,
 * so its {@code name} and {@code message} are JavaScript values like any other property.
 */
public class JsError extends RuntimeException implements JsProperties {
  private static final long serialVersionUID = 1L;

  /** The {@code name} property. */
  private transient Object name;

  /** The {@code message} property. */
  private transient Object message;

  /**
   * An error with the given name and message.
   *
   * @param name the {@code name} property
   * @param message the {@code message} property
   */
  public JsError(Object name, Object message) {
    super(null, null, false, false);
    this.name = name;
    this.message = message;
  }

  /**
   * @param message the message
   * @return a new {@code Error}
   */
  public static JsError error(String message) {
    return new JsError("Error", message);
  }

  /**
   * @param message the message
   * @return a new {@code TypeError}
   */
  public static JsError typeError(String message) {
    return new JsError("TypeError", message);
  }

  /**
   * @param message the message
   * @return a new {@code RangeError}
   */
  public static JsError rangeError(String message) {
    return new JsError("RangeError", message);
  }

  /**
   * @param message the message
   * @return a new {@code SyntaxError}
   */
  public static JsError syntaxError(String message) {
    return new JsError("SyntaxError", message);
  }

  /** @return the {@code name} property */
  public Object name() {
    return name;
  }

  /**
   * Sets the {@code name} property.
   *
   * @param name the new value
   */
  public void setName(Object name) {
    this.name = name;
  }

  /** @return the {@code message} property */
  public Object messageValue() {
    return message;
  }

  /**
   * Sets the {@code message} property.
   *
   * @param message the new value
   */
  public void setMessageValue(Object message) {
    this.message = message;
  }

  /** @return whether the value is a native {@code Error} instance ({@code instanceof Error}) */
  public boolean isNativeError() {
    return true;
  }

  @Override
  public String getMessage() {
    return message instanceof String text ? text : Js.displayString(message);
  }

  /** @return what {@code Error.prototype.toString} makes of this error */
  public String errorToString() {
    String nameText = name == Js.UNDEFINED ? "Error" : Js.toJsString(name);
    String messageText = message == Js.UNDEFINED ? "" : Js.toJsString(message);
    if (nameText.isEmpty()) {
      return messageText;
    }
    if (messageText.isEmpty()) {
      return nameText;
    }
    return nameText + ": " + messageText;
  }

  @Override
  public Object getProperty(String key) {
    return switch (key) {
      case "name" -> name;
      case "message" -> message;
      default -> Js.UNDEFINED;
    };
  }

  @Override
  public String toString() {
    return errorToString();
  }
}
