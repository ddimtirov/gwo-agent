Have you ever needed to build a project using Gradle Wrapper pointing to distribution inaccessible through your company
 firewall? Typically we modify the `wrapper.properties`, resulting in a "dirty" build, or ignore the Wrapper
altogether and build with a locally installed Gradle distribution.

Now there is another way! The `gwo-agent` allows you to override or filter any property specified in `gradle.properties`
giving you extra control over the build distribution, without modifying the checked out sources.

All you need is to download the latest version:

````bash
curl -o gwo-agent.jar https://jitpack.io/com/github/ddimtirov/gwo-agent/1.0.0/gwo-agent-master.jar
````

And inject the agent into the Gradle wrapper using environment variable such as `GRADLE_OPTS`:

````bash
export GRADLE_OPTS=-javaagent:gwo-agent.jar=distributionUrl=https://mymirror/gradle-4.10-all.zip
````

Or in case of high security environments, specify the sha256 checksum too:

````bash
export GRADLE_OPTS=-javaagent:gwo-agent.jar=distributionUrl=https://mymirror/gradle-4.10-all.zip,distributionSha256Sum=371cb9fbebbe9880d147f59bab36d61eee122854ef8c9ee1ecf12b82368bcf10
````

Once this is done, you may use the standard agent and `gradlew` script as normal:

````bash
gradlew clean pub
````

***Note:*** If `GRADLE_OPTS` is already used by your build, you may as well use `JAVA_TOOL_OPTIONS` and `_JAVA_OPTIONS`, 
though keep in mind these will impact any other Java process.
