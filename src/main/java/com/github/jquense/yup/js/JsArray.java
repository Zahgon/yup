/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

import java.util.ArrayList;
import java.util.List;

/** A JavaScript array, which may have empty slots (holes). */
public final class JsArray {
  private static final Object HOLE = new Object();

  private final ArrayList<Object> items;

  /** An empty array. */
  public JsArray() {
    items = new ArrayList<>();
  }

  /**
   * An array of the given elements.
   *
   * @param elements the elements, in order
   */
  public JsArray(List<?> elements) {
    items = new ArrayList<>(elements);
  }

  /**
   * An array of the given elements.
   *
   * @param elements the elements, in order
   * @return the array
   */
  public static JsArray of(Object... elements) {
    return new JsArray(List.of(elements));
  }

  /**
   * An array of empty slots ({@code new Array(length)}).
   *
   * @param length its length
   * @return the array
   */
  public static JsArray ofLength(int length) {
    JsArray array = new JsArray();
    for (int i = 0; i < length; i++) {
      array.items.add(HOLE);
    }
    return array;
  }

  /** @return the length */
  public int length() {
    return items.size();
  }

  /**
   * @param index an index
   * @return whether the index holds an element (not a hole, not past the end)
   */
  public boolean has(int index) {
    return index >= 0 && index < items.size() && items.get(index) != HOLE;
  }

  /**
   * @param index an index
   * @return the element, {@code undefined} for a hole or an index past the end
   */
  public Object get(int index) {
    if (index < 0 || index >= items.size()) {
      return Js.UNDEFINED;
    }
    Object value = items.get(index);
    return value == HOLE ? Js.UNDEFINED : value;
  }

  /**
   * Stores an element, growing the array with holes when needed.
   *
   * @param index an index
   * @param value the element
   */
  public void set(int index, Object value) {
    while (items.size() <= index) {
      items.add(HOLE);
    }
    items.set(index, value);
  }

  /**
   * Appends an element.
   *
   * @param value the element
   */
  public void push(Object value) {
    items.add(value);
  }

  /**
   * Appends an empty slot.
   */
  public void pushHole() {
    items.add(HOLE);
  }

  /** @return the elements, holes read as {@code undefined} */
  public List<Object> toList() {
    List<Object> list = new ArrayList<>(items.size());
    for (int i = 0; i < items.size(); i++) {
      list.add(get(i));
    }
    return list;
  }

  /** @return a copy that keeps the holes */
  public JsArray copy() {
    return new JsArray(items);
  }
}
