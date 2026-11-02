/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup); ported from test/array.ts, test/tuple.ts and test/lazy.ts.
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

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsArray;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;

/** Array, tuple and lazy schemas. */
public class CollectionsTest {
  /** Array casting and defaults. */
  public void testArrayCasting() {
    eq(arr(2, 3, 5, 6), cast(Yup.array().json(), "[2,3,5,6]"));
    eq("asfasf", cast(Yup.array(), "asfasf", obj("assert", false)));
    eq(arr(4, 5), cast(Yup.array().of(Yup.number()), arr("4", "5")));
    eq(arr("4", "5", "false"), cast(Yup.array().of(Yup.string()), arr("4", 5, false)));
    eq(U, Yup.array().getDefault());
    eq(arr(1, 2, 3), Yup.array().defaultValue(constant(arr(1, 2, 3))).getDefault());
    eq(true, Yup.array().isType(new JsArray()));
    eq(false, Yup.array().isType(new JsObject()));
    eq(arr(obj("name", "john")), cast(Yup.array(Yup.object(obj("name", Yup.string()))), arr(obj("id", 1, "name", "john")),
        obj("stripUnknown", true)));
  }

  /** Array validation: presence, lengths, inner schemas, abortEarly and sparse arrays. */
  public void testArrayValidation() {
    check(!isValid(Yup.array().required(U), U) && !isValid(Yup.array(), null) && isValid(Yup.array().nullable(), null),
        "presence");
    check(!isValid(Yup.array().length(2, U), arr(1, 2, 3)) && isValid(Yup.array().length(3, U), arr(1, 2, 3)), "length");
    check(isValid(Yup.array().max(4, U).max(10, U), arr(0, 0, 0, 0, 0)), "max to replace earlier max");
    ArraySchema inner = Yup.array().of(Yup.number().max(5, U));
    check(!isValid(inner, arr("gg", 3)) && !isValid(inner, arr(7, 3)), "inner validation");
    eq(arr(4, 3), validate(inner, arr("4", 3)));
    Schema abort = Yup.array().of(Yup.object(obj("str", Yup.string().required(U))))
        .test("name", "oops", constant(false));
    eq(arr("oops"), errors(abort, arr(obj("str", "")), U));
    eq(arr("[0].str is a required field", "oops"), errors(abort, arr(obj("str", "")), obj("abortEarly", false)));
    JsArray sparse = JsArray.ofLength(2);
    sparse.set(1, 1.0);
    JsArray validated = (JsArray) validate(Yup.array().of(Yup.number()), sparse);
    check(!validated.has(0) && validated.has(1), "sparse arrays to stay sparse");
    check(!isValid(Yup.array().of(Yup.number().required(U)), sparse), "empty slots to be validated");
  }

  /** compact() and ensure(). */
  public void testArrayTransforms() {
    JsArray values = arr("", 1, 0, 4, false, null);
    eq(arr(1, 4), cast(Yup.array().compact(U), values));
    eq(arr("", 1, 0, 4, false), cast(Yup.array().compact((JsFunction) (self, args) -> Js.isNullish(args[0])), values));
    Schema ensured = Yup.array().ensure();
    JsArray a = arr(1, 4);
    check(cast(ensured, a) == a, "an array to be returned as it is");
    eq(new JsArray(), cast(ensured, null));
    eq(new JsArray(), cast(ensured.nullable(), null));
    eq(arr(1), cast(ensured, 1));
  }

  /** concat() of arrays and validateAt() through them. */
  public void testArrayConcat() {
    ArraySchema merged = (ArraySchema) Yup.array(Yup.number()).concat(Yup.array(Yup.number().required(U)));
    eq("number", ((Schema) merged.innerType()).type());
    throwsError("ValidationError", "[0] is a required field", () -> merged.validateAt("[0]", U, U).await());
    eq("[object Error]", "[object " + Js.toStringTag(new ValidationError("")) + "]");
  }

  /** Tuples: casting, lengths and labels. */
  public void testTuple() {
    TupleSchema numbers = Yup.tuple(arr(Yup.number(), Yup.number()));
    eq("asfasf", cast(numbers, "asfasf", obj("assert", false)));
    eq(arr(4, 5), cast(numbers, arr("4", "5")));
    eq(arr(1, 2, 3), Yup.tuple(arr(Yup.number(), Yup.number(), Yup.number())).defaultValue(constant(arr(1, 2, 3)))
        .getDefault());
    eq(true, Yup.tuple(arr(Yup.number())).isType(arr(1)));
    check(isValid(Yup.tuple(arr(Yup.number().defined(U))), U), "undefined to be allowed");
    TupleSchema inst = Yup.tuple(arr(Yup.number().max(5, U), Yup.string()));
    check(!isValid(inst, arr("gg", "any")) && !isValid(inst, arr(7, 3)), "element validation");
    eq(arr(4, "3"), validate(inst, arr("4", 3)));
    TupleSchema labelled = Yup.tuple(arr(Yup.string().label("name"),
        ((NumberSchema) Yup.number().positive(U)).integer(U).label("age")));
    eq(arr("age must be a positive number"), errors(labelled, arr("James", -24.55), U));
    TupleSchema pair = Yup.tuple(arr(Yup.string().label("name"), Yup.number().label("age")));
    eq(arr("this tuple value has too few items, expected a length of 2 but got 1 for value: `[\n  \"\\\"James\\\"\"\n]`"),
        errors(pair, arr("James"), U));
    check(Js.toJsString(errors(pair, arr("James", 2, 4), U)).startsWith(
        "this tuple value has too many items, expected a length of 2 but got 3"), "a too many items message");
  }

  /** Lazy schemas: builders, meta and errors thrown by the builder. */
  public void testLazy() {
    throwsError("TypeError", "lazy() functions must return a valid schema",
        () -> Yup.lazy(constant(U)).validateSync(U, U));
    Object[] seen = {null, null};
    Lazy recorder = Yup.lazy((JsFunction) (self, args) -> {
      seen[0] = args[0];
      seen[1] = args[1];
      return Yup.mixed();
    });
    JsObject options = obj("context", obj("a", 1));
    recorder.validate(1.0, options).await();
    eq(1.0, seen[0]);
    check(seen[1] == options, "the options to be passed as they are");
    Lazy meta = Yup.lazy(constant(Yup.mixed())).meta(obj("a", 1));
    eq(obj("a", 1), meta.meta());
    eq(obj("a", 1, "added", true), meta.meta(obj("added", true)).meta());
    Lazy validationError = Yup.lazy((JsFunction) (self, args) -> {
      throw new ValidationError("oops");
    });
    throwsError("ValidationError", "oops", () -> validationError.validate(1.0, U).await());
    eq(false, validationError.isValid(1.0, U).await());
    throwsError("ValidationError", "oops", () -> validationError.validateSync(1.0, U));
    Lazy plainError = Yup.lazy((JsFunction) (self, args) -> {
      throw com.github.jquense.yup.js.JsError.error("error");
    });
    throwsError("Error", "error", () -> plainError.validate(1.0, U));
    throwsError("Error", "error", () -> plainError.isValid(1.0, U));
  }
}
