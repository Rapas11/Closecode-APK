package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocksTest {

    // -- depth scanning -----------------------------------------------------------------

    @Test
    fun bracesInsideStringsAndCommentsDoNotChangeTheDepth() {
        // Built line by line because the raw string under test contains a raw string.
        val source = listOf(
            "fun a() {",
            "    val s = \"}\"",
            "    val t = \"{\"",
            "    /* } */",
            "    // {",
            "    val raw = \"\"\"",
            "        }",
            "    \"\"\"",
            "    val c = '}'",
            "    val map = mapOf(\"k\" to \"}\")",
            "}",
        ).joinToString("\n")

        val depths = computeDepths(source)
        assertEquals(0, depths.brace[0])
        assertEquals(0, depths.brace[depths.brace.lastIndex])
        assertEquals(11, depths.lineCount)
    }

    @Test
    fun theDepthArrayCoversEveryLinePlusTheEndOfTheFile() {
        val source = "a\nb\nc"
        val depths = computeDepths(source)
        assertEquals(4, depths.brace.size)
        assertEquals(4, depths.paren.size)
        assertEquals(depths.lineCount, 3)
    }

    @Test
    fun aTrailingNewlineStillProducesOneSlotPerLinePlusTheEnd() {
        val source = "a\nb\n"
        val depths = computeDepths(source)
        assertEquals(4, depths.brace.size)
    }

    // -- tree shape ---------------------------------------------------------------------

    @Test
    fun aClassCutsIntoClassMembersAndStatements() {
        val source = """
            class MainActivity {
                fun onCreate() {
                    setContentView(R.layout.main)
                    bindViews()
                }

                fun bindViews() {
                    title.text = "Hi"
                }
            }
        """.trimIndent()

        val tree = buildBlockTree(source)
        assertEquals(1, tree.size)

        val cls = tree[0]
        assertEquals(BlockKind.Declaration, cls.kind)
        assertEquals("class MainActivity", cls.title)
        assertEquals(2, cls.children.size)
        assertEquals(listOf("fun onCreate()", "fun bindViews()"), cls.children.map { it.title })
        assertEquals(BlockKind.Function, cls.children[0].kind)

        val statements = cls.children[0].children
        assertEquals(
            listOf("setContentView(R.layout.main)", "bindViews()"),
            statements.map { it.title },
        )
        assertTrue(statements.all { it.kind == BlockKind.Statement })
    }

    @Test
    fun topLevelDeclarationsAreSeparateRoots() {
        val source = """
            package com.example

            import android.os.Bundle

            class MainActivity {
                fun onCreate() {}
            }

            private fun helper() = 1
        """.trimIndent()

        val tree = buildBlockTree(source)
        assertEquals(4, tree.size)
        assertEquals(
            listOf("package com.example", "import android.os.Bundle", "class MainActivity", "private fun helper() = 1"),
            tree.map { it.title },
        )
        assertEquals(BlockKind.Declaration, tree[2].kind)
        assertEquals(BlockKind.Function, tree[3].kind)
    }

    @Test
    fun plainTextFallsBackToOneBlockPerLine() {
        val source = "alpha\nbeta\ngamma"
        val tree = buildBlockTree(source)
        assertEquals(listOf("alpha", "beta", "gamma"), tree.map { it.title })
        assertTrue(tree.all { it.children.isEmpty() })
    }

    @Test
    fun annotationsStayGluedToTheDeclarationTheyDescribe() {
        val source = """
            @Composable
            @Preview
            fun Greeting() {
                Text("Hi")
            }
        """.trimIndent()

        val tree = buildBlockTree(source)
        assertEquals(1, tree.size)
        assertEquals("fun Greeting()", tree[0].title)
        assertEquals(1, tree[0].children.size)
    }

    @Test
    fun multilineExpressionsStayInOneBlock() {
        val source = """
            val items = listOf(
                "one",
                "two",
            )

            val other = 1
        """.trimIndent()

        val tree = buildBlockTree(source)
        assertEquals(2, tree.size)
        assertEquals("val items = listOf(", tree[0].title)
        assertEquals("val other = 1", tree[1].title)
    }

    // -- lossless round trip ------------------------------------------------------------

    @Test
    fun concatenatingTheRootBlocksReproducesTheFileExactly() {
        val source = """
            package a

            class A {
                fun f() {
                    if (true) {
                        g()
                    } else {
                        h()
                    }
                }
            }

            fun z() {
                val map = mapOf("k" to "}")
                println(map)
            }
        """.trimIndent()

        val tree = buildBlockTree(source)
        val lines = source.split('\n')
        val rebuilt = ArrayList<String>()
        tree.forEach { node -> rebuilt.addAll(lines.subList(node.startLine, node.endLine + 1)) }
        assertEquals(source, rebuilt.joinToString("\n"))
    }

    // -- reordering ---------------------------------------------------------------------

    @Test
    fun movingARootBlockRewritesTheFileWithoutTouchingAnyLine() {
        val source = """
            fun first() {
                a()
            }

            fun second() {
                b()
            }

            fun third() {
                c()
            }
        """.trimIndent()

        val tree = buildBlockTree(source)
        // "first" is dragged past both neighbours, so it lands after "third".
        val moved = reorderBlocks(source, tree, emptyList(), 0, 2)

        assertEquals(
            """
            fun second() {
                b()
            }

            fun third() {
                c()
            }

            fun first() {
                a()
            }
            """.trimIndent(),
            moved,
        )
        // Every original line survives; only their order changed.
        assertEquals(
            source.split('\n').toSet(),
            moved.split('\n').toSet(),
        )
    }

    @Test
    fun movingAMemberInsideAClassKeepsTheBracesWhereTheyBelong() {
        val source = """
            class A {
                fun one() {
                    x()
                }

                fun two() {
                    y()
                }
            }
        """.trimIndent()

        val tree = buildBlockTree(source)
        // Dropped above its only neighbour, so "two" takes the first slot.
        val moved = reorderBlocks(source, tree, listOf(0), 1, -1)

        assertEquals(
            """
            class A {
                fun two() {
                    y()
                }

                fun one() {
                    x()
                }
            }
            """.trimIndent(),
            moved,
        )
    }

    @Test
    fun movingAStatementInsideAFunctionKeepsTheSignatureAndTheClose() {
        val source = """
            fun f() {
                one()
                two()
                three()
            }
        """.trimIndent()

        val tree = buildBlockTree(source)
        val moved = reorderBlocks(source, tree, listOf(0), 0, 2)

        assertEquals(
            """
            fun f() {
                two()
                three()
                one()
            }
            """.trimIndent(),
            moved,
        )
    }

    @Test
    fun anIllegalMoveIsIgnoredRatherThanCorruptingTheFile() {
        val source = "fun f() {\n    a()\n}\n"
        val tree = buildBlockTree(source)
        assertEquals(source, reorderBlocks(source, tree, emptyList(), 0, 5))
        assertEquals(source, reorderBlocks(source, tree, listOf(0), 0, 0))
        assertEquals(source, reorderBlocks(source, tree, listOf(9), 0, 1))
        assertEquals(source, reorderBlocks(source, tree, listOf(0), 7, -1))
    }

    // -- index helpers ------------------------------------------------------------------

    @Test
    fun moveAfterPlacesTheBlockUnderTheRowItWasDroppedOn() {
        val top = mutableListOf("a", "b", "c")
        top.moveAfter(0, 2)
        assertEquals(listOf("b", "c", "a"), top)

        val under = mutableListOf("a", "b", "c")
        under.moveAfter(0, 1)
        assertEquals(listOf("b", "a", "c"), under)

        val first = mutableListOf("a", "b", "c")
        first.moveAfter(2, -1)
        assertEquals(listOf("c", "a", "b"), first)

        val samePlace = mutableListOf("a", "b", "c")
        samePlace.moveAfter(1, 1)
        assertEquals(listOf("a", "b", "c"), samePlace)
    }

    @Test
    fun parentPathDropsTheLastIndexAndLeavesRootAlone() {
        assertEquals(listOf(0, 2), parentPath(listOf(0, 2, 1)))
        assertEquals(emptyList<Int>(), parentPath(listOf(3)))
        assertEquals(emptyList<Int>(), parentPath(emptyList()))
    }

    // -- outline ------------------------------------------------------------------------

    @Test
    fun collapsingAHiddenBlockHidesOnlyItsOwnSubtree() {
        val source = """
            class A {
                fun one() {
                    x()
                }
            }

            fun other() {
                y()
            }
        """.trimIndent()

        val tree = buildBlockTree(source)
        val all = flattenBlocks(tree, emptySet())
        assertTrue(all.size > 2)

        val hidden = flattenBlocks(tree, setOf(tree[0].id))
        // Collapsing the class removes its subtree...
        assertTrue(hidden.none { it.path.size > 1 && it.path.first() == 0 })
        // ...and leaves the other root's children exactly where they were.
        assertEquals(listOf("class A", "fun other()", "y()"), hidden.map { it.node.title })
    }

    @Test
    fun idsArePathsSoTwoBlocksWithTheSameTitleStillDiffer() {
        val source = """
            class A {
                fun run() {}

                fun run() {}
            }
        """.trimIndent()

        val tree = buildBlockTree(source)
        val children = tree[0].children
        assertEquals(2, children.size)
        assertTrue(children[0].id != children[1].id)
    }

    // -- the add-a-block sheet ------------------------------------------------------------

    @Test
    fun everyStatementSlotWritesSomethingAndHasACaretThatFits() {
        assertTrue(StatementSlots.isNotEmpty())
        StatementSlots.forEach { template ->
            assertTrue("slot ${template.id} writes nothing", template.lines.isNotEmpty())
            val line = template.lines[template.caretLine]
            assertTrue(
                "caret of ${template.id} lands past the end of its line",
                template.caretColumn <= line.length,
            )
        }
    }

    @Test
    fun everyCaretLandsOnThePlaceholderTheReaderIsMeantToTypeOver() {
        StatementSlots.forEach { template ->
            val line = template.lines[template.caretLine]
            assertTrue(
                "caret of ${template.id} lands on whitespace",
                template.caretColumn >= line.length || line[template.caretColumn] != ' ',
            )
        }
    }

    @Test
    fun slotIdsAreUniqueSoTheListCanBeUsedAsAKey() {
        val ids = StatementSlots.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun aSlotIsWrittenOnItsOwnLineAndTheLineItLandsOnSurvives() {
        val source = "fun a() {\n    val x = 1\n}"
        val (next, caret) = insertBlockAt(
            source,
            source.indexOf("val x"),
            StatementSlots.first { it.id == "if" },
        )
        assertEquals(
            listOf(
                "fun a() {",
                "    if (condition) {",
                "        // TODO",
                "    }",
                "    val x = 1",
                "}",
            ),
            next.split('\n'),
        )
        assertEquals(next.indexOf("condition"), caret)
    }

    @Test
    fun aSlotInheritsTheIndentOfTheLineItLandsOn() {
        // The caret sits on an eight-space blank line inside a function.
        val source = "fun a() {\n        \n}"
        val (next, caret) = insertBlockAt(
            source,
            source.indexOf('\n') + 1,
            StatementSlots.first { it.id == "val" },
        )
        assertEquals(
            listOf("fun a() {", "        val name = value", "        ", "}"),
            next.split('\n'),
        )
        assertEquals(next.indexOf("name"), caret)
    }

    @Test
    fun theCaretLandsOnTheLineTheTemplateItselfNamed() {
        val (next, caret) = insertBlockAt(
            "fun a() {\n\n}",
            10,
            StatementSlots.first { it.id == "try" },
        )
        assertEquals("    // TODO", next.split('\n')[2])
        assertEquals(next.indexOf("TODO"), caret)
    }

    @Test
    fun whateverTheSlotWritesLeavesTheBracesBalanced() {
        val source = "fun a() {\n\n}"
        StatementSlots.forEach { template ->
            val (next, _) = insertBlockAt(source, 10, template)
            val depths = computeDepths(next)
            assertEquals(
                "slot ${template.id} left the file unbalanced",
                0,
                depths.brace[depths.brace.lastIndex],
            )
        }
    }

    @Test
    fun aSlotAddedAtTheEndOfAFileWithNoTrailingNewlineKeepsThatLine() {
        val (next, caret) = insertBlockAt(
            "val a = 1",
            9,
            StatementSlots.first { it.id == "comment" },
        )
        assertEquals(listOf("// ", "val a = 1"), next.split('\n'))
        assertEquals(3, caret)
    }

    @Test
    fun theFirstBlockInAnEmptyBufferLandsAtTheTop() {
        val (next, caret) = insertBlockAt("", 0, StatementSlots.first { it.id == "if" })
        assertEquals("if (condition) {\n    // TODO\n}\n", next)
        assertEquals(4, caret)
    }

    @Test
    fun anOutOfRangeCaretIsClampedRatherThanThrown() {
        val source = "fun a() {\n}"
        val atEnd = insertBlockAt(source, source.length + 500, StatementSlots.first())
        val atStart = insertBlockAt(source, -20, StatementSlots.first())
        assertTrue(atEnd.second in 0..atEnd.first.length)
        assertTrue(atStart.second in 0..atStart.first.length)
        assertEquals(atEnd.first.split('\n').size, atStart.first.split('\n').size)
    }
}
