/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsArray;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsFunction;
import java.util.List;

/** A {@code when()} condition: a builder called with the referenced values that may replace the schema. */
final class Condition {
  private final List<Reference> refs;
  private final JsFunction builder;

  /**
   * A condition with a builder {@code (values, schema, options) => schema}.
   *
   * @param refs the references whose values the builder gets
   * @param builder the builder
   */
  Condition(List<Reference> refs, JsFunction builder) {
    this.refs = refs;
    this.builder = builder;
  }

  /**
   * {@code Condition.fromOptions(refs, {is, then, otherwise})}.
   *
   * @param refs the references
   * @param config the options object
   * @return the condition
   * @throws JsError a {@code TypeError} when neither {@code then} nor {@code otherwise} is given
   */
  static Condition fromOptions(List<Reference> refs, Object config) {
    Object then = Js.read(config, "then");
    if (!Js.truthy(then) && !Js.truthy(Js.read(config, "otherwise"))) {
      throw JsError.typeError("either `then:` or `otherwise:` is required for `when()` conditions");
    }
    Object is = Js.get(config, "is");
    Object otherwise = Js.get(config, "otherwise");
    JsFunction check = is instanceof JsFunction function
        ? function
        : (self, values) -> {
          for (Object value : values) {
            if (!Js.strictEquals(value, is)) {
              return false;
            }
          }
          return true;
        };
    return new Condition(refs, (self, args) -> {
      JsArray values = (JsArray) args[0];
      Object schema = args[1];
      Object branch = Js.truthy(check.call(Js.UNDEFINED, values.toList().toArray())) ? then : otherwise;
      if (Js.isNullish(branch)) {
        return schema;
      }
      if (!(branch instanceof JsFunction function)) {
        throw JsError.typeError("branch is not a function");
      }
      Object result = function.call(Js.UNDEFINED, schema);
      return Js.isNullish(result) ? schema : result;
    });
  }

  /**
   * Resolves the condition against a base schema.
   *
   * @param base the schema so far
   * @param options the resolve options
   * @return the schema to use
   */
  SchemaLike resolve(SchemaLike base, Object options) {
    JsArray values = new JsArray();
    for (Reference ref : refs) {
      values.push(ref.getValue(Js.get(options, "value"), Js.get(options, "parent"), Js.get(options, "context")));
    }
    Object schema = builder.call(this, values, base, options);
    if (schema == Js.UNDEFINED || schema == base) {
      return base;
    }
    if (!Yup.isSchema(schema)) {
      throw JsError.typeError("conditions must return a schema object");
    }
    return ((SchemaLike) schema).resolve(options);
  }
}
