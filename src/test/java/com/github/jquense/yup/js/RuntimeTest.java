/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

import static com.github.jquense.yup.Support.arr;
import static com.github.jquense.yup.Support.check;
import static com.github.jquense.yup.Support.eq;
import static com.github.jquense.yup.Support.obj;
import static com.github.jquense.yup.Support.throwsError;

/** The JavaScript semantics the library relies on. */
public class RuntimeTest {
  /** Number::toString picks the shortest round-tripping digits and JavaScript's notation. */
  public void testNumberToString() {
    eq("1e+21", JsNumbers.toString(1e21));
    eq("1e-7", JsNumbers.toString(1e-7));
    eq("123456789012345680000", JsNumbers.toString(123456789012345680000.0));
    eq("0.30000000000000004", JsNumbers.toString(0.1 + 0.2));
    eq("5e-324", JsNumbers.toString(Double.MIN_VALUE));
    eq("1.7976931348623157e+308", JsNumbers.toString(Double.MAX_VALUE));
    eq("0", JsNumbers.toString(-0.0));
    eq("0.0000015", JsNumbers.toString(1.5e-6));
    eq("100", JsNumbers.toString(100));
  }

  /** StringToNumber and parseFloat. */
  public void testStringToNumber() {
    eq(31.0, JsNumbers.stringToNumber("0x1F"));
    eq(5.0, JsNumbers.stringToNumber("0b101"));
    eq(0.0, JsNumbers.stringToNumber(" \u00a0 "));
    eq(Double.NaN, JsNumbers.stringToNumber("-0x10"));
    eq(Double.NaN, JsNumbers.stringToNumber("1_000"));
    eq(Double.POSITIVE_INFINITY, JsNumbers.stringToNumber("+Infinity"));
    eq(5.0, JsNumbers.stringToNumber("5."));
    eq(1.0, JsNumbers.parseFloat("1,2"));
    eq(Double.NaN, JsNumbers.parseFloat(".e1"));
    eq(-2.0, JsNumbers.round(-2.5));
    check(1 / JsNumbers.round(-0.4) < 0, "Math.round(-0.4) to be -0");
  }

  /** Equality and relational operators with their conversions. */
  public void testOperators() {
    check(Js.looseEquals("1", 1.0) && Js.looseEquals(null, Js.UNDEFINED) && !Js.looseEquals(null, 0.0), "==");
    check(Js.looseEquals(arr(1), 1.0) && Js.looseEquals(new JsDate(0), "Thu Jan 01 1970 00:00:00 GMT+0000 "
        + "(Coordinated Universal Time)"), "== with objects");
    check(Js.lt("10", 9.0) == false && Js.lt("10", "9") && Js.ge(null, 0.0) && !Js.ge(Js.UNDEFINED, 0.0), "< and >=");
    check(Js.sameValueZero(Double.NaN, Double.NaN) && !Js.strictEquals(Double.NaN, Double.NaN), "NaN equality");
    eq("x[object Object]", Js.add("x", new JsObject()));
    throwsError("TypeError", "Cannot convert a Symbol value to a string", () -> Js.toJsString(new JsSymbol("s")));
  }

  /** JSON.parse errors carry V8's messages; JSON.stringify escapes lone surrogates. */
  public void testJson() {
    throwsError("SyntaxError", "Unexpected token 'o', \"nope\" is not valid JSON", () -> JsonParser.parse("nope"));
    throwsError("SyntaxError", "Unexpected end of JSON input", () -> JsonParser.parse("[1,"));
    throwsError("SyntaxError", "Expected property name or '}' in JSON at position 1 (line 1 column 2)",
        () -> JsonParser.parse("{"));
    throwsError("SyntaxError", "Unexpected non-whitespace character after JSON at position 2 (line 1 column 3)",
        () -> JsonParser.parse("1 2"));
    eq(obj("1", "b", "a", "x"), JsonParser.parse("{\"a\":\"x\",\"1\":\"b\"}"));
    eq("\"\\ud800\u2028\"", JsonWriter.quote("\ud800\u2028"));
    eq("[\n  1,\n  {\n    \"a\": []\n  }\n]", JsonWriter.stringify(arr(1, obj("a", arr())), null, "  "));
  }

