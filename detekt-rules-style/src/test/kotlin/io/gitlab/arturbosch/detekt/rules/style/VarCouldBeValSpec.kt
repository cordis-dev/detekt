package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.compileAndLint
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class VarCouldBeValSpec(val env: KotlinCoreEnvironment) {
    val subject = VarCouldBeVal()

    @Nested
    inner class `file-level declarations` {
        @Test
        fun `does not report non-private variables`() {
            val code = """
                var a = 1
                internal var b = 2
            """.trimIndent()

            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }

        @Test
        fun `reports private variables that are never re-assigned`() {
            val code = """
                private var a = 1
                
                fun foo() {
                    val fizz = "BUZZ"
                }
            """.trimIndent()

            assertThat(subject.compileAndLintWithContext(env, code)).hasSize(1)
        }

        @Test
        fun `does not report private variables that are re-assigned`() {
            val code = """
                private var a = 1

                fun foo() {
                    a = 2
                }
            """

            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }
    }

    @Nested
    inner class `class-level declarations` {
        @Test
        fun `does not report non-private variables in non-private classes`() {
            val code = """
                class A {
                    var a = 1
                    internal var b = 1
                }
                internal class B {
                    var a = 1
                    internal var b = 1
                }
            """.trimIndent()
            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }

        @Test
        fun `does not report non-private variables in non-private objects`() {
            val code = """
                object A {
                    var a = 1
                    internal var b = 1
                }
                internal object B {
                    var a = 1
                    internal var b = 1
                }
            """.trimIndent()
            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }

        @Test
        fun `does not report variables that are re-assigned`() {
            val code = """
                class A {
                    private var a = 1
                    
                    fun foo() {
                        a = 2
                    }
                }
            """.trimIndent()
            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }

        @Test
        fun `reports variables that are not re-assigned`() {
            val code = """
                class A {
                    private var a = 1
                }
            """.trimIndent()
            assertThat(subject.compileAndLintWithContext(env, code)).hasSize(1)
        }
    }

    @Nested
    inner class `local declarations in functions` {

        @Test
        fun `does not report variables that are re-assigned`() {
            val code = """
            fun test() {
                var a = 1
                a = 2
            }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }

        @Test
        fun `does not report variables that are re-assigned with assignment operator`() {
            val code = """
            fun test() {
                var a = 1
                a += 2
            }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }

        @Test
        fun `does not report variables that are re-assigned with postfix operators`() {
            val code = """
            fun test() {
                var a = 1
                a++
            }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }

        @Test
        fun `does not report variables that are re-assigned with infix operators`() {
            val code = """
            fun test() {
                var a = 1
                --a
            }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }

        @Test
        fun `does not report variables that are re-assigned inside scope functions`() {
            val code = """
            fun test() {
                var a = 1
                a.also {
                    a = 2
                }
            }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }

        @Test
        fun `reports variables that are not re-assigned, but used in expressions`() {
            val code = """
            fun test() {
                var a = 1
                val b = a + 2
            }
            """
            val findings = subject.compileAndLintWithContext(env, code)

            assertThat(findings).hasSize(1)
            assertThat(findings[0].entity.signature).isEqualTo("Test.kt\$var a = 1")
        }

        @Test
        fun `reports variables that are not re-assigned, but used in function calls`() {
            val code = """
            fun test() {
                var a = 1
                println(a)
            }
            """
            val findings = subject.compileAndLintWithContext(env, code)

            assertThat(findings).hasSize(1)
            assertThat(findings[0].entity.signature).isEqualTo("Test.kt\$var a = 1")
        }

        @Test
        fun `reports variables that are not re-assigned, but shadowed by one that is`() {
            val code = """
            fun test() {
                var shadowed = 1
                fun nestedFunction() {
                    var shadowed = 2
                    shadowed = 3
                }
            }
            """
            val lint = subject.compileAndLintWithContext(env, code)

            assertThat(lint).hasSize(1)
            with(lint[0].entity) {
                assertThat(ktElement?.text).isEqualTo("var shadowed = 1")
            }
        }
    }

    @Nested
    inner class `this-prefixed properties - #1257` {

        @Test
        fun `finds unused field and local`() {
            val code = """
                fun createObject() = object {
                    private var myVar: String? = null
                    fun assign(value: String?) {
                        var myVar = value
                    }
                }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).hasSize(2)
        }

        @Test
        fun `should not report this-prefixed property`() {
            val code = """
                fun createObject() = object {
                    private var myVar: String? = null
                    fun assign(value: String?) {
                        this.myVar = value
                    }
                }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }

        @Test
        fun `should report unused local variable`() {
            val code = """
                fun createObject() = object {
                    private var myVar: String? = null
                    fun assign(value: String?) {
                        var myVar = value
                        this.myVar = value
                    }
                }
            """
            with(subject.compileAndLintWithContext(env, code)[0]) {
                assertThat(entity.ktElement?.text).isEqualTo("var myVar = value")
            }
        }
    }

    @Nested
    inner class `properties defined in anonymous object - #3805` {
        @Test
        fun `should report unassigned properties`() {
            val code = """
                fun test() {
                    val wrapper = object {
                        var test: Boolean = true
                    }
                }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).hasSize(1)
        }

        @Test
        fun `should not report assigned properties`() {
            val code = """
                fun test() {
                    val wrapper = object {
                        var test: Boolean = true
                    }
                    wrapper.test = false
                }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }

        @Test
        fun `should not report when a property overrides a var`() {
            val code = """
                    interface I {
                        var optionEnabled: Boolean
                    }
                    class Test {
                        val test = object : I {
                            override var optionEnabled: Boolean = false
                        }
                    }
                """
            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }

        @Test
        fun `should not report assigned properties that have accessors that are accessed`() {
            val code = """
                interface I {
                    var optionEnabled: Boolean
                }
                fun test(i: Int) {
                    val o = object: I {
                        override var optionEnabled: Boolean = false
                            get() = i == 1
                            set(value) { field = i != 1 && value }
                    }
                    o.optionEnabled = false
                }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }

        @Nested
        inner class `anonymous objects that escape` {
            @Test
            fun `does not report when an object initializes a variable directly`() {
                val code = """
                    interface I {
                        var optionEnabled: Boolean
                    }
                    fun test(): I {
                        val o = object: I {
                            override var optionEnabled: Boolean = false
                        }
                        return o
                    }
                """
                assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
            }

            @Test
            fun `does not report an object initializing a variable in an if-statement`() {
                val code = """
                    interface I {
                        var optionEnabled: Boolean
                    }
                    fun test(i: Int): I? {
                        val o = if (i % 2 == 0) {
                            object: I {
                                override var optionEnabled: Boolean = false
                            }
                        } else {
                            null
                        }
                        return o
                    }
                """
                assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
            }

            @Test
            fun `does not report when an object is assigned to a variable directly`() {
                val code = """
                    interface I {
                        var optionEnabled: Boolean
                    }
                    fun test(): I {
                        val o: I
                        o = object: I {
                            override var optionEnabled: Boolean = false
                        }
                        return o
                    }
                """
                assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
            }

            @Test
            fun `does not report when an object is assigned to a variable in an if-statement`() {
                val code = """
                    interface I {
                        var optionEnabled: Boolean
                    }
                    fun test(i: Int): I? {
                        val o: I?
                        o = if (i % 2 == 0) {
                            object: I {
                                override var optionEnabled: Boolean = false
                            }
                        } else {
                            null
                        }
                        return o
                    }
                """
                assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
            }

            @Test
            fun `does not report when an object is defined in a return statement directly`() {
                val code = """
                    interface I {
                        var optionEnabled: Boolean
                    }
                    fun test(): I {
                        return object: I {
                            override var optionEnabled: Boolean = false
                        }
                    }
                """
                assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
            }

            @Test
            fun `does not report when an object is when defined in a return statement via an if-statement`() {
                val code = """
                    interface I {
                        var optionEnabled: Boolean
                    }
                    fun test(i: Int): I? {
                        return if (i % 2 == 0) {
                            object: I {
                                override var optionEnabled: Boolean = false
                            }
                        } else {
                            null
                        }
                    }
                """
                assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
            }

            @Test
            fun `does not report when an object is defined as a function initializer`() {
                val code = """
                    interface I {
                        var optionEnabled: Boolean
                    }
                    fun test() = object: I {
                        override var optionEnabled: Boolean = false
                    }
                """
                assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
            }

            @Test
            fun `does not report when an object is defined as a function initializer via an if-statement`() {
                val code = """
                    interface I {
                        var optionEnabled: Boolean
                    }
                    fun test(i: Int) = if (i % 2 == 0) {
                        object: I {
                            override var optionEnabled: Boolean = false
                        }
                    } else null
                """
                assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
            }

            @Test
            fun `does not report when an object initializes a variable directly - without type solving`() {
                val code = """
                    interface I {
                        var optionEnabled: Boolean
                    }
                    fun test(): I {
                        val o = object: I {
                            override var optionEnabled: Boolean = false
                        }
                        return o
                    }
                """.trimIndent()
                assertThat(subject.compileAndLint(code)).isEmpty()
            }
        }
    }
}