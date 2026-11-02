/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * The JavaScript operations the library relies on, over the Java representation of JavaScript values: {@code null}
 * for null, {@link #UNDEFINED}, {@link Boolean}, {@link Double} (every number), {@link String} (UTF-16),
 * {@link BigInteger} (bigint), {@link JsSymbol}, and objects ({@link JsObject}, {@link JsArray}, {@link JsDate},
 * {@link JsRegExp}, {@link JsError}, {@link JsFunction} and the library's own objects).
 */
public final class Js {
  /** {@code undefined}. */
  public static final Object UNDEFINED = Undefined.INSTANCE;

  private Js() {}

  /**
   * A Java value as a JavaScript value: Java numbers other than {@link Double} and {@link BigInteger} become numbers.
   *
   * @param value a Java or JavaScript value
   * @return the JavaScript value
   */
  public static Object fromJava(Object value) {
    if (value instanceof Number number && !(value instanceof Double) && !(value instanceof BigInteger)) {
      return number.doubleValue();
    }
    return value;
  }

  /**
   * @param value any value
   * @return whether it is {@code undefined} or {@code null} ({@code value == null})
   */
  public static boolean isNullish(Object value) {
    return value == null || value == UNDEFINED;
  }

  /**
   * @param value any value
   * @return whether it is an object (not a primitive)
   */
  public static boolean isObject(Object value) {
    return !(value == null || value == UNDEFINED || value instanceof Boolean || value instanceof Double
        || value instanceof String || value instanceof BigInteger || value instanceof JsSymbol);
  }

  /**
   * The {@code typeof} operator.
   *
   * @param value any value
   * @return its type name
   */
  public static String typeOf(Object value) {
    if (value == UNDEFINED) {
      return "undefined";
    }
    if (value instanceof Boolean) {
      return "boolean";
    }
    if (value instanceof Double) {
      return "number";
    }
    if (value instanceof String) {
      return "string";
    }
    if (value instanceof BigInteger) {
      return "bigint";
    }
    if (value instanceof JsSymbol) {
      return "symbol";
    }
    if (value instanceof JsFunction) {
      return "function";
    }
    return "object";
  }

  /**
   * {@code ToBoolean} (truthiness).
   *
   * @param value any value
   * @return whether it is truthy
   */
  public static boolean truthy(Object value) {
    if (value == null || value == UNDEFINED) {
      return false;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    if (value instanceof Double d) {
      return !(d == 0 || Double.isNaN(d));
    }
    if (value instanceof String s) {
      return !s.isEmpty();
    }
    if (value instanceof BigInteger b) {
      return b.signum() != 0;
    }
    return true;
  }

  /**
   * {@code Object.prototype.toString}'s tag.
   *
   * @param value any value
   * @return the tag, e.g. {@code Object}, {@code Array}, {@code Date}
   */
  public static String toStringTag(Object value) {
    if (value == UNDEFINED) {
      return "Undefined";
    }
    if (value == null) {
      return "Null";
    }
    if (value instanceof Boolean) {
      return "Boolean";
    }
    if (value instanceof Double) {
      return "Number";
    }
    if (value instanceof String) {
      return "String";
    }
    if (value instanceof BigInteger) {
      return "BigInt";
    }
    if (value instanceof JsSymbol) {
      return "Symbol";
    }
    if (value instanceof JsArray) {
      return "Array";
    }
    if (value instanceof JsFunction) {
      return "Function";
    }
    if (value instanceof JsDate) {
      return "Date";
    }
    if (value instanceof JsRegExp) {
      return "RegExp";
    }
    if (value instanceof JsError) {
      return "Error";
    }
    return "Object";
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Conversions

  /**
   * {@code ToPrimitive}.
   *
   * @param value any value
   * @param hint {@code "default"}, {@code "number"} or {@code "string"}
   * @return a primitive
   */
  public static Object toPrimitive(Object value, String hint) {
    if (!isObject(value)) {
      return value;
    }
    boolean stringFirst = hint.equals("string") || (hint.equals("default") && value instanceof JsDate);
    for (String name : stringFirst ? new String[] {"toString", "valueOf"} : new String[] {"valueOf", "toString"}) {
      JsFunction method = method(value, name);
      if (method != null) {
        Object result = method.call(value);
        if (!isObject(result)) {
          return result;
        }
      }
    }
    throw JsError.typeError("Cannot convert object to primitive value");
  }

  // The toString/valueOf method an object has: its own function member, or the built-in of its kind.
  private static JsFunction method(Object value, String name) {
    if (value instanceof JsObject object) {
      if (object.has(name)) {
        return object.get(name) instanceof JsFunction f ? f : null;
      }
      if (object.isNullPrototype()) {
        return null;
      }
    }
    if (name.equals("valueOf")) {
      return value instanceof JsDate date ? (self, args) -> date.getTime() : (self, args) -> value;
    }
    return (self, args) -> builtinToString(value);
  }

  private static String builtinToString(Object value) {
    if (value instanceof JsArray array) {
      return join(array, ",");
    }
    if (value instanceof JsDate || value instanceof JsRegExp) {
      return value.toString();
    }
    if (value instanceof JsError error) {
      return error.errorToString();
    }
    if (value instanceof JsFunction function) {
      return "function " + function.name() + "() { [native code] }";
    }
    if (value instanceof JsToString custom) {
      return custom.jsToString();
    }
    return "[object " + toStringTag(value) + "]";
  }

  /**
   * {@code value.toString()}: the value's own or built-in {@code toString}, called on it; {@code undefined} (the value
   * of the missing method) for an object without one.
   *
   * @param value a value that is not {@code undefined} or {@code null}
   * @return the result, whatever its type
   */
  public static Object callToString(Object value) {
    if (value instanceof JsSymbol symbol) {
      return symbol.descriptiveString();
    }
    if (!isObject(value)) {
      return toJsString(value);
    }
    if (value instanceof JsObject object && (object.has("toString") || object.isNullPrototype())) {
      Object method = object.get("toString");
      if (!(method instanceof JsFunction function)) {
        throw JsError.typeError("value.toString is not a function");
      }
      return function.call(value);
    }
    return builtinToString(value);
  }

  /**
   * {@code Array.prototype.join}: {@code undefined} and {@code null} elements are empty.
   *
   * @param array an array
   * @param separator the separator
   * @return the joined string
   */
  public static String join(JsArray array, String separator) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < array.length(); i++) {
      if (i > 0) {
        out.append(separator);
      }
      Object element = array.get(i);
      if (!isNullish(element)) {
        out.append(toJsString(element));
      }
    }
    return out.toString();
  }

  /**
   * {@code ToNumber}.
   *
   * @param value any value
   * @return the number
   */
  public static double toNumber(Object value) {
    if (value == UNDEFINED) {
      return Double.NaN;
    }
    if (value == null) {
      return 0;
    }
    if (value instanceof Boolean b) {
      return b ? 1 : 0;
    }
    if (value instanceof Double d) {
      return d;
    }
    if (value instanceof String s) {
      return JsNumbers.stringToNumber(s);
    }
    if (value instanceof BigInteger) {
      throw JsError.typeError("Cannot convert a BigInt value to a number");
    }
    if (value instanceof JsSymbol) {
      throw JsError.typeError("Cannot convert a Symbol value to a number");
    }
    return toNumber(toPrimitive(value, "number"));
  }

  /**
   * {@code ToNumeric}: a bigint stays a bigint, anything else becomes a number.
   *
   * @param value any value
   * @return a {@link Double} or a {@link BigInteger}
   */
  public static Object toNumeric(Object value) {
    Object primitive = toPrimitive(value, "number");
    return primitive instanceof BigInteger ? primitive : (Object) toNumber(primitive);
  }

  /**
   * {@code ToString}.
   *
   * @param value any value
   * @return the string
   */
  public static String toJsString(Object value) {
    if (value == UNDEFINED) {
      return "undefined";
    }
    if (value == null) {
      return "null";
    }
    if (value instanceof Boolean b) {
      return b ? "true" : "false";
    }
    if (value instanceof Double d) {
      return JsNumbers.toString(d);
    }
    if (value instanceof String s) {
      return s;
    }
    if (value instanceof BigInteger b) {
      return b.toString();
    }
    if (value instanceof JsSymbol) {
      throw JsError.typeError("Cannot convert a Symbol value to a string");
    }
    return toJsString(toPrimitive(value, "string"));
  }

  /**
   * The {@code String(value)} function: like {@code ToString}, but a symbol becomes {@code Symbol(description)}.
   *
   * @param value any value
   * @return the string
   */
  public static String string(Object value) {
    return value instanceof JsSymbol symbol ? symbol.descriptiveString() : toJsString(value);
  }

  /**
   * A string for messages about a value, which never throws.
   *
   * @param value any value
   * @return a description
   */
  public static String displayString(Object value) {
    try {
      return string(value);
    } catch (RuntimeException e) {
      return "[object " + toStringTag(value) + "]";
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Operators

  /**
   * {@code a === b}.
   *
   * @param a a value
   * @param b a value
   * @return whether they are strictly equal
   */
  public static boolean strictEquals(Object a, Object b) {
    if (a instanceof Double x && b instanceof Double y) {
      return x.doubleValue() == y.doubleValue();
    }
    if (a instanceof String || a instanceof Boolean || a instanceof BigInteger) {
      return a.equals(b);
    }
    return a == b;
  }

  /**
   * {@code SameValueZero} ({@code Array.prototype.includes}, {@code Set}): like {@code ===}, but NaN equals NaN.
   *
   * @param a a value
   * @param b a value
   * @return whether they are the same value
   */
  public static boolean sameValueZero(Object a, Object b) {
    if (a instanceof Double x && b instanceof Double y) {
      return x.doubleValue() == y.doubleValue() || (Double.isNaN(x) && Double.isNaN(y));
    }
    return strictEquals(a, b);
  }

  /**
   * {@code a == b} ({@code IsLooselyEqual}).
   *
   * @param a a value
   * @param b a value
   * @return whether they are loosely equal
   */
  public static boolean looseEquals(Object a, Object b) {
    if (sameType(a, b)) {
      return strictEquals(a, b);
    }
    if (isNullish(a) && isNullish(b)) {
      return true;
    }
    if (a instanceof Double && b instanceof String s) {
      return looseEquals(a, JsNumbers.stringToNumber(s));
    }
    if (a instanceof String s && b instanceof Double) {
      return looseEquals(JsNumbers.stringToNumber(s), b);
    }
    if (a instanceof BigInteger && b instanceof String s) {
      BigInteger n = stringToBigInt(s);
      return n != null && looseEquals(a, n);
    }
    if (a instanceof String && b instanceof BigInteger) {
      return looseEquals(b, a);
    }
    if (a instanceof Boolean) {
      return looseEquals(toNumber(a), b);
    }
    if (b instanceof Boolean) {
      return looseEquals(a, toNumber(b));
    }
    boolean primitiveA = a instanceof Double || a instanceof String || a instanceof BigInteger || a instanceof JsSymbol;
    boolean primitiveB = b instanceof Double || b instanceof String || b instanceof BigInteger || b instanceof JsSymbol;
    if (primitiveA && isObject(b)) {
      return looseEquals(a, toPrimitive(b, "default"));
    }
    if (isObject(a) && primitiveB) {
      return looseEquals(toPrimitive(a, "default"), b);
    }
    if (a instanceof BigInteger x && b instanceof Double y) {
      return compareBigIntToNumber(x, y) == 0;
    }
    if (a instanceof Double x && b instanceof BigInteger y) {
      return compareBigIntToNumber(y, x) == 0;
    }
    return false;
  }

  private static boolean sameType(Object a, Object b) {
    if (isObject(a) && isObject(b)) {
      return true;
    }
    if (a == null || b == null) {
      return a == b;
    }
    if (a == UNDEFINED || b == UNDEFINED) {
      return a == b;
    }
    return a.getClass() == b.getClass();
  }

  // StringToBigInt, null for a string that is not an integer literal.
  private static BigInteger stringToBigInt(String s) {
    String t = JsNumbers.trim(s);
    if (t.isEmpty()) {
      return BigInteger.ZERO;
    }
    try {
      if (t.length() > 2 && t.charAt(0) == '0' && "xXoObB".indexOf(t.charAt(1)) >= 0) {
        int radix = switch (Character.toLowerCase(t.charAt(1))) {
          case 'x' -> 16;
          case 'o' -> 8;
          default -> 2;
        };
        String digits = t.substring(2);
        return digits.chars().allMatch(c -> c < 128 && Character.digit(c, radix) >= 0)
            ? new BigInteger(digits, radix)
            : null;
      }
      int start = t.charAt(0) == '+' || t.charAt(0) == '-' ? 1 : 0;
      if (start == t.length() || !t.substring(start).chars().allMatch(c -> c >= '0' && c <= '9')) {
        return null;
      }
      return new BigInteger(t.charAt(0) == '+' ? t.substring(1) : t);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static int compareBigIntToNumber(BigInteger x, double y) {
    if (Double.isNaN(y)) {
      return 2;
    }
    if (Double.isInfinite(y)) {
      return y > 0 ? -1 : 1;
    }
    return new BigDecimal(x).compareTo(new BigDecimal(y));
  }

  // IsLessThan: TRUE, FALSE or null for undefined.
  private static Boolean lessThan(Object x, Object y, boolean leftFirst) {
    Object px;
    Object py;
    if (leftFirst) {
      px = toPrimitive(x, "number");
      py = toPrimitive(y, "number");
    } else {
      py = toPrimitive(y, "number");
      px = toPrimitive(x, "number");
    }
    if (px instanceof String a && py instanceof String b) {
      return a.compareTo(b) < 0;
    }
    if (px instanceof BigInteger a && py instanceof String b) {
      BigInteger n = stringToBigInt(b);
      return n == null ? null : a.compareTo(n) < 0;
    }
    if (px instanceof String a && py instanceof BigInteger b) {
      BigInteger n = stringToBigInt(a);
      return n == null ? null : n.compareTo(b) < 0;
    }
    Object nx = px instanceof BigInteger ? px : (Object) toNumber(px);
    Object ny = py instanceof BigInteger ? py : (Object) toNumber(py);
    if (nx instanceof Double a && ny instanceof Double b) {
      if (Double.isNaN(a) || Double.isNaN(b)) {
        return null;
      }
      return a < b;
    }
    if (nx instanceof BigInteger a && ny instanceof BigInteger b) {
      return a.compareTo(b) < 0;
    }
    if (nx instanceof BigInteger a) {
      int order = compareBigIntToNumber(a, (Double) ny);
      return order == 2 ? null : order < 0;
    }
    int order = compareBigIntToNumber((BigInteger) ny, (Double) nx);
    return order == 2 ? null : order > 0;
  }

  /**
   * {@code a < b}.
   *
   * @param a a value
   * @param b a value
   * @return the result
   */
  public static boolean lt(Object a, Object b) {
    return Boolean.TRUE.equals(lessThan(a, b, true));
  }

  /**
   * {@code a > b}.
   *
   * @param a a value
   * @param b a value
   * @return the result
   */
  public static boolean gt(Object a, Object b) {
    return Boolean.TRUE.equals(lessThan(b, a, false));
  }

  /**
   * {@code a <= b}.
   *
   * @param a a value
   * @param b a value
   * @return the result
   */
  public static boolean le(Object a, Object b) {
    return Boolean.FALSE.equals(lessThan(b, a, false));
  }

  /**
   * {@code a >= b}.
   *
   * @param a a value
   * @param b a value
   * @return the result
   */
  public static boolean ge(Object a, Object b) {
    return Boolean.FALSE.equals(lessThan(a, b, true));
  }

  /**
   * {@code a + b}.
   *
   * @param a a value
   * @param b a value
   * @return the sum or concatenation
   */
  public static Object add(Object a, Object b) {
    Object pa = toPrimitive(a, "default");
    Object pb = toPrimitive(b, "default");
    if (pa instanceof String || pb instanceof String) {
      return toJsString(pa) + toJsString(pb);
    }
    Object na = toNumeric(pa);
    Object nb = toNumeric(pb);
    if (na instanceof BigInteger x && nb instanceof BigInteger y) {
      return x.add(y);
    }
    if (na instanceof BigInteger || nb instanceof BigInteger) {
      throw JsError.typeError("Cannot mix BigInt and other types, use explicit conversions");
    }
    return (Double) na + (Double) nb;
  }

  /**
   * {@code a * b}.
   *
   * @param a a value
   * @param b a value
   * @return the product
   */
  public static Object multiply(Object a, Object b) {
    Object na = toNumeric(a);
    Object nb = toNumeric(b);
    if (na instanceof BigInteger x && nb instanceof BigInteger y) {
      return x.multiply(y);
    }
    if (na instanceof BigInteger || nb instanceof BigInteger) {
      throw JsError.typeError("Cannot mix BigInt and other types, use explicit conversions");
    }
    return (Double) na * (Double) nb;
  }

  /**
   * {@code a | 0} ({@code ToInt32} of the numeric value).
   *
   * @param a a value
   * @return the 32-bit integer
   */
  public static Object toInt32(Object a) {
    Object n = toNumeric(a);
    if (n instanceof BigInteger) {
      throw JsError.typeError("Cannot mix BigInt and other types, use explicit conversions");
    }
    return JsNumbers.toInt32((Double) n);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Properties

  /**
   * Property access {@code target[key]} for own data properties: object members, array elements and {@code length},
   * string code units and {@code length}, and the properties of the library's objects.
   *
   * @param target any value
   * @param key a property name
   * @return the value, {@code undefined} when there is none
   */
  public static Object get(Object target, String key) {
    if (target instanceof JsObject object) {
      return object.get(key);
    }
    if (target instanceof JsArray array) {
      if (key.equals("length")) {
        return (double) array.length();
      }
      long index = JsObject.arrayIndex(key);
      return index >= 0 && index < array.length() ? array.get((int) index) : UNDEFINED;
    }
    if (target instanceof String s) {
      if (key.equals("length")) {
        return (double) s.length();
      }
      long index = JsObject.arrayIndex(key);
      return index >= 0 && index < s.length() ? String.valueOf(s.charAt((int) index)) : UNDEFINED;
    }
    if (target instanceof JsProperties properties) {
      return properties.getProperty(key);
    }
    return UNDEFINED;
  }

  /**
   * Property access that fails like JavaScript on {@code undefined} and {@code null}.
   *
   * @param target any value
   * @param key a property name
   * @return the value, {@code undefined} when there is none
   * @throws JsError a {@code TypeError} when the target is {@code undefined} or {@code null}
   */
  public static Object read(Object target, String key) {
    if (isNullish(target)) {
      throw JsError.typeError("Cannot read properties of " + (target == null ? "null" : "undefined") + " (reading '"
          + key + "')");
    }
    return get(target, key);
  }

  /**
   * The {@code in} operator for own data properties.
   *
   * @param key a property name
   * @param target an object
   * @return whether the object has the property
   * @throws JsError a {@code TypeError} when the target is not an object
   */
  public static boolean in(String key, Object target) {
    if (!isObject(target)) {
      throw JsError.typeError("Cannot use 'in' operator to search for '" + key + "' in " + displayString(target));
    }
    if (target instanceof JsObject object) {
      return object.has(key);
    }
    if (target instanceof JsArray array) {
      long index = JsObject.arrayIndex(key);
      return key.equals("length") || (index >= 0 && index < array.length() && array.has((int) index));
    }
    if (target instanceof JsProperties properties) {
      return properties.getProperty(key) != UNDEFINED;
    }
    return false;
  }

  /**
   * {@code Object.keys}.
   *
   * @param value any value
   * @return its own enumerable string keys, in property order
   * @throws JsError a {@code TypeError} for {@code undefined} and {@code null}
   */
  public static List<String> keys(Object value) {
    if (isNullish(value)) {
      throw JsError.typeError("Cannot convert undefined or null to object");
    }
    List<String> keys = new ArrayList<>();
    if (value instanceof JsObject object) {
      return object.keys();
    }
    if (value instanceof JsArray array) {
      for (int i = 0; i < array.length(); i++) {
        if (array.has(i)) {
          keys.add(Integer.toString(i));
        }
      }
    } else if (value instanceof String s) {
      for (int i = 0; i < s.length(); i++) {
        keys.add(Integer.toString(i));
      }
    }
    return keys;
  }

  /**
   * Object spread {@code { ...value }} into an object.
   *
   * @param target the object being built
   * @param value the spread value
   */
  public static void spread(JsObject target, Object value) {
    if (isNullish(value)) {
      return;
    }
    for (String key : keys(value)) {
      target.put(key, get(value, key));
    }
  }
}
