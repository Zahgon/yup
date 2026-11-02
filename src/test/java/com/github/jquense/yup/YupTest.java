/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup); ported from test/yup.js, test/setLocale.ts,
 * test/ValidationError.ts, test/util/parseIsoDate.ts and test/standardSchema.ts.
 */
package com.github.jquense.yup;

import static com.github.jquense.yup.Support.U;
import static com.github.jquense.yup.Support.arr;
import static com.github.jquense.yup.Support.cast;
import static com.github.jquense.yup.Support.check;
import static com.github.jquense.yup.Support.constant;
import static com.github.jquense.yup.Support.eq;
import static com.github.jquense.yup.Support.errors;
import static com.github.jquense.yup.Support.obj;
import static com.github.jquense.yup.Support.throwsError;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsDate;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.util.ParseIsoDate;

/** The library's entry points: cast assertions, reach, getIn, addMethod, locales, errors, ISO dates, Standard Schema. */
public class YupTest {
  /** cast() asserts the result type unless told not to. */
  public void testCastAssertions() {
    eq(U, cast(Yup.string(), U));
    throwsError("TypeError", "The value of field could not be cast to a value that satisfies the schema type: \"string\".",
        () -> cast(Yup.string().defined(U).transform(constant(U)), "foo"));
    throwsError("TypeError", "attempted value: null", () -> cast(Yup.string(), null));
    eq(null, cast(Yup.string(), null, obj("assert", false)));
  }

  /** getIn() and reach() walk objects, arrays and tuples. */
  public void testReach() {
    NumberSchema num = Yup.number();
    ObjectSchema shape = Yup.object(obj("num-1", num));
    ObjectSchema inst = Yup.object(obj("num", Yup.number().max(4, U), "nested", Yup.object(obj("arr",
        Yup.array().of(shape)))));
    JsObject value = obj("nested", obj("arr", arr(new JsObject(), obj("num-1", 2))));
    Reach.Result found = Yup.getIn(inst, "nested.arr[1].num-1", value, U);
    check(found.schema() == num, "the number schema");
    eq("num-1", found.parentPath());
    eq(obj("num-1", 2), found.parent());
    Reach.Result element = Yup.getIn(inst, "nested.arr[1]", value, U);
    check(element.schema() == shape, "the element schema");
    eq("1", element.parentPath());
    ObjectSchema tupled = Yup.object(obj("nested", Yup.tuple(arr(Yup.string(), Yup.object(obj("arr",
        Yup.array().of(Yup.object(obj("num", num)))))))));
    check(Yup.reach(tupled, "", U, U) == tupled, "the root for an empty path");
    for (String path : new String[] {"nested[1].arr[0].num", "nested[1].arr[].num", "nested[1].arr.num"}) {
      check(Yup.reach(tupled, path, U, U) == num, path + " to reach the number");
    }
    throwsError("Error", "Yup.reach cannot implicitly index into a tuple type. the path part \".nested\" must contain "
        + "an index to the tuple element, e.g. \".nested[0]\"", () -> Yup.reach(tupled, "nested.arr[1].num", U, U));
    throwsError("Error", "The schema does not contain the path: nope.", () -> Yup.reach(tupled, "nope", U, U));
  }

  /** addMethod() extends schema prototypes. */
  public void testAddMethod() {
    JsFunction here = (self, args) -> ((Schema) self).label("here");
    try {
      Yup.addMethod(Yup.Constructor.SCHEMA, "foo", here);
      check(Yup.Constructor.STRING.prototype().addedMethod("foo") == here, "the method on string schemas");
      throwsError("TypeError", "You must provide a yup schema constructor function",
          () -> Yup.addMethod(Yup.Constructor.LAZY, "foo", here));
      throwsError("TypeError", "A Method name must be provided", () -> Yup.addMethod(Yup.Constructor.STRING, 5.0, here));
      throwsError("TypeError", "Method function must be provided",
          () -> Yup.addMethod(Yup.Constructor.STRING, "foo", "x"));
    } finally {
      Prototype.SCHEMA.remove("foo");
    }
  }

