/*
 * The MIT License (MIT)
 * Copyright (c) 2012 Marcel Klehr
 *
 * Java port of toposort 2.0.2 (https://github.com/marcelklehr/toposort), yup's field-ordering dependency.
 */
package com.github.jquense.yup.util;

import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsonWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Topological sorting of the nodes of a dependency graph. */
public final class Toposort {
  private Toposort() {}

  /**
   * {@code toposort.array(nodes, edges)}: every edge {@code [a, b]} puts {@code a} before {@code b}.
   *
   * @param nodes the nodes
   * @param edges the edges
   * @return the sorted nodes
   * @throws JsError an {@code Error} for an unknown node or a cycle
   */
  public static List<String> array(List<String> nodes, List<String[]> edges) {
    String[] sorted = new String[nodes.size()];
    Map<Integer, Boolean> visited = new HashMap<>();
    Map<String, Set<String>> outgoing = new LinkedHashMap<>();
    for (String[] edge : edges) {
      outgoing.computeIfAbsent(edge[0], k -> new LinkedHashSet<>());
      outgoing.computeIfAbsent(edge[1], k -> new LinkedHashSet<>());
      outgoing.get(edge[0]).add(edge[1]);
    }
    Map<String, Integer> nodesHash = new HashMap<>();
    for (int i = 0; i < nodes.size(); i++) {
      nodesHash.put(nodes.get(i), i);
    }
    for (String[] edge : edges) {
      if (!nodesHash.containsKey(edge[0]) || !nodesHash.containsKey(edge[1])) {
        throw JsError.error("Unknown node. There is an unknown node in the supplied edges.");
      }
    }
    int[] cursor = {nodes.size()};
    for (int i = nodes.size() - 1; i >= 0; i--) {
      if (!visited.containsKey(i)) {
        visit(nodes.get(i), i, new LinkedHashSet<>(), sorted, cursor, visited, outgoing, nodesHash);
      }
    }
    List<String> result = new ArrayList<>(sorted.length);
    for (String node : sorted) {
      result.add(node);
    }
    return result;
  }

  private static void visit(String node, int i, Set<String> predecessors, String[] sorted, int[] cursor,
      Map<Integer, Boolean> visited, Map<String, Set<String>> outgoing, Map<String, Integer> nodesHash) {
    if (predecessors.contains(node)) {
      throw JsError.error("Cyclic dependency, node was:" + JsonWriter.quote(node));
    }
    if (!nodesHash.containsKey(node)) {
      throw JsError.error(
          "Found unknown node. Make sure to provided all involved nodes. Unknown node: " + JsonWriter.quote(node));
    }
    if (visited.containsKey(i)) {
      return;
    }
    visited.put(i, true);
    List<String> children = new ArrayList<>(outgoing.getOrDefault(node, Set.of()));
    int index = children.size();
    if (index > 0) {
      predecessors.add(node);
      do {
        String child = children.get(--index);
        visit(child, nodesHash.get(child), predecessors, sorted, cursor, visited, outgoing, nodesHash);
      } while (index > 0);
      predecessors.remove(node);
    }
    sorted[--cursor[0]] = node;
  }
}
