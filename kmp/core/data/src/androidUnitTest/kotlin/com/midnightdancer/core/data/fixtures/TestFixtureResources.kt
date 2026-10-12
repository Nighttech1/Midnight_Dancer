package com.midnightdancer.core.data.fixtures

import java.io.File

/**
 * Loads golden JSON from `commonTest/resources/fixtures/`.
 * Prefers ClassLoader (after Gradle wires commonTest resources into androidUnitTest);
 * falls back to module-relative file path for local IDE runs.
 */
object TestFixtureResources {
    fun text(relativeUnderFixtures: String): String {
        val classpathPath = "fixtures/$relativeUnderFixtures"
        val fromClasspath =
            sequenceOf(
                TestFixtureResources::class.java.classLoader,
                Thread.currentThread().contextClassLoader,
                ClassLoader.getSystemClassLoader(),
            ).mapNotNull { cl -> cl?.getResourceAsStream(classpathPath) }
                .firstOrNull()

        if (fromClasspath != null) {
            return fromClasspath.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

        val candidates =
            listOf(
                File("src/commonTest/resources/$classpathPath"),
                File("core/data/src/commonTest/resources/$classpathPath"),
                File("../core/data/src/commonTest/resources/$classpathPath"),
            )
        val file = candidates.firstOrNull { it.isFile }
            ?: error(
                "Missing test fixture: $classpathPath " +
                    "(classpath empty; tried ${candidates.joinToString { it.absolutePath }})",
            )
        return file.readText(Charsets.UTF_8)
    }
}
