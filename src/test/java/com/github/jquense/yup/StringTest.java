/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup); ported from test/string.ts.
 */
package com.github.jquense.yup;

import static com.github.jquense.yup.Support.U;
import static com.github.jquense.yup.Support.cast;
import static com.github.jquense.yup.Support.check;
import static com.github.jquense.yup.Support.constant;
import static com.github.jquense.yup.Support.eq;
import static com.github.jquense.yup.Support.errors;
import static com.github.jquense.yup.Support.isValid;
import static com.github.jquense.yup.Support.obj;
import static com.github.jquense.yup.Support.throwsError;

import com.github.jquense.yup.js.JsArray;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsRegExp;

/** String schemas. */
public class StringTest {
  /** Casting. */
  public void testCasting() {
    StringSchema schema = Yup.string();
    eq("5", cast(schema, 5));
    eq("3", cast(schema, "3"));
    eq("", cast(schema, ""));
    eq("true", cast(schema, true));
    eq("false", cast(schema, false));
    eq("0", cast(schema, 0));
    eq(null, cast(schema.nullable(), null));
    eq("hey", cast(schema, obj("toString", constant("hey"))));
    eq("1e+21", cast(schema, 1e21));
    for (Object bad : new Object[] {null, new JsObject(), new JsArray()}) {
      throwsError("TypeError", "could not be cast to a value that satisfies the schema type: \"string\"",
          () -> cast(schema, bad));
    }
  }

  /** ensure() turns absent values into the default. */
  public void testEnsure() {
    StringSchema schema = (StringSchema) Yup.string().ensure();
    eq("5", cast(schema, 5));
    eq("", cast(schema, null));
    eq("", cast(schema, U));
    eq("", cast(schema.defaultValue("foo"), null));
    eq("foo", cast(schema.defaultValue("foo"), U));
  }

  /** trim(), lowercase() and uppercase() transform. */
  public void testTransforms() {
    StringSchema schema = Yup.string();
    eq("3", cast(schema.trim(U), " 3  "));
    eq("hello john", cast(schema.lowercase(U), "HellO JohN"));
    eq("HELLO JOHN", cast(schema.uppercase(U), "HellO JohN"));
    eq(null, cast(((StringSchema) ((StringSchema) ((StringSchema) schema.nullable()).trim(U)).lowercase(U))
        .uppercase(U), null));
    eq("STRASSE", cast(schema.uppercase(U), "straße"));
    check(isValid(schema.trim(U), " 3  "), "trim to validate");
    check(!isValid(schema.trim(U), " 3  ", obj("strict", true)), "a strict trim to fail");
    check(!isValid(schema.lowercase(U), "HellO JohN", obj("strict", true)), "a strict lowercase to fail");
  }

  /** Defaults and type checks. */
  public void testDefaultAndType() {
    eq("my_value", Yup.string().defaultValue("my_value").required(U).getDefault());
    StringSchema inst = Yup.string();
    eq(true, inst.isType("5"));
    eq(false, inst.isType(false));
    eq(false, inst.isType(null));
    eq(true, inst.nullable().isType(null));
  }

  /** required(), min() and strict(). */
  public void testValidate() {
    Schema inst = ((StringSchema) Yup.string().required(U)).min(4, U).strict(U);
    check(!isValid(Yup.string().strict(U), null), "null to fail");
    check(isValid(Yup.string().strict(U).nullable(), null), "nullable null to pass");
    check(isValid(inst, "hello"), "hello to pass");
    check(!isValid(inst, "hel"), "hel to fail");
    eq(new JsArray(java.util.List.of("this is a required field")), errors(inst, "", U));
    Schema notRequired = Yup.string().required(U).notRequired();
    check(isValid(notRequired, U) && isValid(notRequired, ""), "notRequired to allow empty values");
  }

  /** matches(), including the g and y flags and empty strings. */
  public void testMatches() {
    Schema v = Yup.string().matches(new JsRegExp("(hi|bye)"), "A message");
    check(isValid(v, "hi") && isValid(v, "bye") && !isValid(v, "nope"), "matches to test the pattern");
    Schema sticky = Yup.string().matches(new JsRegExp("hi", "gy"), U);
    check(isValid(sticky, "hi") && isValid(sticky, "hi"), "g/y flags not to carry state");
    check(!isValid(Yup.string().matches(new JsRegExp("(hi|bye)"), U), ""), "an empty string to fail");
    check(isValid(Yup.string().matches(new JsRegExp("(hi|bye)"), obj("excludeEmptyString", true)), ""),
        "excludeEmptyString to pass empty strings");
    check(isValid(Yup.string().email(U), ""), "email to pass empty strings");
  }

