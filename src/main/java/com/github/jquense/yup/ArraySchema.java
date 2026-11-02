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
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.util.PrintValue;
import java.util.ArrayList;
import java.util.List;

/** {@code array(of)}: arrays whose elements are cast and validated with an inner schema. */
public class ArraySchema extends Schema {
  private Object innerType = Js.UNDEFINED;

  /**
   * {@code array(type)}.
   *
   * @param type the element schema, or {@code undefined}
   */
  public ArraySchema(Object type) {
    super("array", JsObject.of("types", type));
    this.innerType = type;
  }

  /** {@code array()}. */
  public ArraySchema() {
    this(Js.UNDEFINED);
  }

  private ArraySchema(boolean blank) {}

  @Override
  protected Schema blank() {
    return new ArraySchema(true);
  }

  @Override
  protected void copyInto(Schema next) {
    ((ArraySchema) next).innerType = innerType;
  }

  @Override
  public Prototype prototype() {
    return Prototype.ARRAY;
  }

  @Override
  protected Object typeCheck(Object value) {
    return value instanceof JsArray;
  }

  /** @return the element schema, or {@code undefined} */
  public Object innerType() {
    return innerType;
  }

  void setInnerType(Object type) {
    innerType = type;
  }

  @Override
  protected Object castInternal(Object rawValue, Object options) {
    Object value = super.castInternal(rawValue, options);
    if (!Js.truthy(typeCheck(value)) || !Js.truthy(innerType)) {
      return value;
    }
    JsArray array = (JsArray) value;
    JsArray castArray = JsArray.ofLength(array.length());
    boolean isChanged = false;
    Object path = Js.get(options, "path");
    for (int idx = 0; idx < array.length(); idx++) {
      if (!array.has(idx)) {
        continue;
      }
      Object element = array.get(idx);
      JsObject elementOptions = new JsObject();
      Js.spread(elementOptions, options);
      elementOptions.put("path", (Js.truthy(path) ? Js.toJsString(path) : "") + "[" + idx + "]");
      Object castElement = ((SchemaLike) innerType).cast(element, elementOptions);
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
    Object inner = innerType;
    Object recursiveOption = Js.get(opts, "recursive");
    Object recursive = Js.isNullish(recursiveOption) ? spec.get("recursive") : recursiveOption;
    super.validateInternal(value, opts, panic, (arrayErrors, castValue) -> {
      if (!Js.truthy(recursive) || !Js.truthy(inner) || !Js.truthy(typeCheck(castValue))) {
        next.call(arrayErrors, castValue);
        return;
      }
      Object originalOption = Js.get(opts, "originalValue");
      Object originalParent = Js.isNullish(originalOption) ? value : originalOption;
      JsArray array = (JsArray) castValue;
      List<RunTest> tests = new ArrayList<>();
      for (int index = 0; index < array.length(); index++) {
        tests.add(((ISchema) inner).asNestedTest(new NestedTestConfig(opts, castValue, originalParent,
            Js.get(opts, "path"), Js.UNDEFINED, (double) index)));
      }
      runTests(tests, castValue, originalParent, Js.UNDEFINED, opts, panic, (innerErrors, unused) -> {
        List<Object> all = new ArrayList<>((List<?>) innerErrors);
        all.addAll((List<?>) arrayErrors);
        next.call(all, castValue);
      });
    });
  }

  /** @return {@code json()}: parses JSON strings into arrays */
  public ArraySchema json() {
    return (ArraySchema) transform(ObjectSchema.parseJson());
  }

  @Override
  public ArraySchema concat(Object other) {
    ArraySchema next = (ArraySchema) super.concat(other);
    next.innerType = innerType;
    Object otherInner = Js.read(other, "innerType");
    if (Js.truthy(otherInner)) {
      if (Js.truthy(next.innerType)) {
        if (!(next.innerType instanceof Schema schema)) {
          throw JsError.typeError("next.innerType.concat is not a function");
        }
        next.innerType = schema.concat(otherInner);
      } else {
        next.innerType = otherInner;
      }
    }
    return next;
  }

  @Override
  public Object getProperty(String key) {
    return key.equals("innerType") ? innerType : super.getProperty(key);
  }

  /**
   * {@code of(schema)}: sets the element schema.
   *
   * @param schema the element schema
   * @return the new schema
   * @throws JsError a {@code TypeError} when it is not a schema
   */
  public ArraySchema of(Object schema) {
    ArraySchema next = (ArraySchema) copy();
    if (!Yup.isSchema(schema)) {
      throw JsError.typeError("`array.of()` sub-schema must be a valid yup schema not: "
          + PrintValue.print(schema, false));
    }
    next.innerType = schema;
    JsObject nextSpec = next.spec.copy();
    nextSpec.put("types", schema);
    next.spec = nextSpec;
    return next;
  }

  private ArraySchema limit(String name, Object javaLimit, Object message, String operator) {
    Object limit = Js.fromJava(javaLimit);
    // length() defaults an undefined message; min() and max() replace any falsy one.
    boolean useLocale = name.equals("length") ? message == Js.UNDEFINED : !Js.truthy(message);
    return (ArraySchema) test(JsObject.of(
        "message", useLocale ? Locale.message("array", name) : message,
        "name", name,
        "exclusive", true,
        "params", JsObject.of(name, limit),
        "skipAbsent", true,
        "test", (JsFunction) (self, args) -> {
          Object length = Js.get(JsFunction.arg(args, 0), "length");
          Object bound = ((TestContext) self).resolve(limit);
          return switch (operator) {
            case "===" -> Js.strictEquals(length, bound);
            case ">=" -> Js.ge(length, bound);
            default -> Js.le(length, bound);
          };
        }));
  }

  /**
   * {@code length(length, message)}.
   *
   * @param length the exact length (a number or reference)
   * @param message the message, {@code undefined} for the locale's
   * @return the new schema
   */
  public ArraySchema length(Object length, Object message) {
    return limit("length", length, message, "===");
  }

  /**
   * {@code min(min, message)}.
   *
   * @param min the minimum length (a number or reference)
   * @param message the message, the locale's when falsy
   * @return the new schema
   */
  public ArraySchema min(Object min, Object message) {
    return limit("min", min, message, ">=");
  }

  /**
   * {@code max(max, message)}.
   *
   * @param max the maximum length (a number or reference)
   * @param message the message, the locale's when falsy
   * @return the new schema
   */
  public ArraySchema max(Object max, Object message) {
    return limit("max", max, message, "<=");
  }

  /** @return {@code ensure()}: defaults to {@code []} and wraps other values in an array */
  public ArraySchema ensure() {
    ArraySchema self = this;
    return (ArraySchema) defaultValue((JsFunction) (thisArg, args) -> new JsArray()).transform((JsFunction) (thisArg, args) -> {
      Object value = args[0];
      Object original = args[1];
      if (Js.truthy(self.typeCheck(value))) {
        return value;
      }
      if (Js.isNullish(original)) {
        return new JsArray();
      }
      if (original instanceof JsArray array) {
        return array.copy();
      }
      return JsArray.of(original);
    });
  }

  /**
   * {@code compact(rejector)}: removes falsy elements, or the elements the rejector returns true for.
   *
   * @param rejector {@code (value, index, array) => boolean}, or {@code undefined}
   * @return the new schema
   */
  public ArraySchema compact(Object rejector) {
    return (ArraySchema) transform((JsFunction) (self, args) -> {
      Object values = args[0];
      if (Js.isNullish(values)) {
        return values;
      }
      if (!(values instanceof JsArray array)) {
        throw JsError.typeError("values.filter is not a function");
      }
      JsArray kept = new JsArray();
      for (int i = 0; i < array.length(); i++) {
        if (!array.has(i)) {
          continue;
        }
        Object value = array.get(i);
        boolean keep;
        if (!Js.truthy(rejector)) {
          keep = Js.truthy(value);
        } else {
          if (!(rejector instanceof JsFunction function)) {
            throw JsError.typeError("rejector is not a function");
          }
          keep = !Js.truthy(function.call(Js.UNDEFINED, value, (double) i, array));
        }
        if (keep) {
          kept.push(value);
        }
      }
      return kept;
    });
  }

  @Override
  public Object describe(Object options) {
    ArraySchema next = (ArraySchema) (Js.truthy(options) ? resolve(options) : this).copy();
    JsObject base = (JsObject) super.describe(options);
    if (Js.truthy(next.innerType)) {
      Object innerOptions = options;
      Object optionValue = Js.get(innerOptions, "value");
      if (Js.truthy(optionValue)) {
        JsObject copy = new JsObject();
        Js.spread(copy, innerOptions);
        copy.put("parent", optionValue);
        copy.put("value", Js.get(optionValue, "0"));
        innerOptions = copy;
      }
      base.put("innerType", ((SchemaLike) next.innerType).describe(innerOptions));
    }
    return base;
  }
}
