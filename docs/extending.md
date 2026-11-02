# Extending Schema

For simple cases where you want to reuse common schema configurations, creating and passing around instances
works great:

```java
Schema requiredString = Yup.string().required(UNDEFINED).defaultValue("");
```

Schema are immutable, so each can be configured further without changing the original.

## Custom tests and transforms

Tests and transforms are `JsFunction`s. A transform gets the value, the original value and the schema; a test gets
the value and its `TestContext` (also its receiver), and returns a truthy value, a falsy value or a
`ValidationError` (from `ctx.createError(...)`):

```java
Schema even = Yup.number().test("even", "${path} must be even", (JsFunction) (self, args) ->
    Js.toNumber(args[0]) % 2 == 0);

Schema trimmedKeys = Yup.object().transform((JsFunction) (self, args) -> args[0]);
```

A test may return a `JsPromise` when it is asynchronous (or a thenable: a `JsObject` whose `then` member is a
`JsFunction`, followed as JavaScript follows one); `validateSync` then fails as yup does.

## Adding methods

`Yup.addMethod()` adds a method to a schema prototype, as yup's `addMethod()` does. Java code calls its own
methods directly, so added methods are what the `yup` command's schema expressions can call:

```java
Yup.addMethod(Yup.Constructor.STRING, "slug", (JsFunction) (self, args) ->
    ((StringSchema) self).matches(new JsRegExp("^[a-z0-9-]+$"), UNDEFINED));
```

## Creating new schema types

Extend an existing schema class, or `MixedSchema` with a type check. Keep yup's guidelines: never mutate an existing
schema (builder methods copy it for you), and make transforms return an invalid value (`NaN`, an invalid date)
rather than `null` for bad input.
