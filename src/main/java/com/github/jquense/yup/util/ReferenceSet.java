/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.util;

import com.github.jquense.yup.Reference;
import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsArray;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** The values of {@code oneOf} and {@code notOneOf}: a JavaScript {@code Set} of values and references. */
public final class ReferenceSet {
  private final List<Object> items = new ArrayList<>();

  /** An empty set. */
  public ReferenceSet() {}

  private ReferenceSet(List<Object> items) {
    this.items.addAll(items);
  }

  private int indexOf(Object value) {
    for (int i = 0; i < items.size(); i++) {
      if (Js.sameValueZero(items.get(i), value)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Adds a value unless the set has it ({@code SameValueZero}).
   *
   * @param value the value
   */
  public void add(Object value) {
    if (indexOf(value) < 0) {
      // Set.prototype.add stores -0 as +0.
      items.add(value instanceof Double d && d == 0 ? (Object) 0.0 : value);
    }
  }

  /**
   * Removes a value.
   *
   * @param value the value
   */
  public void delete(Object value) {
    int index = indexOf(value);
    if (index >= 0) {
      items.remove(index);
    }
  }

  /** @return the values in insertion order */
  public List<Object> values() {
    return List.copyOf(items);
  }

  /** @return the values, references described as {@code {type: 'ref', key}} */
  public JsArray describe() {
    JsArray description = new JsArray();
    for (Object item : items) {
      description.push(item instanceof Reference reference ? reference.describe() : item);
    }
    return description;
  }

  /**
   * @param resolve resolves a value or reference
   * @return the resolved values
   */
  public JsArray resolveAll(UnaryOperator<Object> resolve) {
    JsArray result = new JsArray();
    for (Object item : items) {
      result.push(resolve.apply(item));
    }
    return result;
  }

  /** @return {@code Array.from(set).join(', ')} */
  public String join() {
    return Js.join(new JsArray(items), ", ");
  }

  /** @return a copy */
  public ReferenceSet copy() {
    return new ReferenceSet(items);
  }

  /**
   * A copy with some values added and others removed.
   *
   * @param newItems the values to add
   * @param removeItems the values to remove
   * @return the merged set
   */
  public ReferenceSet merge(ReferenceSet newItems, ReferenceSet removeItems) {
    ReferenceSet next = copy();
    for (Object value : newItems.items) {
      next.add(value);
    }
    for (Object value : removeItems.items) {
      next.delete(value);
    }
    return next;
  }
}
