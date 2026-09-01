# java-dto-generator

Generates a DTO from a method's return type, and lets you name the fields you leave out without
writing them as strings.

```java
@Fields
public class User {
    private String id;
    private String username;
    private String password;
}

public interface UserService {

    @GenerateDto(removeFields = {User.PASSWORD})
    User publicUser();
}
```

produces `PublicUser` with `id` and `username`. Rename `password` and `User.PASSWORD` moves with it;
point it at another class's field and the build stops.

---

## `@Fields`

Injects a `public static final String` constant for every instance field into the annotated class
itself — in place, the way Lombok injects getters, with no generated companion type.

```java
@Fields
public class User {
    private UUID id;
    private String firstName;
}
```

gains:

```java
public static final String ID         = "com.acme.User#id";
public static final String FIRST_NAME = "com.acme.User#firstName";
```

They are real compile-time constants (`ConstantValue` in the class file), so they work as annotation
arguments, in `switch` labels, and from any downstream compilation.

**Naming** is `UPPER_SNAKE_CASE`, breaking a word only where an uppercase run actually ends:

| Field | Constant |
|---|---|
| `id` | `ID` |
| `firstName` | `FIRST_NAME` |
| `first_name` | `FIRST_NAME` |
| `userID` | `USER_ID` |
| `customerVAT` | `CUSTOMER_VAT` |
| `htmlURLParser` | `HTML_URL_PARSER` |

**Inherited fields get a constant too, named after the class that inherits them:**

```java
@Fields class Engineer extends User { private String emailAddress; }

Engineer.ID             // "com.acme.Engineer#id"       — not User#id
Engineer.EMAIL_ADDRESS  // "com.acme.Engineer#emailAddress"
User.ID                 // "com.acme.User#id"
```

The supertype does not need to be annotated — it can be a base class from a jar you cannot modify.
The walk stops at `java.*`, `javax.*` and `jdk.*`, so extending a JDK class contributes nothing.

