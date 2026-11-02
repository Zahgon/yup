/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 * Tweaked from https://github.com/Kelin2025/nanoclone (MIT licensed).
 */
package com.github.jquense.yup.util;

import com.github.jquense.yup.Yup;
import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsArray;
import com.github.jquense.yup.js.JsDate;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsRegExp;
import java.util.IdentityHashMap;
import java.util.Map;

/** Deep copies of the values a schema keeps (its spec and defaults); schemas themselves are shared. */
public final class CloneDeep {
  private CloneDeep() {}

  /**
   * Copies a value deeply: dates, regular expressions, arrays (empty slots become {@code undefined}) and plain objects
   * are copied, schemas and primitives are returned as they are.
   *
   * @param src any value
   * @return the copy
   */
  public static Object clone(Object src) {
    return clone(src, new IdentityHashMap<>());
  }

  private static Object clone(Object src, Map<Object, Object> seen) {
    if (Yup.isSchema(src) || !Js.truthy(src) || !Js.typeOf(src).equals("object")) {
      return src;
    }
    if (seen.containsKey(src)) {
      return seen.get(src);
    }
    if (src instanceof JsDate date) {
      JsDate copy = new JsDate(date.getTime());
      seen.put(src, copy);
      return copy;
    }
    if (src instanceof JsRegExp regex) {
      JsRegExp copy = regex.copy();
      seen.put(src, copy);
      return copy;
    }
    if (src instanceof JsArray array) {
      JsArray copy = JsArray.ofLength(array.length());
      seen.put(src, copy);
      for (int i = 0; i < array.length(); i++) {
        copy.set(i, clone(array.get(i), seen));
      }
      return copy;
    }
    if (src instanceof JsObject object) {
      if (object.isNullPrototype()) {
        throw JsError.typeError("Cannot convert object to primitive value");
      }
      JsObject copy = new JsObject();
      seen.put(src, copy);
      for (String key : object.keys()) {
        copy.put(key, clone(object.get(key), seen));
      }
      return copy;
    }
    JsObject copy = new JsObject();
    seen.put(src, copy);
    for (String key : Js.keys(src)) {
      copy.put(key, clone(Js.get(src, key), seen));
    }
    return copy;
  }
}
