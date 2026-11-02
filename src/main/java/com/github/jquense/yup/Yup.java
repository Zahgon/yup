/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.util.PrintValue;

/** The entry points of the library: the schema factories and the utilities yup exports. */
public final class Yup {
  private Yup() {}

  /** A schema constructor that {@link #addMethod} can extend (the factory functions and the schema classes). */
  public enum Constructor {
    /** {@code mixed}. */
    MIXED(Prototype.MIXED),
    /** {@code string}. */
    STRING(Prototype.STRING),
    /** {@code number}. */
    NUMBER(Prototype.NUMBER),
    /** {@code boolean} and {@code bool}. */
    BOOLEAN(Prototype.BOOLEAN),
    /** {@code date}. */
    DATE(Prototype.DATE),
    /** {@code object}. */
    OBJECT(Prototype.OBJECT_SCHEMA),
    /** {@code array}. */
    ARRAY(Prototype.ARRAY),
    /** {@code tuple}. */
    TUPLE(Prototype.TUPLE),
    /** {@code lazy}: a plain function, not a schema constructor. */
    LAZY(Prototype.FUNCTION),
    /** {@code ref}: a plain function, not a schema constructor. */
    REF(Prototype.FUNCTION),
    /** {@code Schema}. */
    SCHEMA(Prototype.SCHEMA),
    /** {@code LazySchema}: lazy schemas are not instances of a schema prototype. */
    LAZY_SCHEMA(Prototype.LAZY);

    private final Prototype prototype;

    Constructor(Prototype prototype) {
      this.prototype = prototype;
    }

    /** @return the constructor's {@code prototype} */
    public Prototype prototype() {
      return prototype;
    }
  }

  /** @return {@code mixed()} */
  public static MixedSchema mixed() {
    return new MixedSchema();
  }

  /**
   * @param spec a type check function or {@code {type, check}}
   * @return {@code mixed(spec)}
   */
  public static MixedSchema mixed(Object spec) {
    return new MixedSchema(spec);
  }

  /** @return {@code string()} */
  public static StringSchema string() {
    return new StringSchema();
  }

  /** @return {@code number()} */
  public static NumberSchema number() {
    return new NumberSchema();
  }

  /** @return {@code boolean()} */
  public static BooleanSchema bool() {
    return new BooleanSchema();
  }

  /** @return {@code date()} */
  public static DateSchema date() {
    return new DateSchema();
  }

  /**
   * @param shape an object of field schemas
   * @return {@code object(shape)}
   */
  public static ObjectSchema object(Object shape) {
    return new ObjectSchema(shape);
  }

  /** @return {@code object()} */
  public static ObjectSchema object() {
    return new ObjectSchema();
  }

  /**
   * @param type the element schema
   * @return {@code array(type)}
   */
  public static ArraySchema array(Object type) {
    return new ArraySchema(type);
  }

  /** @return {@code array()} */
  public static ArraySchema array() {
    return new ArraySchema();
  }

  /**
   * @param types an array of element schemas
   * @return {@code tuple(types)}
   */
  public static TupleSchema tuple(Object types) {
    return new TupleSchema(types);
  }

  /**
   * @param builder {@code (value, options) => schema}
   * @return {@code lazy(builder)}
   */
  public static Lazy lazy(Object builder) {
    return new Lazy(builder);
  }

  /**
   * @param key the key
   * @param options {@code undefined} or {@code {map}}
   * @return {@code ref(key, options)}
   */
  public static Reference ref(Object key, Object options) {
    return new Reference(key, options);
  }

  /**
   * @param key the key
   * @return {@code ref(key)}
   */
  public static Reference ref(String key) {
    return new Reference(key);
  }

  /**
   * {@code isSchema(obj)} as JavaScript computes it: {@code obj && obj.__isYupSchema__}.
   *
   * @param obj any value
   * @return the value itself when falsy, otherwise its {@code __isYupSchema__} member
   */
  public static Object isSchemaValue(Object obj) {
    if (!Js.truthy(obj)) {
      return obj;
    }
    if (obj instanceof ISchema) {
      return true;
    }
    return Js.get(obj, "__isYupSchema__");
  }

  /**
   * @param obj any value
   * @return whether it is a schema
   */
  public static boolean isSchema(Object obj) {
    return Js.truthy(isSchemaValue(obj));
  }

  /**
   * {@code reach(schema, path, value, context)}.
   *
   * @param schema the root schema
   * @param path the path
   * @param value the root value, or {@code undefined}
   * @param context the context, or {@code undefined}
   * @return the schema (or reference) at the path
   */
  public static Object reach(Object schema, String path, Object value, Object context) {
    return Reach.reach(schema, path, value, context);
  }

  /**
   * {@code getIn(schema, path, value, context)}.
   *
   * @param schema the root schema
   * @param path the path
   * @param value the root value, or {@code undefined}
   * @param context the context, or {@code undefined}
   * @return the schema, parent and last part
   */
  public static Reach.Result getIn(Object schema, String path, Object value, Object context) {
    return Reach.getIn(schema, path, value, context);
  }

  /**
   * {@code printValue(value, quoteStrings)}.
   *
   * @param value any value
   * @param quoteStrings whether strings are quoted
   * @return the printed value
   */
  public static Object printValue(Object value, Object quoteStrings) {
    return PrintValue.printValue(value, quoteStrings);
  }

  /** @return {@code defaultLocale} */
  public static JsObject defaultLocale() {
    return Locale.defaultLocale();
  }

  /**
   * {@code setLocale(custom)}.
   *
   * @param custom an object of message sections
   */
  public static void setLocale(Object custom) {
    Locale.setLocale(custom);
  }

  /**
   * {@code addMethod(schemaType, name, fn)}: adds a builder method to a schema prototype.
   *
   * @param schemaType a {@link Constructor} (anything else is rejected)
   * @param name the method name
   * @param fn the method, called with the schema as {@code this}
   * @throws JsError a {@code TypeError} for an invalid schema type, name or function
   */
  public static void addMethod(Object schemaType, Object name, Object fn) {
    if (!(schemaType instanceof Constructor constructor) || !constructor.prototype().isSchema()) {
      throw JsError.typeError("You must provide a yup schema constructor function");
    }
    if (!(name instanceof String methodName)) {
      throw JsError.typeError("A Method name must be provided");
    }
    if (!(fn instanceof JsFunction method)) {
      throw JsError.typeError("Method function must be provided");
    }
    constructor.prototype().add(methodName, method);
  }
}