`@Fields` **never fails a build.** Every problem it has is a warning; see
[When a constant is omitted](#when-a-constant-is-omitted).

## `@FieldPath`

Marks an annotation member as holding those paths, and checks every value.

```java
public @interface DtoConfig {

    String[] words() default {};                  // unconstrained

    @FieldPath
    String[] fields() default {};                 // any class's fields

    @FieldPath(User.class)
    String[] userFields() default {};             // User's fields only

    @FieldPath(returnType = true)
    String[] ownFields() default {};              // fields of the annotated
}                                                 // method's return type
```

Each of these fails the build, and shows the same message in the editor:

```java
userFields = {Account.DATE}     // com.acme.Account#date is a field of com.acme.Account,
                                // but only fields of com.acme.User are allowed here

fields = {"totally.made.up"}    // "totally.made.up" does not refer to a field
```

`@FieldPath` **always errors, never warns.** A wrong path still compiles to a valid `String`, so
whatever consumes it only finds out at runtime.

### `returnType = true`

`@FieldPath(User.class)` fixes one class for every use of the member. That does not fit
`@GenerateDto`, whose subject class is different at every use site — it is whatever the annotated
method returns. `returnType = true` follows it:

```java
@GenerateDto(removeFields = {User.PASSWORD})     // fine
User publicUser();

@GenerateDto(removeFields = {Account.IBAN})      // error: com.acme.Account#iban is a field of
User publicUser();                               // com.acme.Account, but only fields of
                                                 // com.acme.User are allowed here
```

Used where there is no return type — on a class, a field, inside a nested annotation — it is an
error at that use site, as is a return type that is not a class (`void`, a primitive, a type
variable).

---

## Requirements

`@FieldPath` needs nothing. Its checks are written against supported compiler API
(`com.sun.source`, `javax.lang.model`) and work on every JDK from 8 onwards.

`@Fields` rewrites the syntax tree, which needs javac internals the JDK stopped exporting in 9.

- **On Java 8**, nothing is needed.
- **On Java 9 and later**, the compiler must be forked and given these **JVM arguments**:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <fork>true</fork>
        <compilerArgs>
            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED</arg>
            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED</arg>
            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED</arg>
            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

They are JVM arguments, not compiler options, and the difference is not cosmetic:

- The processor **runs inside the compiler's own JVM**, so only that JVM's module graph decides what
  it can reach. A compiler option governs the code being compiled — a different thing entirely, and
  not the thing that is blocked here.
- Compiling against the javac internals needs no flags at all: `target 8` turns module access
  enforcement off for the compilation. Only *running* the processor is blocked.
- Which is also why javac **rejects** a plain `--add-exports` alongside `target 8`:
  `error: option --add-exports not allowed with target 8`.

Without the flags, `@Fields` generates nothing and reports one warning naming them. It never fails
the build — a project that uses only `@GenerateDto` or `@FieldPath` is unaffected by this library
being on the classpath.

## Building

```bash
mvn clean install                                     # needs a JDK; profiles pick the right flags
docker run --rm -v "$PWD":/app -w /app \
  maven:3.9-eclipse-temurin-8 mvn clean test          # what CI actually runs
```

The `javac-internals-jdk8` profile adds `tools.jar`. `javac-internals-jdk9plus` passes the flags to
the **test** JVM only — the tests invoke the processor by compiling in process, so they need the
runtime access; compiling the library itself does not.

## The IDE plugin

The IDE does not run the annotation processor, so without the plugin every generated constant shows
as unresolved. The plugin supplies them as synthetic PSI and mirrors every diagnostic.

```bash
cd idea-plugin
./gradlew buildPlugin
```

That produces `idea-plugin/build/distributions/dto-generator-fields.zip`. Install it with
**Settings → Plugins → ⚙ → Install Plugin from Disk**. It registers three extension points —
`lang.psiAugmentProvider`, `annotator` and `referencesSearch` — and normally takes effect straight
away; take the restart if the IDE offers one.

The first `buildPlugin` downloads the IntelliJ platform it compiles against (~1.4 GB) and takes a
few minutes; later builds are seconds. Pinned in `idea-plugin/build.gradle.kts`:

| | |
|---|---|
| Platform | `intellijIdea("2026.2.1")`, `since-build` 262, no upper bound |
| Gradle plugin | `org.jetbrains.intellij.platform` 2.18.1 |
| Toolchain | Java 25 — IDEA 2026.2 bundles JBR 25, and the platform's test framework is compiled for it |

The plugin compiles `FieldConstants.java` straight out of `../src/main/java` rather than depending on
a published version, so the naming rule, the path format and every message are literally the same
file in both. Reinstall after changing it.

---

# Design notes

Everything below is a decision forced by a concrete problem.

## When a constant is omitted

A constant is skipped rather than generated in three situations, each reported as a **warning** so
nothing is dropped silently:

**The field is already named like a constant.** `private String FOO` would generate `FOO`, colliding
with the field. Java forbids two fields of one name, so it cannot be generated at all.

This bites hardest through inheritance. An *inherited* `FOO` is not declared in the subclass, so a
"does this class already declare it?" check misses it — and the subclass then generates a static
`FOO` that silently **shadows** the inherited instance field. Legal Java, compiles clean, and
`Sub.FOO` quietly stops meaning the field. The rule is therefore about the name itself
(`nameFor(x).equals(x)`), not about what the class declares.

**The class declares a field with that name.** This is what lets you hand-write a constant to
override the generated one:

```java
@Fields class B {
    public static final String ID = "custom";   // kept, untouched
    private String id;                          // no generated ID
}
```

**Two fields map to one constant.** `firstName` and `first_name` both want `FIRST_NAME`. Neither
gets it — generating it for one would be arbitrary, and doing so silently is how the second one goes
missing until someone notices much later.

Static fields are never candidates, and are skipped without a warning.

## `@Fields` warns, `@FieldPath` errors

Every `@Fields` diagnostic is a warning; every `@FieldPath` diagnostic is an error.

**`@Fields` is observable through its own output.** If a constant is missing you find out the moment
you use it, as an ordinary "cannot find symbol" at the use site. Adding the annotation to a class
that already compiled must not stop it compiling.

**`@FieldPath` has no such backstop.** A wrong path is still a valid `String`. Whatever reflects on
it only fails at runtime, in a service, possibly in production.

Structurally: `FieldsProcessor` has one `warn` helper and no `error`; `FieldPathValidator` only
reports errors. Neither can drift into the other by accident.

## Why the javac internals are quarantined

`FieldsProcessor` is registered through `ServiceLoader`, so javac instantiates it in **every** build
that has this library on the classpath — including builds that never mention `@Fields`. If it named
a `com.sun.tools.javac` type in its own body, resolving that reference would throw
`IllegalAccessError` on any JDK 9+ compiler without the flags, and this library would break every
existing consumer.

So the injector sits behind an interface, is loaded by name inside a `try`, and a linkage failure
becomes the warning described above. `FieldPathValidator` avoids the problem differently: it is
written against supported API only, so it needs no flags at all.

## Why validation runs after analysis, not during processing

Annotation arguments are not attributed while a processing round runs — every value reads as
`<error>`. `FieldPathValidator` is therefore driven by a `TaskListener` on `ANALYZE`, once a file
has finished being attributed. A consequence: if a file has an unrelated compile error, attribution
may not finish, and these diagnostics may not appear for that file until it does.

## Why `removeFields` is read from the syntax tree

`DtoGeneratorProcessor` does not call `generateDto.removeFields()`. It reads the values out of the
annotation's syntax tree instead.

javac attributes annotation arguments *before* any processor runs. A constant that `@Fields` injects
during the same compilation does not exist at that moment, so the argument is recorded as an error
and the annotation proxy throws `AnnotationTypeMismatchException` — the whole build fails on the
library's own primary use case. The tree still says `User.PASSWORD`, and `PASSWORD` alone is enough
to find the field it stands for, so nothing depends on the value having resolved.

For the same reason the generator stays **silent** about a constant that names no field of the
source class, and lets the validator report it. An error raised from a processing round ends
processing, and the injected constants never reach the symbol table — burying the precise message
under a cascade of "cannot find symbol".

## Compatibility

`removeFields` used to take bare names (`removeFields = {"password"}`). Those are still understood by
the generator, but the compiler now rejects them, because nothing checks that such a string survives
a rename. Migration is mechanical: `"password"` becomes `User.PASSWORD`, and `User` gains `@Fields`.
