# Yup (Java)

Yup is a schema builder for runtime value parsing and validation. Define a schema, transform a value to match,
assert the shape of an existing value, or both. Yup schema are expressive and allow modeling complex,
interdependent validations, or value transformation.

This is a Java port of [yup](https://github.com/jquense/yup) 1.7.0. It keeps yup's behaviour exactly, including
the parts yup gets from JavaScript: values are JavaScript values (`undefined` and `null`, numbers as doubles,
UTF-16 strings, objects whose members keep JavaScript's property order), conversions and comparisons follow the
ECMAScript rules, regular expressions follow JavaScript's dialect, dates are parsed as `Date.parse` does in V8
(local time is UTC), and messages print values the way yup's `printValue` does. It needs only the JDK.

## Building

```sh
mvn -q -DskipTests package   # builds target/yup.jar
mvn test                     # runs the tests
```

Java 21 or later. There are no dependencies besides the JDK.

## Using the library

```java
import static com.github.jquense.yup.js.Js.UNDEFINED;

import com.github.jquense.yup.*;
import com.github.jquense.yup.js.JsObject;

ObjectSchema user = Yup.object(JsObject.of(
    "name", Yup.string().required(UNDEFINED),
    "age", Yup.number().positive(UNDEFINED).integer(UNDEFINED).required(UNDEFINED),
    "email", Yup.string().email(UNDEFINED),
    "website", Yup.string().url(UNDEFINED).nullable()));

// Cast: coerce a value into the schema's shape.
Object cast = user.cast(JsObject.of("name", "jimmy", "age", "24"), UNDEFINED);   // {name: "jimmy", age: 24.0}

// Validate: cast, then run the tests. validate() returns a promise; await() runs it to completion.
try {
  user.validate(JsObject.of("name", "jimmy", "age", "-1"), JsObject.of("abortEarly", false)).await();
} catch (ValidationError e) {
  e.errors();  // ["age must be a positive number"]
}
```

Every builder method mirrors yup's. Arguments are JavaScript values (see `com.github.jquense.yup.js.Js`);
`Js.UNDEFINED` stands for an argument left out, so that yup's defaults apply (`min(5, UNDEFINED)` uses the default
message). Numbers are `Double`s; other Java numbers are accepted for limits (`min(5, ...)`) and defaults.

| yup | Java |
| --- | --- |
| `mixed()`, `string()`, `number()`, `boolean()`, `date()`, `object()`, `array()`, `tuple()`, `lazy()`, `ref()` | `Yup.mixed()`, `Yup.string()`, ..., `Yup.lazy(fn)`, `Yup.ref(key)` |
| `schema.validate(v, o)` / `validateSync` / `isValid` / `isValidSync` | the same names; async results are `JsPromise`s |
| `schema.default(v)` / `schema.default()` | `defaultValue(v)` / `defaultValue()` |
| `schema.equals(...)` | `oneOf(...)` (and its alias `is(...)` in the command) |
| `schema['~standard']` | `standard()`, `StandardSchema.validate(schema, value)` |
| `reach`, `getIn`, `isSchema`, `printValue`, `setLocale`, `defaultLocale`, `addMethod` | static methods of `Yup` |

Functions (custom tests, transforms, conditions, messages) are `JsFunction`s, called with a receiver and arguments
as in JavaScript; a test function's receiver is its `TestContext`.

## The `yup` command

`bin/yup` (after building) reads JSON-lines requests on standard input and writes one JSON response line per
request, in order:

```sh
$ echo '{"op":"validate","schema":["string",["min",3]],"value":"ab"}' | bin/yup
{"invalid":{"name":"ValidationError","message":"this must be at least 3 characters", ...}}
```

A request names an operation (`cast`, `validate`, `validateSync`, `validateAt`, `validateSyncAt`, `isValid`,
`isValidSync`, `isType`, `describe`, `resolve`, `getDefault`, `default`, `meta`, `reach`, `getIn`, `printValue`,
`isSchema`, `validationError`, `formatError`, `isError`, `standardValidate`, `locale`, `ref`) with its members.
Schemas are written as expressions: `["string", ["min", 2], ["required"]]` is `string().min(2).required()`,
`[["object", {"a": {"$schema": ["number"]}}]]` is `object({ a: number() })`. Values are JSON with tags for what
JSON cannot express (`{"$undefined": true}`, `{"$number": "NaN"}`, `{"$date": ...}`, `{"$regex": ..., "flags": ...}`,
`{"$ref": ...}`, ...), and user functions are chosen from a catalog (`{"$fn": "compare", "op": "<", "to": 5}`).
Errors are answered with `{"error": {"name": ..., "message": ...}}`. The exit status is 0 when every request
succeeded, 1 otherwise, and 2 (with a usage text) when the command is given arguments. As with the original running on
Node.js, a request that can never settle (a thenable that never calls back) ends the command with no output and status 0.

## Extending

See [docs/extending.md](docs/extending.md).

## License

MIT, see [LICENSE.md](LICENSE.md).
