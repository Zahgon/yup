/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * The field order behind ObjectSchema. yup's JavaScript gets it from the toposort package; this class was written for the Java port and
 * reproduces that order and those error messages.
 */
package com.github.jquense.yup.util;

import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsonWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Topological sorting of the nodes of a dependency graph. */
public final class Toposort {
  private Toposort() {}

  /**
   * Sorts the nodes so that every edge {@code [a, b]} puts {@code a} before {@code b}: a depth-first walk from the last node to the first,
   * following each node's edges from the last to the first, where a node takes the front of the result once everything after it is placed.
   *
   * @param nodes the nodes, without duplicates
   * @param edges the edges
   * @return the sorted nodes
   * @throws JsError an {@code Error} for an unknown node or a cycle
   */
  public static List<String> array(List<String> nodes, List<String[]> edges) {
    Set<String> known = new HashSet<>(nodes);
    Map<String, List<String>> successors = new HashMap<>();
    for (String[] edge : edges) {
      if (!known.contains(edge[0]) || !known.contains(edge[1])) {
        throw JsError.error("Unknown node. There is an unknown node in the supplied edges.");
      }
      List<String> next = successors.computeIfAbsent(edge[0], k -> new ArrayList<>());
      if (!next.contains(edge[1])) {
        next.add(edge[1]);
      }
    }
    Deque<String> placed = new ArrayDeque<>();
    Set<String> finished = new HashSet<>();
    Set<String> onPath = new HashSet<>();
    for (int i = nodes.size() - 1; i >= 0; i--) {
      place(nodes.get(i), successors, onPath, finished, placed);
    }
    return new ArrayList<>(placed);
  }

  private static void place(String node, Map<String, List<String>> successors, Set<String> onPath, Set<String> finished,
      Deque<String> placed) {
    if (onPath.contains(node)) {
      throw JsError.error("Cyclic dependency, node was:" + JsonWriter.quote(node));
    }
    if (!finished.add(node)) {
      return;
    }
    onPath.add(node);
    List<String> next = successors.getOrDefault(node, List.of());
    for (int i = next.size() - 1; i >= 0; i--) {
      place(next.get(i), successors, onPath, finished, placed);
    }
    onPath.remove(node);
    placed.addFirst(node);
  }
}
