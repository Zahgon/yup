/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup); ported from test/mixed.ts.
 */
package com.github.jquense.yup;

import static com.github.jquense.yup.Support.U;
import static com.github.jquense.yup.Support.arr;
import static com.github.jquense.yup.Support.cast;
import static com.github.jquense.yup.Support.check;
import static com.github.jquense.yup.Support.constant;
import static com.github.jquense.yup.Support.eq;
import static com.github.jquense.yup.Support.errors;
import static com.github.jquense.yup.Support.invalid;
import static com.github.jquense.yup.Support.isValid;
import static com.github.jquense.yup.Support.obj;
import static com.github.jquense.yup.Support.thrown;
import static com.github.jquense.yup.Support.throwsError;
import static com.github.jquense.yup.Support.validate;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsPromise;

/** Behaviour shared by every schema: defaults, tests, conditions, oneOf, concat, describe. */
public class MixedTest {
  private static final JsFunction IS_STRING = (self, args) -> args[0] instanceof String;

  private static JsFunction then(java.util.function.UnaryOperator<Schema> fn) {
    return (self, args) -> fn.apply((Schema) args[0]);
  }

  /** Nullability, defaults and custom checks. */
  public void testBasics() {
    check(!isValid(Yup.mixed(), null) && isValid(Yup.mixed().nullable(), null), "nullability");
    eq("hello", cast(Yup.mixed().defaultValue("hello"), U));
    eq("hi", Yup.string().defaultValue("hi").getDefault(new JsObject()));
    Schema greet = Yup.string().when("$foo", obj("is", "greet", "then", then(s -> s.defaultValue("hi"))));
    eq("hi", greet.getDefault(obj("context", obj("foo", "greet"))));
    MixedSchema custom = Yup.mixed(IS_STRING);
    eq(false, custom.isType(1.0));
    eq(true, custom.isType("foo"));
    eq(arr("this must match the configured type. The validated value was: `1`."), errors(custom, 1, U));
    MixedSchema typed = Yup.mixed(obj("type", "string", "check", IS_STRING));
    eq(arr("this must be a `string` type, but the final value was: `1`."), errors(typed, 1, U));
    eq(null, Yup.string().required(U).cast(null, obj("assert", "ignore-optionality")));
    eq(arr("this cannot be null"), errors(Yup.string().strict(U), null, U));
  }

  /** Synchronous validation, promises in tests, and errors from tests. */
  public void testSyncAndAsync() {
    Schema asyncTest = Yup.mixed().test("test", "foo", (JsFunction) (self, args) -> JsPromise.resolved(true));
    throwsError("Error", "Validation test of type: \"test\" returned a Promise during a synchronous validate",
        () -> asyncTest.validateSync(new JsObject(), U));
    eq(new JsObject(), validate(asyncTest, new JsObject()));
    Schema is42 = Yup.number().test("is-42", "${path} isn't 42", (JsFunction) (self, args) -> Js.strictEquals(args[0], 42.0));
    eq(42.0, is42.validateSync(42.0, U));
    eq("this isn't 42", ((ValidationError) thrown(() -> is42.validateSync(43.0, U))).getMessage());
    eq(false, is42.isValidSync(43.0, U));
    Schema boom = Yup.string().test((JsFunction) (self, args) -> JsPromise.rejected(
        com.github.jquense.yup.js.JsError.error("oops an error occurred")));
    throwsError("Error", "oops an error occurred", () -> validate(boom, "joe"));
  }

  /** Function messages, typeError, oneOf and notOneOf. */
  public void testMessagesAndValues() {
    Schema labelled = Yup.string().label("My string").required(
        (JsFunction) (self, args) -> Js.get(args[0], "label") + " is required");
    eq(arr("My string is required"), errors(labelled, U, U));
    Schema typed = Yup.string().strict(U).typeError("must be a ${type}!");
    ValidationError error = invalid(typed, 5);
    eq("typeError", error.type());
    eq("must be a string!", error.messageValue());
    ValidationError all = invalid(typed, 5, obj("abortEarly", false));
    eq(U, all.type());
    eq(arr("this must be one of the following values: 5, hello"), errors(Yup.mixed().oneOf(arr(5, "hello"), U), 6, U));
    Schema withRefs = Yup.mixed().oneOf(arr(Yup.ref("$someValues[0]"), Yup.ref("$someValues[1]")), U);
    eq(1.0, validate(withRefs, 1, obj("context", obj("someValues", arr(1, 2)))));
    eq(arr("this must not be one of the following values: 5, hello"),
        errors(Yup.mixed().nullable().notOneOf(arr(5, "hello"), U), 5, U));
    check(isValid(Yup.mixed().oneOf(arr(Double.NaN), U), Double.NaN), "oneOf to use SameValueZero");
    check(isValid(Yup.mixed().oneOf(arr("hello"), U), U), "oneOf to skip undefined");
    check(!isValid(Yup.mixed().notOneOf(arr("hello", U), U), U), "notOneOf not to skip undefined");
  }

