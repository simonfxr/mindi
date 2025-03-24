package de.sfxr.mindi.testutil

import de.sfxr.mindi.annotations.Component

/**
 * Test component used for testing the component scanner with real JAR files.
 * This class will be packaged into a JAR file during tests.
 */
@Component("test_component_in_jar")
class ComponentScannerTestComponent {
    fun testMethod(): String {
        return "Hello from test component"
    }
}