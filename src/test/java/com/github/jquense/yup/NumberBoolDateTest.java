/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup); ported from test/number.ts, test/bool.ts and test/date.ts.
 */
package com.github.jquense.yup;

import static com.github.jquense.yup.Support.U;
import static com.github.jquense.yup.Support.arr;
import static com.github.jquense.yup.Support.cast;
import static com.github.jquense.yup.Support.check;
import static com.github.jquense.yup.Support.date;
import static com.github.jquense.yup.Support.eq;
import static com.github.jquense.yup.Support.errors;
import static com.github.jquense.yup.Support.isValid;
import static com.github.jquense.yup.Support.obj;
import static com.github.jquense.yup.Support.throwsError;

import com.github.jquense.yup.js.JsDate;
import com.github.jquense.yup.js.JsObject;

/** Number, boolean and date schemas. */
public class NumberBoolDateTest {
  /** Number casting, rounding and truncating. */
  public void testNumberCasting() {
    NumberSchema schema = Yup.number();
    eq(5.0, cast(schema, "5"));
    eq(3.0, cast(schema, 3));
    eq(5.656, cast(schema, " 5.656 "));
    eq(12.0, cast(schema, "1 2"));
    eq(31.0, cast(schema, "0x1F"));
    for (Object bad : new Object[] {"", false, true, new JsDate(0), new JsObject()}) {
      throwsError("TypeError", "satisfies the schema type: \"number\"", () -> cast(schema, bad));
    }
    eq(46.0, cast(schema.round("ceIl"), 45.1111));
    eq(45.0, cast(schema.round(U), 45.444444));
    eq(null, cast(((NumberSchema) ((NumberSchema) schema.nullable()).integer(U)).round(U), null));
    throwsError("TypeError", "Only valid options for round() are: ceil, floor, round, trunc", () -> schema.round("fasf"));
    eq(45.0, cast(schema.truncate(), 45.55));
    eq(Double.NaN, cast(schema, "asfasf", obj("assert", false)));
    eq(null, cast(schema, null, obj("assert", false)));
  }

  /** Number defaults, type checks and validation. */
  public void testNumberValidation() {
    eq(0.0, Yup.number().defaultValue(0).getDefault());
    eq(5.0, Yup.number().defaultValue(0).defaultValue(5).required(U).getDefault());
    eq(true, Yup.number().isType(5.0));
    eq(false, Yup.number().isType(Double.NaN));
    eq(false, Yup.number().isType(null));
    check(!isValid(Yup.number(), " ") && !isValid(Yup.number(), "12abc") && isValid(Yup.number(), "0xff"),
        "string numbers");
    Schema inst = Yup.number().min(4, U);
    check(isValid(inst, 5) && !isValid(inst, 2), "min");
    eq(arr("this must be less than 5"), errors(Yup.number().lessThan(5, U), 6, U));
    eq(arr("this must be greater than 5"), errors(Yup.number().moreThan(5, U), 4, U));
    eq(arr("this must be an integer"), errors(Yup.number().integer(U), 10.53, U));
    eq(arr("this must be a positive number"), errors(Yup.number().positive(U), 0, U));
    eq(arr("this must be a negative number"), errors(Yup.number().negative(U), 10, U));
    check(!isValid(Yup.number().min(5, U).min(10, U).min(15, U), 14), "min to replace earlier mins");
    eq(arr("this must be a `number` type, but the final value was: `NaN` (cast from the value `\"john\"`)."),
        errors(Yup.number(), "john", U));
  }

  /** Describing resolves param refs. */
  public void testDescribeResolvesRefs() {
    Object description = Yup.number().min(Yup.ref("$foo"), U).describe(obj("value", 10, "context", obj("foo", 5)));
    eq(arr(obj("name", "min", "params", obj("min", 5))), ((JsObject) description).get("tests"));
  }

  /** Boolean casting and isTrue/isFalse. */
  public void testBoolean() {
    BooleanSchema inst = Yup.bool();
    eq(true, cast(inst, "true"));
    eq(true, cast(inst, "True"));
    eq(false, cast(inst, "False"));
    eq(true, cast(inst, 1));
    eq(false, cast(inst, 0));
    throwsError("TypeError", "\"boolean\"", () -> cast(inst, "foo"));
    eq(U, inst.getDefault());
    eq(true, inst.defaultValue(true).required(U).getDefault());
    // boolean's default() always passes its argument on, so without one it sets an undefined default
    check(inst.defaultValue(true).defaultValue() instanceof BooleanSchema, "default() returns a schema");
    eq(U, ((BooleanSchema) inst.defaultValue(true).defaultValue()).getDefault());
    eq(false, inst.isType(1.0));
    eq(true, inst.isType(false));
    check(isValid(inst, "1") && !isValid(inst.strict(U), null), "boolean validation");
    eq(arr("this is a required field"), errors(inst.required(U), U, U));
    check(isValid(inst.isTrue(U), true) && !isValid(inst.isTrue(U), false), "isTrue");
    check(isValid(inst.isFalse(U), false) && !isValid(inst.isFalse(U), true), "isFalse");
  }

  /** Date casting, including V8's legacy formats. */
  public void testDateCasting() {
    DateSchema inst = Yup.date();
    eq(date(2014, 0, 15), cast(inst, "jan 15 2014"));
    eq(new JsDate(1411500325000.0), cast(inst, "2014-09-23T19:25:25Z"));
    eq(new JsDate(1470828739012.0), cast(inst, "2016-08-10T11:32:19.012Z"));
    eq(new JsDate(1470828739212.0), cast(inst, "2016-08-10T11:32:19.2125Z"));
    eq(date(2001, 0, 5), cast(inst, "Jan 5"));
    eq(null, cast(inst, null, obj("assert", false)));
    check(((JsDate) cast(inst, "", obj("assert", false))).isInvalid(), "an invalid date");
    eq(true, inst.isType(new JsDate(0)));
    eq(false, inst.isType(false));
  }

  /** Date min and max. */
  public void testDateLimits() {
    Schema inst = Yup.date().max(date(2014, 5, 15), U);
    check(isValid(inst, date(2014, 0, 15)) && !isValid(inst, date(2014, 7, 15)) && isValid(inst, "5"), "max");
    throwsError("TypeError", "could not be cast", () -> Yup.date().max("hello", U));
    throwsError("TypeError", "`min` must be a Date or a value that can be `cast()` to a Date",
        () -> Yup.date().min(U, U));
    JsDate min = date(2014, 3, 15);
    check(isValid(Yup.date().min(min, U), date(2014, 5, 15)) && !isValid(Yup.date().min(min, U), date(2014, 1, 15)),
        "min");
    Schema withRef = Yup.date().min(Yup.ref("$foo"), U);
    check(isValid(withRef, date(2014, 5, 15), obj("context", obj("foo", min))), "min with a ref");
    eq(arr("this field must be later than 2014-04-15T00:00:00.000Z"),
        errors(Yup.date().min(min, U), date(2014, 1, 15), U));
  }
}
