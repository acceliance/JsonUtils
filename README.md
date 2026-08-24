# Json Utils

Lightweight Jackson-based helpers for reading, writing, and describing Java objects as JSON —
including JSON Schema generation tuned for consumption by AI models (LLM structured
output / function-calling / tool-use schemas).

## Why

Feeding a raw, mechanically-generated JSON Schema to an LLM often wastes context and confuses
the model: duplicated object definitions, missing `additionalProperties: false` constraints,
and ambiguous references all make the schema harder to follow. `JsonSchemaGen` produces
Draft 2020-12 schemas designed to stay compact and unambiguous for that use case:

- **One definition per type** (`$defs`), referenced everywhere it recurs — no duplication.
- **`additionalProperties: false` by default** on every object, so models don't hallucinate
  extra fields.
- **Human/model-readable titles** derived from each class's simple name.
- **Identity-reference awareness**: types annotated with Jackson's
  `@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class)` are serialized in
  full once and referenced by key afterwards. The generated schema reflects that with a
  `oneOf: [string, object]` on every reference, so validation (and an LLM reading the schema)
  correctly accepts both forms instead of rejecting the by-key shorthand.

## Requirements

- Java 11+
- Maven

## Installation

```xml
<dependency>
    <groupId>fr.acceliance</groupId>
    <artifactId>jsonutils</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Usage

### Write an object to JSON

```java
import fr.acceliance.jsonutils.serialization.JsonWriter;

String json = JsonWriter.write(myObject);
```

Produces pretty-printed, indented JSON. Null fields are omitted (`Include.NON_NULL`), and
`java.time` types are supported out of the box via `jackson-datatype-jsr310`.

### Read JSON into an object

```java
import fr.acceliance.jsonutils.serialization.JsonReader;

MyType value = JsonReader.read(json, MyType.class);
```

### Generate a JSON Schema for a type

```java
import fr.acceliance.jsonutils.serialization.JsonSchemaGen;

String schema = JsonSchemaGen.generateSchema(MyType.class);
```

Returns a pretty-printed Draft 2020-12 JSON Schema string for `MyType`, suitable for handing
directly to an LLM as a structured-output / tool-call schema.

## Dependencies

Built on:

- [Jackson](https://github.com/FasterXML/jackson) (`jackson-core`, `jackson-databind`,
  `jackson-annotations`, `jackson-datatype-jsr310`, `jackson-dataformat-xml`)
- [victools jsonschema-generator](https://github.com/victools/jsonschema-generator) with the
  Jackson module, for schema generation
- [json-schema-validator](https://github.com/java-json-tools/json-schema-validator) for schema
  validation support

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

## Author

[Acceliance](https://acceliance.fr)
