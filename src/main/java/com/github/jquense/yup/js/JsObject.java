/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A plain JavaScript object: own data properties kept in JavaScript property order, array indices in ascending
 * numeric order first, then the other names in insertion order.
 */
public class JsObject {
  private static final long MAX_ARRAY_INDEX = 4294967294L;

  private final TreeMap<Long, Object> indexed = new TreeMap<>();
  private final LinkedHashMap<String, Object> named = new LinkedHashMap<>();
  private final boolean nullPrototype;

  /** An empty object with {@code Object.prototype}. */
  public JsObject() {
    this(false);
  }

  /**
   * An empty object.
   *
   * @param nullPrototype whether it is made with {@code Object.create(null)}
   */
  public JsObject(boolean nullPrototype) {
    this.nullPrototype = nullPrototype;
  }

  /**
   * An object of the given name/value pairs.
   *
   * @param pairs alternating property names and values
   * @return the object
   */
  public static JsObject of(Object... pairs) {
    JsObject object = new JsObject();
    for (int i = 0; i < pairs.length; i += 2) {
      object.put((String) pairs[i], pairs[i + 1]);
    }
    return object;
  }

  /**
   * The array index a property name stands for.
   *
   * @param key a property name
   * @return its index, or -1 when it is not a canonical array index
   */
  public static long arrayIndex(String key) {
    int length = key.length();
    if (length == 0 || length > 10) {
      return -1;
    }
    if (key.charAt(0) == '0') {
      return length == 1 ? 0 : -1;
    }
    long value = 0;
    for (int i = 0; i < length; i++) {
      char c = key.charAt(i);
      if (c < '0' || c > '9') {
        return -1;
      }
      value = value * 10 + (c - '0');
    }
    return value <= MAX_ARRAY_INDEX ? value : -1;
  }

  /** @return whether the object was made with {@code Object.create(null)} */
  public boolean isNullPrototype() {
    return nullPrototype;
  }

  /**
   * @param key a property name
   * @return whether the object has that own property
   */
  public boolean has(String key) {
    long index = arrayIndex(key);
    return index >= 0 ? indexed.containsKey(index) : named.containsKey(key);
  }

  /**
   * @param key a property name
   * @return the own property's value, {@code undefined} when there is none
   */
  public Object get(String key) {
    long index = arrayIndex(key);
    if (index >= 0) {
      return indexed.containsKey(index) ? indexed.get(index) : Js.UNDEFINED;
    }
    return named.containsKey(key) ? named.get(key) : Js.UNDEFINED;
  }

  /**
   * Creates or overwrites an own property; an overwritten property keeps its position.
   *
   * @param key a property name
   * @param value its value
   */
  public void put(String key, Object value) {
    long index = arrayIndex(key);
    if (index >= 0) {
      indexed.put(index, value);
    } else {
      named.put(key, value);
    }
  }

  /**
   * Deletes an own property.
   *
   * @param key a property name
   */
  public void delete(String key) {
    long index = arrayIndex(key);
    if (index >= 0) {
      indexed.remove(index);
    } else {
      named.remove(key);
    }
  }

  /** @return the own property names in property order ({@code Object.keys}) */
  public List<String> keys() {
    List<String> keys = new ArrayList<>(indexed.size() + named.size());
    for (Long index : indexed.keySet()) {
      keys.add(Long.toString(index));
    }
    keys.addAll(named.keySet());
    return keys;
  }

  /** @return the number of own properties */
  public int size() {
    return indexed.size() + named.size();
  }

  /** @return a shallow copy with {@code Object.prototype} ({@code { ...this }}) */
  public JsObject copy() {
    JsObject copy = new JsObject();
    copy.indexed.putAll(indexed);
    copy.named.putAll(named);
    return copy;
  }

  /** @return the own properties in property order */
  public Map<String, Object> entries() {
    Map<String, Object> entries = new LinkedHashMap<>();
    for (Map.Entry<Long, Object> entry : indexed.entrySet()) {
      entries.put(Long.toString(entry.getKey()), entry.getValue());
    }
    entries.putAll(named);
    return entries;
  }
}
