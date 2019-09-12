package io.github.ddimtirov.gradle.wrapperoverride;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import java.lang.instrument.Instrumentation;
import java.util.*;
import java.util.regex.Pattern;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

@SuppressWarnings({"unused", "UnusedAssignment", "ParameterCanBeLocal", "SameParameterValue", "WeakerAccess"})
public class GradleWrapperOverrideAgent {
    public static final boolean DEBUG_INSTRUMENTATION = Boolean.getBoolean("gwo.debug.instrumentation");
    public static final boolean DEBUG_RESOLUTION = Boolean.getBoolean("gwo.debug.resolution");

    public static Map<String, String> overrides = new HashMap<String, String>();
    public static Map<String, ValueTransformer> transforms = new HashMap<String, ValueTransformer>();
    public static synchronized void premain(String arguments, Instrumentation instrumentation) {
        Map<String, String> parsed = new HashMap<String, String>();
        for (String pair : arguments.split(",")) {
            String[] kv = pair.split("=", 2);
            String key = kv[0].trim();
            if (kv.length == 1) {
                if (key.length() == 0) continue;
                throw new IllegalArgumentException("Malformed pair '" + pair + "'. Format is: 'key=value" + "," + "key=value...' (NOTE: both key and value are trimmed)");
            }
            String value = kv[1].trim();

            if (key.endsWith("~")) {
                String tKey = key.substring(0, key.length() - 1);
                if (!transforms.containsKey(tKey)) transforms.put(tKey, new ValueTransformer());
                ValueTransformer transformer = transforms.get(tKey);

                // VALUE FORMAT: /REGEX/REPLACE/ where you can use any other char instead of slash, as long as it does not appear in the text
                String separator = String.valueOf(value.charAt(0));
                String[] parts = value.split(Pattern.quote(separator), -1);

                String err = null;
                if (parts.length!=4) {
                    err = "Transform pattern has format: /foo(.*)bar/baz\\1qux/ OR @foo(.*)bar@baz\\1qux@ OR /-4.10-all/-4.10.2-bun/";
                }
                if (err==null && !(parts[0].isEmpty() && parts[3].isEmpty())) err = "The transform pattern can not have text before and after the leading/trailing separator";
                if (err!=null) {
                    System.err.println(err);
                    System.err.println("GOT: '" + value +"' -> " + Arrays.toString(parts));
                    System.exit(1);
                }

                String regex = parts[1];
                String replace = parts[2];
                Pattern pattern = Pattern.compile(regex);

                transformer.addTransform(pattern, replace);
            } else {
                parsed.put(key, value);
            }
        }
        overrides.putAll(parsed);

        AgentBuilder.Transformer.ForAdvice adviser = new AgentBuilder.Transformer.ForAdvice();
        AgentBuilder builder = new AgentBuilder.Default().with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
        if (DEBUG_INSTRUMENTATION) {
            builder = builder.with(AgentBuilder.Listener.StreamWriting.toSystemError());
        }

        // https://github.com/gradle/gradle/blob/master/subprojects/wrapper/src/main/java/org/gradle/wrapper/WrapperExecutor.java#L119
        builder.type(named("org.gradle.wrapper.WrapperExecutor")).transform(adviser.advice(
                named("getProperty").and(takesArguments(3)),
                GradleWrapperOverrideAgent.class.getName() + "$OverrideProperty"
        )).installOn(instrumentation);
    }

    public static class ValueTransformer {
        List<Pattern> patterns = new ArrayList<Pattern>();
        List<String> replaces = new ArrayList<String>();

        void addTransform(Pattern pattern, String replace) {
            patterns.add(pattern);
            replaces.add(replace);
        }

        public String transformValue(String value) {
            String current = value;
            for (int i = 0; i < patterns.size(); i++) {
                Pattern pattern = patterns.get(i);
                String replace = replaces.get(i);
                current = pattern.matcher(current).replaceAll(replace);
            }
            return current;
        }
    }

    static class OverrideProperty {
        @Advice.OnMethodEnter(skipOn=String.class)
        static String lookupOverride(final String propertyName) {
            return overrides.get(propertyName);
        }

        @Advice.OnMethodExit()
        static void dumpException(
                @Advice.Argument(0) final String propName,
                @Advice.Enter final String override, @Advice.Return(readOnly = false) String retval
        ) {
            if (override != null) {
                if (DEBUG_RESOLUTION) System.err.printf("Overrode Gradle Wrapper property: %s = '%s'%n", propName, override);
                retval = override;
                return;
            }

            ValueTransformer transformer = transforms.get(propName);
            if (transformer!=null) {
                String transformed = transformer.transformValue(retval);
                if (DEBUG_RESOLUTION) System.err.printf("Transformed Gradle Wrapper property: %s = '%s' -> '%s'%n", propName, retval, transformed);
                retval = transformed;
            }
        }
    }
}
