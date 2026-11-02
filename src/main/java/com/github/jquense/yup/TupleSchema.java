/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsArray;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsObject;
import java.util.ArrayList;
import java.util.List;

/** {@code tuple(types)}: arrays of a fixed length whose elements each have their own schema. */
public class TupleSchema extends Schema {
  /**
   * {@code tuple(schemas)}.
   *
   * @param schemas an array of element schemas
   */
  public TupleSchema(Object schemas) {
    super("tuple", JsObject.of("types", schemas));
    withMutation(s -> s.typeError(Locale.message("tuple", "notType")));
  }

  private TupleSchema(boolean blank) {}

  @Override
  protected Schema blank() {
    return new TupleSchema(true);
  }

  @Override
  public Prototype prototype() {
    return Prototype.TUPLE;
  }

  private JsArray types() {
    if (!(spec.get("types") instanceof JsArray types)) {
      throw JsError.typeError("types.map is not a function");
    }
    return types;
  }

  @Override
  protected Object typeCheck(Object value) {
    Object types = spec.get("types");
    return value instanceof JsArray array && Js.strictEquals((double) array.length(), Js.read(types, "length"));
  }

  @Override
  protected Object castInternal(Object rawValue, Object options) {
    Object value = super.castInternal(rawValue, options);
    if (!Js.truthy(typeCheck(value))) {
      return value;
    }
    JsArray types = types();
    JsArray array = (JsArray) value;
    boolean isChanged = false;
    JsArray castArray = JsArray.ofLength(types.length());
    Object path = Js.get(options, "path");
    for (int idx = 0; idx < types.length(); idx++) {
      if (!types.has(idx)) {
        continue;
      }
      JsObject elementOptions = new JsObject();
      Js.spread(elementOptions, options);
      elementOptions.put("path", (Js.truthy(path) ? Js.toJsString(path) : "") + "[" + idx + "]");
      Object element = array.get(idx);
      Object castElement = ((SchemaLike) types.get(idx)).cast(element, elementOptions);
      if (!Js.strictEquals(castElement, element)) {
        isChanged = true;
      }
      castArray.set(idx, castElement);
    }
    return isChanged ? castArray : value;
  }

  @Override
  protected void validateInternal(Object value, Object options, RunTest.Callback panic, RunTest.Callback next) {
    Object opts = options == Js.UNDEFINED ? new JsObject() : options;
    Object itemTypes = spec.get("types");
    super.validateInternal(value, opts, panic, (tupleErrors, castValue) -> {
      if (!Js.truthy(typeCheck(castValue))) {
        next.call(tupleErrors, castValue);
        return;
      }
      Object originalOption = Js.get(opts, "originalValue");
      Object originalParent = Js.isNullish(originalOption) ? value : originalOption;
      List<RunTest> tests = new ArrayList<>();
      JsArray types = (JsArray) itemTypes;
      for (int index = 0; index < types.length(); index++) {
        Object itemSchema = types.get(index);
        if (!(itemSchema instanceof ISchema schema)) {
          throw JsError.typeError("Cannot read properties of undefined (reading 'asNestedTest')");
        }
        tests.add(schema.asNestedTest(new NestedTestConfig(opts, castValue, originalParent, Js.get(opts, "path"),
            Js.UNDEFINED, (double) index)));
      }
      runTests(tests, castValue, originalParent, Js.UNDEFINED, opts, panic, (innerErrors, unused) -> {
        List<Object> all = new ArrayList<>((List<?>) innerErrors);
        all.addAll((List<?>) tupleErrors);
        next.call(all, castValue);
      });
    });
  }

  @Override
  public Object describe(Object options) {
    TupleSchema next = (TupleSchema) (Js.truthy(options) ? resolve(options) : this).copy();
    JsObject base = (JsObject) super.describe(options);
    JsArray inner = new JsArray();
    JsArray types = next.types();
    for (int index = 0; index < types.length(); index++) {
      Object innerOptions = options;
      Object optionValue = Js.get(innerOptions, "value");
      if (Js.truthy(optionValue)) {
        JsObject copy = new JsObject();
        Js.spread(copy, innerOptions);
        copy.put("parent", optionValue);
        copy.put("value", Js.get(optionValue, Integer.toString(index)));
        innerOptions = copy;
      }
      inner.push(((SchemaLike) types.get(index)).describe(innerOptions));
    }
    base.put("innerType", inner);
    return base;
  }
}