  /** setLocale() changes the messages schemas pick up. */
  public void testSetLocale() {
    try {
      eq("${path} must be a valid email", Locale.message("string", "email"));
      Yup.setLocale(obj("string", obj("email", "Invalid email")));
      eq("Invalid email", Locale.message("string", "email"));
      eq(arr("Invalid email"), errors(Yup.string().email(U), "nope", U));
      throwsError("TypeError", "Cannot set properties of undefined (setting 'polluted')",
          () -> Yup.setLocale(obj("toString", obj("polluted", "oh no"))));
    } finally {
      Locale.reset();
    }
  }

  /** formatError() and stack-less errors. */
  public void testValidationError() {
    eq("Some message here", ValidationError.formatError("Some message ${param}", obj("param", "here")));
    eq("label goes here", ValidationError.formatError("${path} goes here", obj("label", "label")));
    eq("this goes here", ValidationError.formatError("${path} goes here", new JsObject()));
    eq("this value is undefined", ValidationError.formatError("${path} value is ${min}", obj("min", U)));
    eq("this value is null", ValidationError.formatError("${path} value is ${min}", obj("min", null)));
    eq("this value is NaN", ValidationError.formatError("${path} value is ${min}", obj("min", Double.NaN)));
    eq("this value is 0", ValidationError.formatError("${path} value is ${min}", obj("min", 0)));
    ValidationError disabled = new ValidationError("error", 1.0, "field", "type", true);
    check(!disabled.isNativeError(), "a stack-less error");
    eq("field", disabled.path());
  }

  /** parseIsoDate(): plain dates, times and offsets (local time is UTC). */
  public void testParseIsoDate() {
    eq(0.0, ParseIsoDate.parseIsoDate("1970-01-01"));
    eq(JsDate.fromParts(2001, 1, 3), ParseIsoDate.parseIsoDate("2001-02-03"));
    eq(JsDate.fromParts(-2001, 1, 3), ParseIsoDate.parseIsoDate("-002001-02-03"));
    eq(JsDate.fromParts(10000, 1, 1), ParseIsoDate.parseIsoDate("+010000-02"));
    eq(Double.NaN, ParseIsoDate.parseIsoDate("asdf"));
    eq(JsDate.fromParts(2001, 1, 3, 4, 5, 6, 7), ParseIsoDate.parseIsoDate("2001-02-03T04:05:06.007"));
    eq(JsDate.fromParts(2001, 1, 3, 4, 5 + 390, 6, 7), ParseIsoDate.parseIsoDate("2001-02-03T04:05:06.007-06:30"));
    eq(JsDate.fromParts(2001, 1, 3, 4, 5 - 390, 6, 7), ParseIsoDate.parseIsoDate("2001-02-03T04:05:06.007+06:30"));
    eq(JsDate.fromParts(1970, 0, 1), ParseIsoDate.parseIsoDate("1970-01-01 00:00:00"));
  }

  /** The Standard Schema interface reports issues with paths. */
  public void testStandardSchema() {
    ObjectSchema schema = Yup.object(obj("obj", Yup.object(obj("foo", Yup.string().required(U), "not.obj.nested",
        Yup.string().required(U))).required(U), "not.a.field", Yup.string().required(U)));
    JsObject standard = schema.standard();
    eq(1.0, standard.get("version"));
    eq("yup", standard.get("vendor"));
    Object result = StandardSchema.validate(schema, obj("obj", obj("foo", "", "not.obj.nested", ""))).await();
    eq(obj("issues", arr(obj("message", "obj.foo is a required field", "path", arr("obj", "foo")),
        obj("message", "obj[\"not.obj.nested\"] is a required field", "path", arr("obj", "not.obj.nested")),
        obj("message", "[\"not.a.field\"] is a required field", "path", arr("not.a.field")))), result);
    eq(obj("value", "abc"), StandardSchema.validate(Yup.string(), "abc").await());
    check(Js.truthy(Yup.isSchemaValue(Yup.lazy(constant(Yup.string())))), "a lazy schema to be a schema");
  }
}
