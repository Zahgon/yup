/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;

/** {@code mixed()}: any value, or a custom type with its own check. */
public class MixedSchema extends Schema {
  private Object check;

  /**
   * {@code mixed(spec)}: {@code spec} is a type check function, or an object of {@code type}, {@code check} and extra
   * options, or {@code undefined}.
   *
   * @param spec the check or options
   */
  public MixedSchema(Object spec) {
    super(typeOf(spec), specOf(spec));
    this.check = checkOf(spec);
  }

  /** {@code mixed()}. */
  public MixedSchema() {
    this(Js.UNDEFINED);
  }

  private MixedSchema(boolean blank) {}

  private static Object typeOf(Object spec) {
    if (spec instanceof JsFunction || !(spec instanceof JsObject object) || !object.has("type")) {
      return "mixed";
    }
    return object.get("type");
  }

  private static JsObject specOf(Object spec) {
    if (spec instanceof JsObject object && object.get("spec") instanceof JsObject extra) {
      return extra;
    }
    return null;
  }

  private static Object checkOf(Object spec) {
    if (spec instanceof JsFunction function) {
      return function;
    }
    if (spec instanceof JsObject object && object.has("check")) {
      return object.get("check");
    }
    return null;
  }

  @Override
  protected Schema blank() {
    return new MixedSchema(true);
  }

  @Override
  protected void copyInto(Schema next) {
    ((MixedSchema) next).check = check;
  }

  @Override
  public Prototype prototype() {
    return Prototype.MIXED;
  }

  @Override
  protected Object typeCheck(Object value) {
    if (check == null) {
      return true;
    }
    if (!(check instanceof JsFunction function)) {
      throw JsError.typeError("this._typeCheck is not a function");
    }
    return function.call(this, value);
  }
}
