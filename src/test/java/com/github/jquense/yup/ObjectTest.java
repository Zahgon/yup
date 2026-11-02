/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup); ported from test/object.ts.
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
import static com.github.jquense.yup.Support.throwsError;
import static com.github.jquense.yup.Support.validate;

import com.github.jquense.yup.js.JsDate;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;

/** Object schemas. */
public class ObjectTest {
  private static JsFunction then(java.util.function.UnaryOperator<Schema> fn) {
    return (self, args) -> fn.apply((Schema) args[0]);
  }

  private static ObjectSchema full() {
    return Yup.object(obj("num", Yup.number(), "str", Yup.string(), "arr", Yup.array().of(Yup.number()), "dte",
        Yup.date(), "nested", Yup.object().shape(obj("str", Yup.string()), U), "arrNested",
        Yup.array().of(Yup.object().shape(obj("num", Yup.number()), U)), "stripped", Yup.string().strip(U)));
  }

  /** Casting: JSON, failed casts, recursion and identity. */
  public void testCasting() {
    eq(obj("hello", 5), cast(Yup.object(obj("hello", Yup.number())).json(), "{ \"hello\": \"5\" }"));
    eq("dfhdfh", cast(Yup.object(), "dfhdfh", obj("assert", false)));
    JsObject input = obj("num", "5", "str", "hello", "arr", arr("4", 5), "dte", "2014-09-23T19:25:25Z", "nested",
        obj("str", 5), "arrNested", arr(obj("num", 5), obj("num", "5")));
    eq(obj("num", 5, "str", "hello", "arr", arr(4, 5), "dte", new JsDate(1411500325000.0), "nested", obj("str", "5"),
        "arrNested", arr(obj("num", 5), obj("num", 5))), cast(full(), input));
    JsObject already = obj("num", 5, "str", "hello", "arr", arr(4, 5), "dte", new JsDate(1411500325000.0), "nested",
        obj("str", "5"), "arrNested", arr(obj("num", 5), obj("num", 5)));
    check(cast(full(), already) == already, "an already cast object to be returned as it is");
    eq(obj("1", "b", "z", "a"), cast(Yup.object(obj("z", Yup.string(), "1", Yup.string())), obj("z", "a", "1", "b")));
  }

  /** Recursive validation, strictness and custom tests. */
  public void testValidation() {
    ObjectSchema inst = Yup.object(obj("num", Yup.number(), "nested", Yup.object(obj("str", Yup.string().strict(U)))));
    check(isValid(inst, U), "undefined to be valid");
    eq("nested.str", invalid(inst, obj("num", "4", "nested", obj("str", 5))).path());
    eq(obj("field", "5"), validate(Yup.object(obj("field", Yup.string())), obj("field", 5)));
    throwsError("ValidationError", "must be a `string` type",
        () -> validate(Yup.object(obj("field", Yup.string())).strict(U), obj("field", 5)));
    eq(obj("field", 5), cast(Yup.object(obj("field", Yup.number().strict(U))), obj("field", "5")));
    eq(arr("this oops"), errors(Yup.object().shape(obj("prop", Yup.mixed()), U)
        .test("test", "${path} oops", constant(false)), new JsObject(), U));
  }

  /** stripUnknown, noUnknown, exact and strip(). */
  public void testUnknownKeys() {
    ObjectSchema inst = Yup.object(obj("str", Yup.string()));
    eq(obj("str", "hi"), cast(inst, obj("str", "hi", "extra", false, "sneaky", U), obj("stripUnknown", true)));
    eq(obj("names", obj("first", "john")), cast(Yup.object(obj("names", Yup.object(obj("first", Yup.string())))),
        obj("extra", true, "names", obj("first", "john", "extra", true)), obj("stripUnknown", true)));
    ObjectSchema shaped = Yup.object().shape(obj("prop", Yup.mixed(), "other", Yup.mixed()), U);
    eq(arr("hi"), errors(shaped.noUnknown("hi", U), obj("extra", "field"), obj("strict", true)));
    eq(arr("this field has unspecified keys: extra"), errors(shaped.noUnknown(U, U), obj("extra", "field"),
        obj("strict", true)));
    eq(obj("extra", "field"), validate(((ObjectSchema) shaped.noUnknown(U, U)).noUnknown(false, U),
        obj("extra", "field")));
    eq(arr("this object contains unknown properties: extra"), errors(shaped.exact(U), obj("extra", "field"), U));
    eq(obj("prop", "bar"), cast(Yup.object().shape(obj("prop", Yup.mixed().strip(false), "other", Yup.mixed().strip(U)),
        U), obj("other", "boo", "prop", "bar")));
  }