  /** Exclusive and non-exclusive tests. */
  public void testTestDeduplication() {
    JsFunction noop = (self, args) -> U;
    eq(1, ((java.util.List<?>) Yup.mixed().test("test", " ", noop).test("test", "asdasd", noop).tests()).size());
    eq(2, Yup.mixed().test("test", " ", noop).test("test", "x", (JsFunction) (self, args) -> U).tests().size());
    eq(1, Yup.mixed().test(obj("name", "test", "exclusive", true, "message", " ", "test", noop))
        .test(obj("name", "test", "exclusive", true, "message", " ", "test", (JsFunction) (self, args) -> U))
        .tests().size());
    throwsError("TypeError", "Exclusive tests must provide a unique `name` identifying the test",
        () -> Yup.mixed().test(obj("message", "invalid", "exclusive", true, "test", noop)));
    throwsError("TypeError", "`test` is a required parameters", () -> Yup.mixed().test(obj("name", "x")));
    eq(2, Yup.string().required(U).required(U).tests().size());
  }

  /** The test context: path, parent, options and createError. */
  public void testTestContext() {
    JsObject[] seen = {null};
    Schema inst = Yup.object(obj("other", Yup.mixed(), "test", Yup.mixed().test(obj(
        "message", "invalid", "exclusive", true, "name", "max",
        "test", (JsFunction) (self, args) -> {
          TestContext ctx = (TestContext) self;
          seen[0] = obj("path", ctx.path(), "parent", ctx.parent(), "context", Js.get(ctx.options(), "context"));
          return true;
        }))));
    validate(inst, obj("other", 5, "test", "hi"), obj("context", obj("user", "jason")));
    eq(obj("path", "test", "parent", obj("other", 5, "test", "hi"), "context", obj("user", "jason")), seen[0]);
    Schema made = Yup.mixed().test(obj("message", "invalid ${path}", "name", "max",
        "test", (JsFunction) (self, args) -> ((TestContext) self).createError(obj("path", "my.path"))));
    ValidationError error = invalid(made, "");
    eq("my.path", error.path());
    eq(arr("invalid my.path"), error.errors());
  }

  /** Conditions on the parent, the context and the value itself. */
  public void testConditions() {
    Schema inst = Yup.mixed().when("prop", obj("is", 5, "then", then(s -> s.required("from parent"))));
    check(invalid(inst, U, obj("parent", obj("prop", 5))) != null, "a required error");
    eq(U, validate(inst, U, obj("parent", obj("prop", 1))));
    Schema strInst = Yup.string().when("$prop", obj("is", (JsFunction) (self, args) -> Js.strictEquals(args[0], 5.0),
        "then", then(s -> s.required(U)), "otherwise", then(s -> ((StringSchema) s).min(4, U))));
    eq("hello", validate(strInst, "hello", obj("context", obj("prop", 1))));
    check(invalid(strInst, "hel", obj("context", obj("prop", 1))) != null, "a min error");
    Schema self = Yup.number().when(".", obj("is", (JsFunction) (thisArg, args) -> Js.gt(args[0], 0.0),
        "then", then(s -> ((NumberSchema) s).min(5, U))));
    check(invalid(self, 4) != null && validate(self, 5).equals(5.0) && validate(self, -1).equals(-1.0), "self refs");
    throwsError("TypeError", "either `then:` or `otherwise:` is required for `when()` conditions",
        () -> Yup.mixed().when("a", obj("is", 1)));
    throwsError("TypeError", "conditions must return a schema object",
        () -> validate(Yup.mixed().when("a", constant(5)), 1));
  }

  /** concat() merges tests, values and presence. */
  public void testConcat() {
    Schema trimmed = Yup.string().trim(U).concat(Yup.string().min(4, U));
    eq("hello", cast(trimmed, " hello  "));
    check(!isValid(trimmed, " he  "), "the min test to run");
    throwsError("TypeError", "You cannot `concat()` schema's of different types: string and object",
        () -> Yup.string().defaultValue("hi").concat(Yup.object()));
    eq("string", Yup.mixed().defaultValue("hi").concat(Yup.string()).type());
    eq(U, Yup.string().defaultValue("hi").concat(Yup.string().defaultValue(U)).getDefault());
    Schema presence = Yup.string().required(U).concat(Yup.string().nullable());
    check(isValid(presence, U) && isValid(presence, null), "the second schema's presence");
    Schema lists = Yup.string().oneOf(arr("a", "b", "c"), U).concat(Yup.string().notOneOf(arr("b"), U));
    check(isValid(lists, "a") && !isValid(lists, "b"), "the second schema's values");
  }

  /** describe(), with and without options. */
  public void testDescribe() {
    Schema schema = Yup.string().max(2, U).defaultValue((JsFunction) (self, args) -> "a").meta(obj("input", "foo"))
        .label("str!").oneOf(arr("a", "b"), U).notOneOf(arr(Yup.ref("foo")), U);
    eq(obj("meta", obj("input", "foo"), "label", "str!", "optional", true, "nullable", false, "default", "a",
        "type", "string", "oneOf", arr("a", "b"), "notOneOf", arr(obj("type", "ref", "key", "foo")),
        "tests", arr(obj("name", "max", "params", obj("max", 2)))), schema.describe());
    Schema fancy = Yup.string().label("bad label").when("type", obj("is", true,
        "then", then(s -> s.required(U).label("good label")), "otherwise", then(s -> s.label("default label"))));
    eq("good label", ((JsObject) fancy.describe(obj("parent", obj("type", true)))).get("label"));
    eq("default label", ((JsObject) fancy.describe(obj("parent", obj("type", false)))).get("label"));
  }
}
