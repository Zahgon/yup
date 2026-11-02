/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsException;
import com.github.jquense.yup.js.JsFunction;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsPromise;

/**
 * A test made by {@code createValidation(config)}: the config ({@code name}, {@code message}, {@code test},
 * {@code params}, {@code exclusive}, {@code skipAbsent}) is kept as the JavaScript object it was given as.
 */
public final class Test implements RunTest {
  private final JsObject config;

  /**
   * {@code createValidation(config)}.
   *
   * @param config the test config
   */
  public Test(JsObject config) {
    this.config = config;
  }

  /** @return the config ({@code OPTIONS}) */
  public JsObject options() {
    return config;
  }

  /** @return the test's name */
  public Object name() {
    return config.get("name");
  }

  /** @return the test function */
  public Object testFunction() {
    return config.get("test");
  }

  @Override
  public void run(Args args, Callback panic, Callback next) {
    Object value = args.value();
    Object path = args.path() == Js.UNDEFINED ? "" : args.path();
    Object options = args.options();
    Object originalValue = args.originalValue();
    Schema schema = args.schema();
    Object name = config.get("name");
    Object test = config.get("test");
    Object params = config.get("params");
    Object message = config.get("message");
    Object skipAbsent = config.get("skipAbsent");
    Object parent = Js.get(options, "parent");
    Object context = Js.get(options, "context");
    Object abortEarly = Js.get(options, "abortEarly");
    if (abortEarly == Js.UNDEFINED) {
      abortEarly = schema.spec().get("abortEarly");
    }
    Object disableStackTrace = Js.get(options, "disableStackTrace");
    if (disableStackTrace == Js.UNDEFINED) {
      disableStackTrace = schema.spec().get("disableStackTrace");
    }
    JsObject resolveOptions = JsObject.of("value", value, "parent", parent, "context", context);
    Object stackSetting = disableStackTrace;
    java.util.function.UnaryOperator<Object> createError = overridesValue -> {
      Object overrides = overridesValue == Js.UNDEFINED ? new JsObject() : overridesValue;
      Object overridePath = Js.read(overrides, "path");
      Object overrideStack = Js.get(overrides, "disableStackTrace");
      JsObject nextParams = JsObject.of(
          "value", value,
          "originalValue", originalValue,
          "label", schema.spec().get("label"),
          "path", Js.truthy(overridePath) ? overridePath : path,
          "spec", schema.spec(),
          "disableStackTrace", Js.truthy(overrideStack) ? overrideStack : stackSetting);
      Js.spread(nextParams, params);
      Js.spread(nextParams, Js.get(overrides, "params"));
      resolveParams(nextParams, resolveOptions);
      Object overrideMessage = Js.get(overrides, "message");
      Object overrideType = Js.get(overrides, "type");
      ValidationError error = new ValidationError(
          ValidationError.formatError(Js.truthy(overrideMessage) ? overrideMessage : message, nextParams),
          value,
          nextParams.get("path"),
          Js.truthy(overrideType) ? overrideType : name,
          nextParams.get("disableStackTrace"));
      error.setParams(nextParams);
      return error;
    };
    Callback invalid = Js.truthy(abortEarly) ? panic : next;
    TestContext ctx = new TestContext(path, parent, name, Js.get(options, "from"), options, originalValue, schema,
        createError, item -> resolveMaybeRef(item, resolveOptions));
    java.util.function.Consumer<Object> handleResult = validOrError -> {
      if (ValidationError.isError(validOrError)) {
        invalid.call(validOrError, Js.UNDEFINED);
      } else if (!Js.truthy(validOrError)) {
        invalid.call(createError.apply(Js.UNDEFINED), Js.UNDEFINED);
      } else {
        next.call(null, Js.UNDEFINED);
      }
    };
    java.util.function.Consumer<Object> handleError = err -> {
      if (ValidationError.isError(err)) {
        invalid.call(err, Js.UNDEFINED);
      } else {
        panic.call(err, Js.UNDEFINED);
      }
    };
    if (Js.truthy(skipAbsent) && Js.isNullish(value)) {
      handleResult.accept(true);
      return;
    }
    Object result;
    try {
      if (!(test instanceof JsFunction function)) {
        throw JsError.typeError("test.call is not a function");
      }
      result = function.call(ctx, value, ctx);
      if (JsPromise.isThenable(result)) {
        if (Js.truthy(Js.get(options, "sync"))) {
          throw JsError.error("Validation test of type: \"" + Js.toJsString(ctx.type())
              + "\" returned a Promise during a synchronous validate. "
              + "This test will finish after the validate call has returned");
        }
        JsPromise.resolved(result).then(valid -> {
          handleResult.accept(valid);
          return Js.UNDEFINED;
        }, err -> {
          handleError.accept(err);
          return Js.UNDEFINED;
        });
        return;
      }
    } catch (RuntimeException e) {
      handleError.accept(JsException.valueOf(e));
      return;
    }
    handleResult.accept(result);
  }

  /**
   * {@code resolveParams(params, options)}: replaces every reference among the params with its value (in place).
   *
   * @param params the params object
   * @param options the resolve options
   * @return the params
   */
  static Object resolveParams(Object params, Object options) {
    if (params instanceof JsObject object) {
      for (String key : object.keys()) {
        object.put(key, resolveMaybeRef(object.get(key), options));
      }
    }
    return params;
  }

  static Object resolveMaybeRef(Object item, Object options) {
    return item instanceof Reference ref
        ? ref.getValue(Js.get(options, "value"), Js.get(options, "parent"), Js.get(options, "context"))
        : item;
  }
}
