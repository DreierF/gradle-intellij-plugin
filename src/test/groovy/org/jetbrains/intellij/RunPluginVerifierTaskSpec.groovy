package org.jetbrains.intellij

import groovy.json.JsonSlurper

class RunPluginVerifierTaskSpec extends IntelliJPluginSpecBase {
    def 'run plugin verifier in specified version'() {
        given:
        writePluginXmlFile()
        buildFile << """
            version = "1.0.0"
            
            runPluginVerifier {
                verifierVersion = "1.241"
            }
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Starting the IntelliJ Plugin Verifier 1.241")
    }

    def 'run plugin verifier in the latest version'() {
        given:
        writePluginXmlFile()
        buildFile << """
            version = "1.0.0"
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        def url = new URL("https://api.bintray.com/packages/jetbrains/intellij-plugin-service/intellij-plugin-verifier/versions/_latest")
        def version = new JsonSlurper().parse(url)["name"]
        result.output.contains("Starting the IntelliJ Plugin Verifier $version")
    }

    def 'fallback to ide version specified in intellij configuration'() {
        given:
        writePluginXmlFile()
        buildFile << """
            version = "1.0.0"
            
            intellij {
                type = "CL"
                version = "2020.2"
            }
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        println result.output
        result.output.contains("PluginName:1.0.0 against CL-202.6397.106")
    }

    def 'test plugin against two IDEs'() {
        given:
        writeJavaFile()
        writePluginXmlFile()
        buildFile << """
            version = "1.0.0"
            
            runPluginVerifier {
                ides = ["IC-2020.2", "PY-2020.1"]
            }

            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Plugin PluginName:1.0.0 against IC-202.6397.94: Compatible")
        result.output.contains("Plugin PluginName:1.0.0 against PY-201.6668.115: Compatible")
    }

    def 'set verification reports directory'() {
        given:
        writeJavaFile()
        writePluginXmlFile()
        buildFile << """
            version = "1.0.0"
            
            runPluginVerifier {
                verificationReportsDir = "\${project.buildDir}/foo"
            }

            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        def directory = file("build/foo").absolutePath
        result.output.contains("Verification reports directory: /private$directory")
    }

    def 'fail on INVALID_PLUGIN by default'() {
        given:
        writeJavaFileWithDeprecation()
        buildFile << """
            version = "1.0.0"
            """.stripIndent()

        when:
        def result = buildAndFail(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Plugin descriptor 'plugin.xml' is not found")
        result.output.contains("org.gradle.api.GradleException: INVALID_PLUGIN")
    }

    def 'fail on Deprecated API usages'() {
        given:
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile << """
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                failureLevel = FailureLevel.DEPRECATED_USAGES
            }

            """.stripIndent()

        when:
        def result = buildAndFail(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Deprecated API usages")
        result.output.contains("org.gradle.api.GradleException: DEPRECATED_USAGES")
    }

    def 'pass on Deprecated API usages'() {
        given:
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile << """
            version = "1.0.0"
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Deprecated API usages")
        !result.output.contains("org.gradle.api.GradleException: DEPRECATED_USAGES")
    }

    def 'fail on any failureLevel'() {
        given:
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile << """
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                failureLevel = FailureLevel.ALL
            }

            """.stripIndent()

        when:
        def result = buildAndFail(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Deprecated API usages")
        result.output.contains("org.gradle.api.GradleException: DEPRECATED_USAGES")
    }

     def 'pass on any failureLevel'() {
        given:
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile << """
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                failureLevel = FailureLevel.NONE
            }

            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Deprecated API usages")
        !result.output.contains("org.gradle.api.GradleException: DEPRECATED_USAGES")
    }

    private void writeJavaFileWithDeprecation() {
        file('src/main/java/App.java')  << """  
            import java.lang.String;
            import org.jetbrains.annotations.NotNull;
            import com.intellij.openapi.util.text.StringUtil;
            class App {
                public static void main(@NotNull String[] strings) {
                    StringUtil.firstLetterToUpperCase("foo");
                }
            }
            """.stripIndent()
    }

    private void writePluginXmlFile() {
        pluginXml << """
            <idea-plugin version="2">
                <name>PluginName</name>
                <description>Plugin description</description>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.stripIndent()

    }
}
