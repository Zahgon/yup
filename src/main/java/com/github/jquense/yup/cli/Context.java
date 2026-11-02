/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.cli;

import com.github.jquense.yup.Prototype;
import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The state of one request: its named functions and definitions, and the methods it installed. */
final class Context {
  /** The {@code functions} member, or {@code undefined}. */
  final Object functionSpecs;
  /** The functions made from it so far. */
  final Map<String, Object> functions = new HashMap<>();
  /** The functions being made (to catch self references). */
  final Set<String> making = new HashSet<>();
  /** The {@code defs} member, or {@code undefined}. */
  final Object defSpecs;
  /** The schemas built from it so far. */
  final Map<String, Object> defs = new HashMap<>();
  /** The definitions being built (to catch self references). */
  final Set<String> building = new HashSet<>();
  /** The names of the methods the request installed. */
  final Set<String> added = new HashSet<>();
  /** Where the request installed methods, to remove them afterwards. */
  final List<Installed> installed = new ArrayList<>();

  /** A method installed on a prototype. */
  record Installed(Prototype prototype, String name) {}

  private Context(Object functionSpecs, Object defSpecs) {
    this.functionSpecs = functionSpecs;
    this.defSpecs = defSpecs;
  }

  /**
   * The context of a request, checking its {@code functions} and {@code defs} members.
   *
   * @param request the request object
   * @return the context
   */
  static Context of(JsObject request) {
    Object functions = request.get("functions");
    if (functions != Js.UNDEFINED && !Decoder.isObject(functions)) {
      throw new RequestError("functions must be an object");
    }
    Object defs = request.get("defs");
    if (defs != Js.UNDEFINED && !Decoder.isObject(defs)) {
      throw new RequestError("defs must be an object");
    }
    return new Context(functions, defs);
  }

  /** Removes the methods the request installed. */
  void uninstall() {
    for (Installed method : installed) {
      method.prototype().remove(method.name());
    }
  }
}
