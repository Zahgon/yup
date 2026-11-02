/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsNumbers;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsProperties;
import com.github.jquense.yup.js.JsToString;
import com.github.jquense.yup.util.PropertyExpr;
import java.util.function.Function;

/**
 * {@code ref(key)}: a reference to a sibling value ({@code "a.b"}), the value itself ({@code ".a"}) or the context
 * ({@code "$a"}).
 */
public final class Reference implements SchemaLike, JsProperties, JsToString {
  private final String key;
  private final boolean isContext;
  private final boolean isValue;
  private final boolean isSibling;
  private final String path;
  private final Function<Object, Object> getter;
  private final Object map;

  /**
   * {@code new Reference(key, options)}.
   *
   * @param key the key (must be a string)
   * @param options {@code undefined} or an object with an optional {@code map} function
   * @throws JsError a {@code TypeError} for a key that is not a non-empty string
   */
  public Reference(Object key, Object options) {
    if (!(key instanceof String keyText)) {
      throw JsError.typeError(Js.toJsString(Js.add("ref must be a string, got: ", key)));
    }
    this.key = JsNumbers.trim(keyText);
    if (keyText.isEmpty()) {
      throw JsError.typeError("ref must be a non-empty string");
    }
    this.isContext = this.key.startsWith("$");
    this.isValue = this.key.startsWith(".");
    this.isSibling = !isContext && !isValue;
    String prefix = isContext ? "$" : isValue ? "." : "";
    this.path = this.key.substring(prefix.length());
    this.getter = path.isEmpty() ? null : PropertyExpr.getter(path, true);
    this.map = options == Js.UNDEFINED ? Js.UNDEFINED : Js.read(options, "map");
  }

  /**
   * {@code ref(key)}.
   *
   * @param key the key
   */
  public Reference(String key) {
    this(key, Js.UNDEFINED);
  }

  /** @return the trimmed key */
  public String key() {
    return key;
  }

  /** @return whether the key starts with {@code $} */
  public boolean isContext() {
    return isContext;
  }

  /** @return whether the key starts with {@code .} */
  public boolean isValue() {
    return isValue;
  }

  /** @return whether it refers to a sibling value */
  public boolean isSibling() {
    return isSibling;
  }

  /** @return the key without its prefix */
  public String path() {
    return path;
  }

  /**
   * The referenced value: read from the context, the value or the parent, then mapped.
   *
   * @param value the value
   * @param parent the parent
   * @param context the context
   * @return the referenced value
   */
  public Object getValue(Object value, Object parent, Object context) {
    Object result = isContext ? context : isValue ? value : parent;
    if (getter != null) {
      result = getter.apply(Js.truthy(result) ? result : new JsObject());
    }
    if (Js.truthy(map)) {
      if (!(map instanceof JsFunction mapper)) {
        throw JsError.typeError("this.map is not a function");
      }
      result = mapper.call(this, result);
    }
    return result;
  }

  @Override
  public Object cast(Object value, Object options) {
    return getValue(value, Js.get(options, "parent"), Js.get(options, "context"));
  }

  @Override
  public SchemaLike resolve(Object options) {
    return this;
  }

  /** @return {@code {type: 'ref', key}} */
  public JsObject describe() {
    return JsObject.of("type", "ref", "key", key);
  }

  @Override
  public Object describe(Object options) {
    return describe();
  }

  @Override
  public String jsToString() {
    return "Ref(" + key + ")";
  }

  @Override
  public String toString() {
    return jsToString();
  }

  @Override
  public Object getProperty(String name) {
    return switch (name) {
      case "key" -> key;
      case "isContext" -> isContext;
      case "isValue" -> isValue;
      case "isSibling" -> isSibling;
      case "path" -> path;
      case "map" -> map;
      default -> Js.UNDEFINED;
    };
  }
}
