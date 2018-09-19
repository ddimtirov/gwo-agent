package io.github.ddimtirov.gradle.wrapperoverride;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

@SuppressWarnings({"unused", "UnusedAssignment", "ParameterCanBeLocal", "SameParameterValue", "WeakerAccess"})
public class GradleWrapperOverrideAgent {
    public static final boolean DEBUG_INSTRUMENTATION = Boolean.getBoolean("gwo.debug.instrumentation");
    public static Map<String, String> overrides = new HashMap<String, String>();
    public static synchronized void premain(String arguments, Instrumentation instrumentation) {
        Map<String, String> parsed = new HashMap<String, String>();
        for (String pair : arguments.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 1) {
                if (kv[0].trim().length() == 0) continue;
                throw new IllegalArgumentException("Malformed pair '" + pair + "'. Format is: 'key=value" + "," + "key=value...' (NOTE: both key and value are trimmed)");
            }

            // TODO: add syntax for filtering existing props: key~=/REGEX/REPLACE/
            parsed.put(kv[0].trim(), kv[1].trim());
        }
        overrides.putAll(parsed);

        AgentBuilder.Transformer.ForAdvice adviser = new AgentBuilder.Transformer.ForAdvice();
        AgentBuilder builder = new AgentBuilder.Default().with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
        if (DEBUG_INSTRUMENTATION) {
            builder = builder.with(AgentBuilder.Listener.StreamWriting.toSystemError());
        }
        builder.type(named("org.gradle.wrapper.WrapperExecutor")).transform(adviser.advice(
                named("getProperty").and(takesArguments(3)),
                GradleWrapperOverrideAgent.class.getName() + "$OverrideProperty"
        )).installOn(instrumentation);
    }

    static class OverrideProperty {
        @Advice.OnMethodEnter(skipOn=String.class)
        static String lookupOverride(final String propertyName) {
            return overrides.get(propertyName);
        }

        @Advice.OnMethodExit()
        static void dumpException(@Advice.Enter final String override, @Advice.Return(readOnly = false) String retval) {
            if (override!=null) retval=override;
        }
    }

}