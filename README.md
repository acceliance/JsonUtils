# Json Utils

[![Java 11](https://img.shields.io/badge/Java-11-007396?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![Jackson](https://img.shields.io/badge/Jackson-JSON%20databind-000000)](https://github.com/FasterXML/jackson)
[![JSON Schema](https://img.shields.io/badge/JSON%20Schema-Draft%202020--12-blue)](https://json-schema.org/draft/2020-12/release-notes)

[![Licence](https://img.shields.io/badge/licence-Apache%202.0-green)](LICENSE)
[![Vendor](https://img.shields.io/badge/vendor-Acceliance-orange)](https://acceliance.fr)
[![Last commit](https://img.shields.io/github/last-commit/acceliance/JsonUtils)](https://github.com/acceliance/JsonUtils/commits/main)

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
    <version>1.1.0</version>
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

### Build a JSON Schema at runtime (no Java class)

When the types only exist at runtime (for example in a UML model), `JsonSchemaBuilder`
produces a schema with the same conventions as `JsonSchemaGen`: one `$defs` entry per
type, `additionalProperties: false`, and `oneOf [key, $ref]` for references by identity key.

```java
import fr.acceliance.jsonutils.serialization.JsonSchemaBuilder;

JsonSchemaBuilder b = new JsonSchemaBuilder().title("Orders");
b.defineEnum("Status", List.of("OPEN", "CLOSED"), null);
b.defineObject("Order")
    .property("number", b.string(), true)
    .property("status", b.ref("Status"), false)
    .property("customer", b.identityReference(b.string(), b.ref("Customer"), "Customer"), true)
    .property("lines", b.array(b.ref("OrderLine"), 1, null), true);
String schema = b.rootArray("Order", null, null).toJson(); // or rootObject("Order")
```

## Dependencies

Built on:

- [Jackson](https://github.com/FasterXML/jackson) (`jackson-core`, `jackson-databind`,
  `jackson-annotations`, `jackson-datatype-jsr310`, `jackson-dataformat-xml`)
- [victools jsonschema-generator](https://github.com/victools/jsonschema-generator) with the
  Jackson module, for schema generation
- [json-schema-validator](https://github.com/java-json-tools/json-schema-validator) for schema
  validation support

## Releasing

Releases are published to Maven Central via the [Sonatype Central Portal](https://central.sonatype.com/).
Everyday builds (`mvn install`) never touch signing or publishing — that's all scoped to a
dedicated `release` Maven profile.

```powershell
./publish-to-central.ps1
```

The script runs pre-flight checks (GPG key present, `~/.m2/settings.xml` has Central
credentials, version isn't a `SNAPSHOT`) then `mvn clean deploy -Prelease`, which builds,
signs, and uploads a staging bundle. Publishing the bundle itself is a manual step you take at
[central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments)
after reviewing it.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

## Author

[Acceliance](https://acceliance.fr)