  /** Defaults are built from the fields. */
  public void testDefaults() {
    ObjectSchema nest = Yup.object(obj("nest", Yup.object(obj("str", Yup.string().defaultValue("hi")))));
    eq(obj("nest", obj("str", "hi")), nest.getDefault());
    eq(obj("boom", "hi"), nest.defaultValue(obj("boom", "hi")).getDefault());
    // The invented default {x: {b: undefined}} is cast again, which drops the undefined b.
    eq(obj("prop", "foo", "other", obj("x", new JsObject())), cast(Yup.object(obj("prop", Yup.mixed(), "other",
        Yup.object(obj("x", Yup.object(obj("b", Yup.string())))))), obj("prop", "foo")));
    ObjectSchema conditional = Yup.object(obj("child", Yup.string().when("$variable", obj("is", "foo",
        "then", then(s -> s.defaultValue("is foo")), "otherwise", then(s -> s.defaultValue("not foo"))))));
    eq(obj("child", "is foo"), conditional.getDefault(obj("context", obj("variable", "foo"))));
    eq(obj("child", "not foo"), conditional.getDefault());
    eq(obj("child", "is foo"), cast(conditional, U, obj("context", obj("variable", "foo"))));
  }

  /** References between fields and to the context. */
  public void testRefs() {
    ObjectSchema schema = Yup.object(obj("quz", Yup.ref("baz"), "baz", Yup.ref("foo.bar"), "foo",
        Yup.object(obj("bar", Yup.string())), "x", Yup.ref("$x")));
    eq(obj("foo", obj("bar", "boom"), "baz", "boom", "quz", "boom", "x", 5),
        validate(schema, obj("foo", obj("bar", "boom")), obj("context", obj("x", 5))));
  }

  /** abortEarly, error flattening, ordering and recursive. */
  public void testErrorCollection() {
    Schema inst = Yup.object(obj("nest", Yup.object(obj("str", Yup.string().required(U)))))
        .test("name", "oops", constant(false));
    eq(arr("oops"), errors(inst, obj("nest", obj("str", "")), U));
    eq(arr("nest.str is a required field", "oops"), errors(inst, obj("nest", obj("str", "")), obj("abortEarly", false)));
    eq(arr("oops"), errors(inst, obj("nest", obj("str", null)), obj("abortEarly", false, "recursive", false)));
    ObjectSchema sorted = Yup.object(obj("foo", Yup.string().when("bar",
        (JsFunction) (self, args) -> Yup.string().min(5, U)), "bar", Yup.string().required(U)));
    eq(arr("foo must be at least 5 characters", "bar is a required field"),
        errors(sorted, obj("foo", "foo"), obj("abortEarly", false)));
  }

  /** partial(), deepPartial(), pick() and omit(). */
  public void testDerivedShapes() {
    ObjectSchema inst = Yup.object(obj("age", Yup.number().required(U), "name", Yup.string().required(U)));
    check(!isValid(inst, obj("age", null, "name", "")), "a required error");
    check(isValid(inst.partial(), new JsObject()) && !isValid(inst.partial(), obj("age", null)), "partial");
    ObjectSchema deep = Yup.object(obj("contacts", Yup.array(Yup.object(obj("name", Yup.string().required(U))))
        .defined(U)));
    check(!isValid(deep, new JsObject()) && isValid(deep.deepPartial(), obj("contacts", arr(new JsObject()))),
        "deepPartial");
    ObjectSchema defaults = Yup.object(obj("age", Yup.number().defaultValue(30).required(U), "name",
        Yup.string().defaultValue("pat").required(U), "color", Yup.string().defaultValue("red").required(U)));
    eq(obj("age", 30, "name", "pat"), defaults.pick(arr("age", "name")).getDefault());
    eq(obj("color", "red"), defaults.omit(arr("age", "name")).getDefault());
  }

  /** from(), key casing and transformKeys(). */
  public void testKeyTransforms() {
    Schema moved = Yup.object().shape(obj("myProp", Yup.mixed(), "Other", Yup.mixed()), U)
        .from("prop", "myProp", U);
    eq(obj("myProp", 5), cast(moved, obj("prop", 5)));
    eq(obj("foo", obj("bar", "quz"), "foobar", "quz"), cast(Yup.object(obj("foo", Yup.object(obj("bar", Yup.string()))))
        .from("foo.bar", "foobar", true), obj("foo", obj("bar", "quz"))));
    Schema camel = Yup.object().shape(obj("conStat", Yup.number(), "caseStatus", Yup.number(), "hiJohn", Yup.number()),
        U).camelCase();
    eq(obj("conStat", 5, "caseStatus", 6, "hiJohn", 4), cast(camel, obj("CON_STAT", 5, "CaseStatus", 6, "hi john", 4)));
    Schema constant = Yup.object().constantCase();
    eq(obj("CON_STAT", 5, "CASE_STATUS", 6, "HI_JOHN", 4), cast(constant, obj("conStat", 5, "CaseStatus", 6, "hi john",
        4)));
  }

  /** Dependency sorting: cycles and excluded edges. */
  public void testDependencies() {
    JsFunction requireIfMissing = (self, args) -> args[1];
    JsObject cyclic = obj("orgID", Yup.number().when("location", requireIfMissing), "location",
        Yup.string().when("orgID", requireIfMissing));
    throwsError("Error", "Cyclic dependency, node was:\"location\"", () -> Yup.object().shape(cyclic, U));
    eq(arr("location", "orgID"), new com.github.jquense.yup.js.JsArray(
        Yup.object().shape(cyclic, arr(arr("location", "orgID"))).nodes()));
  }
}
