/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.JsFunction;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The prototype chain of the schema classes, which {@code addMethod} extends: the member names each prototype defines
 * and the methods added to it.
 */
public final class Prototype {
  /** {@code Object.prototype}. */
  public static final Prototype OBJECT = new Prototype(null, false, "constructor", "__defineGetter__",
      "__defineSetter__", "hasOwnProperty", "__lookupGetter__", "__lookupSetter__", "isPrototypeOf",
      "propertyIsEnumerable", "toString", "valueOf", "__proto__", "toLocaleString");
  /** {@code Schema.prototype}. */
  public static final Prototype SCHEMA = new Prototype(OBJECT, true, "constructor", "_type", "clone", "label", "meta",
      "withMutation", "concat", "isType", "resolve", "resolveOptions", "cast", "_cast", "_validate", "runTests",
      "asNestedTest", "validate", "validateSync", "isValid", "isValidSync", "_getDefault", "getDefault", "default",
      "strict", "nullability", "optionality", "optional", "defined", "nullable", "nonNullable", "required",
      "notRequired", "transform", "test", "when", "typeError", "oneOf", "notOneOf", "strip", "describe", "~standard",
      "__isYupSchema__", "validateAt", "validateSyncAt", "equals", "is", "not", "nope");
  /** {@code MixedSchema.prototype}. */
  public static final Prototype MIXED = new Prototype(SCHEMA, true, "constructor");
  /** {@code BooleanSchema.prototype}. */
  public static final Prototype BOOLEAN = new Prototype(SCHEMA, true, "constructor", "isTrue", "isFalse", "default",
      "defined", "optional", "required", "notRequired", "nullable", "nonNullable", "strip");
  /** {@code StringSchema.prototype}. */
  public static final Prototype STRING = new Prototype(SCHEMA, true, "constructor", "required", "notRequired",
      "length", "min", "max", "matches", "email", "url", "uuid", "datetime", "ensure", "trim", "lowercase",
      "uppercase");
  /** {@code NumberSchema.prototype}. */
  public static final Prototype NUMBER = new Prototype(SCHEMA, true, "constructor", "min", "max", "lessThan",
      "moreThan", "positive", "negative", "integer", "truncate", "round");
  /** {@code DateSchema.prototype}. */
  public static final Prototype DATE = new Prototype(SCHEMA, true, "constructor", "prepareParam", "min", "max");
  /** {@code ObjectSchema.prototype}. */
  public static final Prototype OBJECT_SCHEMA = new Prototype(SCHEMA, true, "constructor", "_cast", "_validate",
      "clone", "concat", "_getDefault", "setFields", "shape", "partial", "deepPartial", "pick", "omit", "from", "json",
      "exact", "stripUnknown", "noUnknown", "unknown", "transformKeys", "camelCase", "snakeCase", "constantCase",
      "describe");
  /** {@code ArraySchema.prototype}. */
  public static final Prototype ARRAY = new Prototype(SCHEMA, true, "constructor", "_cast", "_validate", "clone",
      "json", "concat", "of", "length", "min", "max", "ensure", "compact", "describe");
  /** {@code TupleSchema.prototype}. */
  public static final Prototype TUPLE = new Prototype(SCHEMA, true, "constructor", "_cast", "_validate", "describe");
  /** {@code Lazy.prototype} (not a schema prototype: lazy schemas are schemas by an instance field). */
  public static final Prototype LAZY = new Prototype(OBJECT, false, "constructor", "clone", "optionality", "optional",
      "resolve", "cast", "asNestedTest", "validate", "validateSync", "validateAt", "validateSyncAt", "isValid",
      "isValidSync", "describe", "meta", "~standard");
  /** The prototype of a plain function such as {@code ref} or {@code lazy}. */
  public static final Prototype FUNCTION = new Prototype(OBJECT, false, "constructor");

  private final Prototype parent;
  private final boolean schema;
  private final Set<String> builtins;
  private final Map<String, JsFunction> added = new LinkedHashMap<>();

  private Prototype(Prototype parent, boolean schema, String... builtins) {
    this.parent = parent;
    this.schema = schema;
    this.builtins = Set.of(builtins);
  }

  /** @return whether it is a schema prototype ({@code isSchema(prototype)}) */
  public boolean isSchema() {
    return schema;
  }

  /**
   * The {@code in} operator: whether the prototype or its chain has the member.
   *
   * @param name a member name
   * @return whether it has it
   */
  public synchronized boolean has(String name) {
    for (Prototype p = this; p != null; p = p.parent) {
      if (p.builtins.contains(name) || p.added.containsKey(name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Where a method name resolves along the chain.
   *
   * @param name a member name
   * @return the added method it resolves to, or null when it resolves to a built-in member or to nothing
   */
  public synchronized JsFunction addedMethod(String name) {
    for (Prototype p = this; p != null; p = p.parent) {
      if (p.builtins.contains(name)) {
        return null;
      }
      JsFunction method = p.added.get(name);
      if (method != null) {
        return method;
      }
    }
    return null;
  }

  /**
   * Whether a method name resolves to a built-in member.
   *
   * @param name a member name
   * @return whether the nearest definition is built in
   */
  public synchronized boolean hasBuiltin(String name) {
    for (Prototype p = this; p != null; p = p.parent) {
      if (p.builtins.contains(name)) {
        return true;
      }
      if (p.added.containsKey(name)) {
        return false;
      }
    }
    return false;
  }

  synchronized void add(String name, JsFunction method) {
    added.put(name, method);
  }

  /**
   * Removes an added method.
   *
   * @param name the method name
   */
  public synchronized void remove(String name) {
    added.remove(name);
  }
}
