/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsWrongGenericArgumentsOrderInspectionTest : RsInspectionsTestBase(RsWrongGenericArgumentsOrderInspection::class) {

    fun `test mismatch (expr)`() = checkByText("""
        struct Foo<T>(T);
        struct Bar<const N: i32>;
        fn foo<T>() {}
        fn bar<const N: i32>() {}
        fn baz<'a, T, const N: i32>(
            p1: Foo<T>,
            p2: Foo<<error descr="Constant provided when a type was expected [E0747]">N</error>>,
            p3: Bar<<error descr="Type provided when a constant was expected [E0747]">T</error>>,
            p4: Bar<N>
        ) {
            foo::<T>();
            foo::<<error descr="Constant provided when a type was expected [E0747]">N</error>>();
            bar::<<error descr="Type provided when a constant was expected [E0747]">T</error>>();
            bar::<N>();
        }
    """)

    fun `test const argument ambiguity`() = checkFixByText("Enclose the expression in braces", """
        struct N { f: i32 }
        fn foo<const N: i32>() {
            foo::<<error descr="Type provided when a constant was expected [E0747]">N/*caret*/</error>>();
        }
    """, """
        struct N { f: i32 }
        fn foo<const N: i32>() {
            foo::<{ N }>();
        }
    """)
}
