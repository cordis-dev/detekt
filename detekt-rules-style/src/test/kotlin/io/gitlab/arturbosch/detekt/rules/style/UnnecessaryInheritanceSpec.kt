package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UnnecessaryInheritanceSpec {
    val subject = UnnecessaryInheritance(Config.empty)

    @Nested
    inner class `check inherit classes` {

        @Test
        fun `has unnecessary super type declarations`() {
            val findings = subject.lint(
                """
                class A : Any()
                class B : Object()
                """
            )
            assertThat(findings).hasSize(2)
        }

        @Test
        fun `has no unnecessary super type declarations`() {
            val findings = subject.lint("class C : An()")
            assertThat(findings).isEmpty()
        }
    }
}