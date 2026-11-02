/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.util.PropertyExpr;

/** {@code reach} and {@code getIn}: the schema at a path of a schema, with the value and parent found on the way. */
public final class Reach {
  private Reach() {}

  /**
   * What {@code getIn} finds.
   *
   * @param schema the schema (or reference) at the path
   * @param parent the parent value of the last part
   * @param parentPath the last part ({@code path} itself for the root)
   */
  public record Result(Object schema, Object parent, Object parentPath) {}

  /**
   * {@code getIn(schema, path, value, context)}.
   *
   * @param root the root schema
   * @param path the path
   * @param value the root value, or {@code undefined}
   * @param context the context, {@code undefined} for the value
   * @return the schema, parent and last part
   * @throws JsError an {@code Error} when the path does not exist in the schema
   */
  public static Result getIn(Object root, String path, Object value, Object context) {
    Object ctx = context == Js.UNDEFINED ? value : context;
    if (path.isEmpty()) {
      return new Result(root, Js.UNDEFINED, path);
    }
    Object[] state = {root, Js.UNDEFINED, value, Js.UNDEFINED, Js.UNDEFINED};
    PropertyExpr.forEach(path, (rawPart, isBracket, isArray) -> {
      String part = isBracket ? rawPart.substring(1, rawPart.length() - 1) : rawPart;
      Object schema = state[0];
      Object parent = state[1];
      Object current = state[2];
      if (!(schema instanceof SchemaLike resolvable)) {
        throw JsError.typeError("Cannot read properties of " + (schema == null ? "null" : "undefined")
            + " (reading 'resolve')");
      }
      schema = resolvable.resolve(JsObject.of("context", ctx, "parent", parent, "value", current));
      boolean isTuple = Js.strictEquals(Js.get(schema, "type"), "tuple");
      double idx = isArray ? Js.toNumber(part) : 0;
      Object innerType = schema instanceof ArraySchema array ? array.innerType() : Js.UNDEFINED;
      String lastPartDebug = Js.toJsString(state[4]);
      if (Js.truthy(innerType) || isTuple) {
        if (isTuple && !isArray) {
          throw JsError.error("Yup.reach cannot implicitly index into a tuple type. the path part \"" + lastPartDebug
              + "\" must contain an index to the tuple element, e.g. \"" + lastPartDebug + "[0]\"");
        }
        if (Js.truthy(current) && Js.ge(idx, Js.get(current, "length"))) {
          throw JsError.error("Yup.reach cannot resolve an array item at index: " + rawPart + ", in the path: " + path
              + ". because there is no value at that index. ");
        }
        parent = current;
        current = Js.truthy(current) ? Js.get(current, Js.toJsString(idx)) : current;
        schema = isTuple ? Js.get(((Schema) schema).spec().get("types"), Js.toJsString(idx)) : innerType;
      }
      if (!isArray) {
        Object fields = schema instanceof ObjectSchema object ? object.fields() : Js.UNDEFINED;
        if (!Js.truthy(fields) || !Js.truthy(Js.get(fields, part))) {
          throw JsError.error("The schema does not contain the path: " + path + ". (failed at: " + lastPartDebug
              + " which is a type: \"" + Js.toJsString(Js.get(schema, "type")) + "\")");
        }
        parent = current;
        current = Js.truthy(current) ? Js.get(current, part) : current;
        schema = Js.get(fields, part);
      }
      state[0] = schema;
      state[1] = parent;
      state[2] = current;
      state[3] = part;
      state[4] = isBracket ? "[" + rawPart + "]" : "." + rawPart;
    });
    return new Result(state[0], state[1], state[3]);
  }

  /**
   * {@code reach(schema, path, value, context)}.
   *
   * @param root the root schema
   * @param path the path
   * @param value the root value, or {@code undefined}
   * @param context the context, or {@code undefined}
   * @return the schema (or reference) at the path
   */
  public static Object reach(Object root, String path, Object value, Object context) {
    return getIn(root, path, value, context).schema();
  }
}