  /** min(), max() and length(), with references. */
  public void testLengths() {
    check(isValid(Yup.string().min(5, U), "hiiofff") && !isValid(Yup.string().min(5, U), "big"), "min");
    check(!isValid(Yup.string().min(5, U), null) && isValid(Yup.string().min(5, U).nullable(), null), "min null");
    Schema minRef = Yup.object(obj("len", Yup.number(), "name", Yup.string().min(Yup.ref("len"), U)));
    check(!isValid(minRef, obj("len", 10, "name", "john")), "min with a ref");
    check(isValid(Yup.string().max(5, U), "adgf") && !isValid(Yup.string().max(5, U), "bigdfdsfsdf"), "max");
    Schema maxRef = Yup.object(obj("len", Yup.number(), "name", Yup.string().max(Yup.ref("len"), U)));
    check(!isValid(maxRef, obj("len", 3, "name", "john")), "max with a ref");
    check(isValid(Yup.string().length(5, U), "exact") && !isValid(Yup.string().length(5, U), "sml"), "length");
    check(isValid(Yup.string().length(2, U), "😀"), "length to count UTF-16 code units");
  }

  /** url() and uuid(). */
  public void testUrlAndUuid() {
    Schema url = Yup.string().url(U);
    check(isValid(url, "//www.github.com/") && isValid(url, "https://www.github.com/")
        && !isValid(url, "this is not a url"), "url");
    Schema uuid = Yup.string().uuid(U);
    check(isValid(uuid, "0c40428c-d88d-4ff0-a5dc-a6755cb4f4d1") && !isValid(uuid, "42c4a747-3e3e-zzzz-af30-469cfb9c1913")
        && !isValid(uuid, ""), "uuid");
  }

  /** datetime() with its options and messages. */
  public void testDatetime() {
    Schema v = Yup.string().datetime(U);
    for (String good : new String[] {"2023-01-09T12:34:56Z", "1977-00-28T12:34:56.0Z", "4444-12-31T12:34:56.0000Z"}) {
      check(isValid(v, good), good + " to be valid");
    }
    for (String bad : new String[] {"2010-04-10T14:06:14+00:00", "2023-08-16T12:34:56", "1970-01-01 00:00:00Z",
        "1970-01-01T00:00:00,000Z", "2023-01-09T12:34:56.Z", "19700101"}) {
      check(!isValid(v, bad), bad + " to be invalid");
    }
    Schema offset = Yup.string().datetime(obj("allowOffset", true));
    check(isValid(offset, "1970-01-01T00:00:00+0630") && isValid(offset, "2010-04-10T14:06:14+00:00"), "offsets");
    Schema precise = Yup.string().datetime(obj("precision", 4));
    check(isValid(precise, "2023-01-09T12:34:56.0000Z") && !isValid(precise, "2023-01-09T12:34:56.000Z"), "precision");
    eq(new JsArray(java.util.List.of("this must be a valid ISO date-time")), errors(v, "asdf", U));
    eq(new JsArray(java.util.List.of("this must be a valid ISO date-time with UTC \"Z\" timezone")),
        errors(v, "2010-04-10T14:06:14+00:00", U));
    eq(new JsArray(java.util.List.of(
        "this must be a valid ISO date-time with a sub-second precision of exactly 2 digits")),
        errors(Yup.string().datetime(obj("precision", 2)), "2023-01-09T12:34:56Z", U));
    eq(new JsArray(java.util.List.of("hello")), errors(Yup.string().datetime(obj("message", "hello")), "asdf", U));
  }

  /** notOneOf with a reference, abortEarly off: the internal notOneOf test fails before required(). */
  public void testRefsInNotOneOf() {
    Schema inst = Yup.object(obj("email", Yup.string().required("Email Required"), "password",
        Yup.string().required("Password Required").notOneOf(new JsArray(java.util.List.of(Yup.ref("email"))), U)));
    eq(new JsArray(java.util.List.of("Email Required", "password must not be one of the following values: Ref(email)")),
        errors(inst, obj("email", "", "password", ""), obj("abortEarly", false)));
  }
}