  /** Regular expressions: V8's errors, code-unit matching, case folding and lookbehind. */
  public void testRegExp() {
    throwsError("SyntaxError", "Invalid regular expression: /(/g: Unterminated group", () -> new JsRegExp("(", "g"));
    throwsError("SyntaxError", "Invalid flags supplied to RegExp constructor 'gg'", () -> new JsRegExp("a", "gg"));
    throwsError("SyntaxError", "Invalid regular expression: /(?<=a)*/: Invalid quantifier", () -> new JsRegExp("(?<=a)*"));
    check(!new JsRegExp("^.$").test("😀"), ". to match one code unit");
    check(!new JsRegExp("ſ", "i").test("s") && !new JsRegExp("[a-z]", "i").test("\u212a"), "Canonicalize");
    check(new JsRegExp("(?<=(\\d+)(\\d+))$").test("1053") && new JsRegExp("(a)|\\1b").test("b"), "lookbehind, backrefs");
    check(new JsRegExp("^\\477$").test("'7") && new JsRegExp("\\c1").test("\\c1"), "Annex B escapes");
    eq("/a\\/b\\n[/]\\//m", new JsRegExp("a/b\n[/]\\/", "m").toString());
    eq("/(?:)/gy", new JsRegExp("", "yg").toString());
    JsRegExp global = new JsRegExp("a", "g");
    check(global.test("aa") && global.test("aa") && !global.test("aa"), "lastIndex to carry over");
    eq(java.util.List.of("a", "b", "0", "\"c\"", "", "d", ""),
        new JsRegExp("[^.^\\]^[]+|(?=\\[\\]|\\.\\.)", "g").matchAll("a.b[0][\"c\"]..d[]"));
  }

  /** Dates: V8's legacy parser and the date strings. */
  public void testDates() {
    eq("2001-01-05T00:00:00.000Z", JsDate.parse("Jan 5").toISOString());
    eq("2001-05-06T00:00:00.000Z", JsDate.parse("5/6").toISOString());
    eq("2020-01-01T00:00:00.000Z", JsDate.parse("2020-1-1").toISOString());
    eq("0123-01-01T00:00:00.000Z", JsDate.parse("123").toISOString());
    eq("1999-12-31T22:00:00.000Z", JsDate.parse("Sat, 01-Jan-2000 00:00:00 GMT+0200").toISOString());
    check(JsDate.parse("+275760-09-13T00:00:00.001Z").isInvalid(), "an out-of-range date");
    eq("Fri Jan 01 -0001 00:00:00 GMT+0000 (Coordinated Universal Time)", new JsDate(-62198755200000.0).toString());
    eq("-001199-02-15T14:13:20.000Z", new JsDate(-1e14).toISOString());
  }

  /** Case mapping at Unicode 16, with the Final_Sigma condition. */
  public void testCase() {
    eq("SS", UnicodeCase.toUpperCase("ß"));
    eq("i\u0307", UnicodeCase.toLowerCase("İ"));
    eq("σας", UnicodeCase.toLowerCase("ΣΑΣ"));
    eq("\ua7dc", UnicodeCase.toUpperCase("\u019b"));
    eq("\ud803\udd70", UnicodeCase.toLowerCase("\ud803\udd50"));
    eq("\ud803\udcd4ς", UnicodeCase.toLowerCase("\ud803\udcd4Σ"));
    eq("K", String.valueOf(UnicodeCase.canonicalize('k')));
    eq("\u212a", String.valueOf(UnicodeCase.canonicalize('\u212a')));
    eq("\u017f", String.valueOf(UnicodeCase.canonicalize('\u017f')));
  }

  /** Promise jobs run in order once drained. */
  public void testPromises() {
    StringBuilder order = new StringBuilder();
    JsPromise.resolved(null).then(v -> order.append('a'), null).then(v -> order.append('c'), null);
    JsPromise.resolved(null).then(v -> order.append('b'), null);
    check(order.length() == 0, "nothing to run before draining");
    JsPromise.drain();
    eq("abc", order.toString());
    throwsError("TypeError", "late", () -> JsPromise.rejected(JsError.typeError("late")).await());
  }

  /** Objects with a function then member are thenables: adopted through then, which a promise calls one job later. */
  public void testThenables() {
    JsObject throwing = obj("then", (JsFunction) (self, args) -> {
      throw JsError.rangeError("from then");
    });
    check(JsPromise.isThenable(throwing) && !JsPromise.isThenable(obj("then", 1)), "thenable detection");
    throwsError("RangeError", "from then", () -> JsPromise.resolved(throwing).await());
    JsObject fulfilling = obj("then", (JsFunction) (self, args) -> ((JsFunction) args[0]).call(Js.UNDEFINED, 7.0));
    eq(7.0, JsPromise.resolved(fulfilling).await());
    JsObject silent = obj("then", (JsFunction) (self, args) -> Js.UNDEFINED);
    boolean pending = false;
    try {
      JsPromise.resolved(silent).await();
    } catch (JsPromise.NeverSettled e) {
      pending = true;
    }
    check(pending, "a thenable that never calls back never settles");
  }
}
