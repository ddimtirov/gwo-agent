# gwo-agent

Have you ever needed to build a project using Gradle Wrapper pointing to distribution inaccessible through your company
 firewall? Typically we modify the `wrapper.properties`, resulting in a "dirty" build, or ignore the Wrapper
altogether and build with a locally installed Gradle distribution.

Now there is another way! The `gwo-agent` allows you to override or filter any property specified in `gradle.properties`
giving you extra control over the build distribution, without modifying the checked out sources.

## Basic usage

All you need is to download the latest version  [![Release](https://jitpack.io/v/ddimtirov/gwo-agent.svg)](https://jitpack.io/#ddimtirov/gwo-agent) or use `curl`:

```sh
curl -o gwo-agent.jar https://jitpack.io/com/github/ddimtirov/gwo-agent/1.2.0/gwo-agent-1.2.0.jar
```

And inject the agent into the Gradle wrapper using environment variable such as `GRADLE_OPTS`:

```sh
export GRADLE_OPTS=-javaagent:gwo-agent.jar=distributionUrl=https://mymirror/gradle-4.10-all.zip
```

Or in case of high security environments, specify the sha256 checksum too:

```sh
export GRADLE_OPTS=-javaagent:gwo-agent.jar=distributionUrl=https://mymirror/gradle-4.10-all.zip,distributionSha256Sum=371cb9fbebbe9880d147f59bab36d61eee122854ef8c9ee1ecf12b82368bcf10
```

Once this is done, you may use the standard agent and `gradlew` script as normal:

```sh
gradlew --version
gradlew clean pub
```

***Note:*** If `GRADLE_OPTS` is already used by your build, you may as well use `JAVA_TOOL_OPTIONS` and `_JAVA_OPTIONS`,
though keep in mind these will impact any other Java process.

## Advanced usage

Often you would want to edit a small part of a property. For example:

```sh
# replace a blacklisted Gradle version with a good one
set GRADLE_OPTS=-javaagent:build/gwo-agent.jar=distributionUrl~=/4.10-bin/4.10.2-bin/

# use a specific Gradle version for all distributions, keeping the bin/all classifier
set GRADLE_OPTS=-javaagent:build/gwo-agent.jar=distributionUrl~=/-([0-9\.]+)-(bin|all)/4.10-$2/

# use same version from alternative download site
set GRADLE_OPTS=-javaagent:build/gwo-agent.jar=distributionUrl~=@https://services.gradle.org/distributions@https://mymirror/gradle/@
```

In summary, the syntax for property transformation is:

* separate the property-key and transform definition with `~=` (instead of `=`)
* the first character of the transform definition is treated as delimiter. It is similar to `sed s/pattern/replace/flags`, except we don't support flags.
* The pattern and replacement use the Java regex syntax.

## Debug options

Especially with the transform options, sometimes we may want to get extra insight in what the agent is doing.

Set the `gwo.debug.resolution` JVM property to `true` to dump details about the properties which are handled by the agent.

```sh
> set GRADLE_OPTS=-Dgwo.debug.resolution=true -javaagent:build/gwo-agent.jar=distributionUrl~=/4.10.2-bin/4.10-bin/,distributionSha256Sum=abc123

> gradlew --version
Transformed Gradle Wrapper property: distributionUrl = 'https://services.gradle.org/distributions/gradle-4.10.2-bin.zip' -> 'https://services.gradle.org/distributions/gradle-4.10-bin.zip'
Overrode Gradle Wrapper property: distributionSha256Sum = 'abc123'
```

Finally, if you need to check whether the agent is instrumenting the right classes, set
the `gwo.debug.instrumentation` JVM property to `true` to dump details about ByteBuddy's
class inspection and instrumentation.
