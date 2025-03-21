/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.ExpandMacros
import org.rust.MockEdition
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsConstant
import org.rust.lang.core.psi.ext.RsElement

class RsQuickDocumentationTest : RsDocumentationProviderTest() {
    fun `test fn`() = doTest("""
        /// Adds one to the number given.
        ///
        /// # Examples
        ///
        /// Some text
        ///
        fn add_one(x: i32) -> i32 {
            x + 1
        }

        fn main() {
            add_one(91)
             //^
        }
    """, """
        <div class='definition'><pre>test_package
        fn <b>add_one</b>(x: i32) -&gt; i32</pre></div>
        <div class='content'><p>Adds one to the number given.</p><h2>Examples</h2><p>Some text</p></div>
    """)

    fun `test pub fn`() = doTest("""
        pub fn foo() {}
              //^
    """, """
        <div class='definition'><pre>test_package
        pub fn <b>foo</b>()</pre></div>
    """)

    fun `test pub(crate) fn`() = doTest("""
        pub(crate) fn foo() {}
                     //^
    """, """
        <div class='definition'><pre>test_package
        pub(crate) fn <b>foo</b>()</pre></div>
    """)

    fun `test pub(self) fn`() = doTest("""
        pub(self) fn foo() {}
                     //^
    """, """
        <div class='definition'><pre>test_package
        pub(self) fn <b>foo</b>()</pre></div>
    """)

    fun `test pub(super) fn`() = doTest("""
        mod bar {
            pub(super) fn foo() {}
                         //^
        }
    """, """
        <div class='definition'><pre>test_package::bar
        pub(super) fn <b>foo</b>()</pre></div>
    """)

    fun `test crate fn`() = doTest("""
        mod bar {
            crate fn foo() {}
                    //^
        }
    """, """
        <div class='definition'><pre>test_package::bar
        crate fn <b>foo</b>()</pre></div>
    """)

    fun `test pub(in crate-bar) fn`() = doTest("""
        mod bar {
            pub(in crate::bar) fn foo() {}
                                //^
        }
    """, """
        <div class='definition'><pre>test_package::bar
        pub(in crate::bar) fn <b>foo</b>()</pre></div>
    """)

    fun `test const fn`() = doTest("""
        const fn foo() {}
                 //^
    """, """
        <div class='definition'><pre>test_package
        const fn <b>foo</b>()</pre></div>
    """)

    fun `test unsafe fn`() = doTest("""
        unsafe fn foo() {}
                 //^
    """, """
        <div class='definition'><pre>test_package
        unsafe fn <b>foo</b>()</pre></div>
    """)

    fun `test fn in extern block`() = doTest("""
        extern {
            fn foo();
              //^
        }
    """, """
        <div class='definition'><pre>test_package
        extern fn <b>foo</b>()</pre></div>
    """)

    fun `test fn in extern block with abi name`() = doTest("""
        extern "C" {
            fn foo();
              //^
        }
    """, """
        <div class='definition'><pre>test_package
        extern "C" fn <b>foo</b>()</pre></div>
    """)

    fun `test extern fn`() = doTest("""
        extern fn foo() {}
                 //^
    """, """
        <div class='definition'><pre>test_package
        extern fn <b>foo</b>()</pre></div>
    """)

    fun `test extern fn with abi name`() = doTest("""
        extern "C" fn foo() {}
                      //^
    """, """
        <div class='definition'><pre>test_package
        extern "C" fn <b>foo</b>()</pre></div>
    """)

    fun `test async fn`() = doTest("""
        async fn foo() {}
                //^
    """, """
        <div class='definition'><pre>test_package
        async fn <b>foo</b>()</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic fn`() = doTest("""
        fn foo<T: Into<String>>(t: T) {}
          //^
    """, """
        <div class='definition'><pre>test_package
        fn <b>foo</b>&lt;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;&gt;(t: T)</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic fn with where clause`() = doTest("""
        fn foo<T>(t: T) where T: Into<String> {}
          //^
    """, """
        <div class='definition'><pre>test_package
        fn <b>foo</b>&lt;T&gt;(t: T)<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,</pre></div>
    """)

    fun `test generic fn with const generic`() = doTest("""
        fn foo<'a, const N: usize, T>(x: &'a [T; N]) {}
          //^
    """, """
        <div class='definition'><pre>test_package
        fn <b>foo</b>&lt;&#39;a, const N: usize, T&gt;(x: &amp;&#39;a [T; N])</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test complex fn`() = doTest("""
        /// Docs
        /// More Docs
        pub const unsafe extern "C" fn foo<T: Into<String>, F>(t: T, f: F) where F: Ord {}
                                      //^
    """, """
        <div class='definition'><pre>test_package
        pub const unsafe extern "C" fn <b>foo</b>&lt;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;, F&gt;(t: T, f: F)<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;F: <a href="psi_element://Ord">Ord</a>,</pre></div>
        <div class='content'><p>Docs
        More Docs</p></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test fn with complex types`() = doTest("""
        fn foo<'a>(tuple: (String, &'a i32), f: fn(String) -> [Vec<u32>; 3]) -> Option<[Vec<u32>; 3]> {
          //^
            unimplemented!();
        }
    """, """
        <div class='definition'><pre>test_package
        fn <b>foo</b>&lt;&#39;a&gt;(tuple: (<a href="psi_element://String">String</a>, &amp;&#39;a i32), f: fn(<a href="psi_element://String">String</a>) -&gt; [<a href="psi_element://Vec">Vec</a>&lt;u32&gt;; 3]) -&gt; <a href="psi_element://Option">Option</a>&lt;[<a href="psi_element://Vec">Vec</a>&lt;u32&gt;; 3]&gt;</pre></div>
    """)

    fun `test method`() = doTest("""
        struct Foo;

        impl Foo {
            pub fn foo(&self) {}
                  //^
        }
    """, """
        <div class='definition'><pre>test_package<br>impl <a href="psi_element://Foo">Foo</a>
        pub fn <b>foo</b>(&amp;self)</pre></div>
    """)

    fun `test method with Self ret type`() = doTest("""
        struct Foo;

        impl Foo {
            pub fn foo(&self) -> Self { unimplemented!() }
                  //^
        }
    """, """
        <div class='definition'><pre>test_package<br>impl <a href="psi_element://Foo">Foo</a>
        pub fn <b>foo</b>(&amp;self) -&gt; Self</pre></div>
    """)

    fun `test generic struct method`() = doTest("""
        struct Foo<T>(T);

        impl<T> Foo<T> {
            pub fn foo(&self) {}
                  //^
        }
    """, """
        <div class='definition'><pre>test_package<br>impl&lt;T&gt; <a href="psi_element://Foo">Foo</a>&lt;T&gt;
        pub fn <b>foo</b>(&amp;self)</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic struct method with where clause`() = doTest("""
        struct Foo<T, F>(T, F);

        impl<T, F> Foo<T, F> where T: Ord, F: Into<String> {
            pub fn foo(&self) {}
                  //^
        }
    """, """
        <div class='definition'><pre>test_package<br>impl&lt;T, F&gt; <a href="psi_element://Foo">Foo</a>&lt;T, F&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Ord">Ord</a>,<br>&nbsp;&nbsp;&nbsp;&nbsp;F: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,
        pub fn <b>foo</b>(&amp;self)</pre></div>
    """)

    fun `test generic struct with const generic`() = doTest("""
        struct Foo<'a, T, const N: usize>(&'a [T; N]);
              //^
    """, """
        <div class='definition'><pre>test_package
        struct <b>Foo</b>&lt;&#39;a, T, const N: usize&gt;</pre></div>
    """)

    fun `test different comments`() = doTest("""
        /// Outer comment
        /// 111
        #[doc = "outer attribute"]
        /// Second outer comment
        /// 222
        #[doc = r###"second outer attribute"###]
        fn overly_documented() {
         //^
            #![doc = "inner attribute"]
            //! Inner comment
        }
    """, """
        <div class='definition'><pre>test_package
        fn <b>overly_documented</b>()</pre></div>
        <div class='content'><p>Outer comment
        111
        outer attribute
        Second outer comment
        222
        second outer attribute
        inner attribute
        Inner comment</p></div>
    """)

    fun `test struct`() = doTest("""
        struct Foo(i32);
              //^
    """, """
        <div class='definition'><pre>test_package
        struct <b>Foo</b></pre></div>
    """)

    fun `test pub struct`() = doTest("""
        pub struct Foo(i32);
                  //^
    """, """
        <div class='definition'><pre>test_package
        pub struct <b>Foo</b></pre></div>
    """)

    fun `test generic struct`() = doTest("""
        struct Foo<T>(T);
              //^
    """, """
        <div class='definition'><pre>test_package
        struct <b>Foo</b>&lt;T&gt;</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic struct with where clause`() = doTest("""
        struct Foo<T>(T) where T: Into<String>;
              //^
    """, """
        <div class='definition'><pre>test_package
        struct <b>Foo</b>&lt;T&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,</pre></div>
    """)

    fun `test enum`() = doTest("""
        enum Foo {
            //^
            Bar
        }
    """, """
        <div class='definition'><pre>test_package
        enum <b>Foo</b></pre></div>
    """)

    fun `test pub enum`() = doTest("""
        pub enum Foo {
               //^
            Bar
        }
    """, """
        <div class='definition'><pre>test_package
        pub enum <b>Foo</b></pre></div>
    """)

    fun `test generic enum`() = doTest("""
        enum Foo<T> {
            //^
            Bar(T)
        }
    """, """
        <div class='definition'><pre>test_package
        enum <b>Foo</b>&lt;T&gt;</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic enum with where clause`() = doTest("""
        enum Foo<T> where T: Into<String> {
             //^
            Bar(T)
        }
    """, """
        <div class='definition'><pre>test_package
        enum <b>Foo</b>&lt;T&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,</pre></div>
    """)

    fun `test enum variant`() = doTest("""
        enum Foo {
            /// I am a well documented enum variant
            Bar { field: i32 },
            Baz(i32),
        }

        fn main() {
            let _ = Foo::Bar;
                       //^
        }
    """, """
        <div class='definition'><pre>test_package::Foo::Bar
        </pre></div>
        <div class='content'><p>I am a well documented enum variant</p></div>
    """)

    fun `test trait`() = doTest("""
        trait Foo {
            //^
        }
    """, """
        <div class='definition'><pre>test_package
        trait <b>Foo</b></pre></div>
    """)

    fun `test pub trait`() = doTest("""
        pub trait Foo {
                 //^
        }
    """, """
        <div class='definition'><pre>test_package
        pub trait <b>Foo</b></pre></div>
    """)

    fun `test unsafe trait`() = doTest("""
        unsafe trait Foo {
                    //^
        }
    """, """
        <div class='definition'><pre>test_package
        unsafe trait <b>Foo</b></pre></div>
    """)

    fun `test generic trait`() = doTest("""
        trait Foo<T> {
             //^
        }
    """, """
        <div class='definition'><pre>test_package
        trait <b>Foo</b>&lt;T&gt;</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic trait with where clause`() = doTest("""
        trait Foo<T> where T: Into<String> {
             //^
        }
    """, """
        <div class='definition'><pre>test_package
        trait <b>Foo</b>&lt;T&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test type alias`() = doTest("""
        type Foo = Result<(), i32>;
            //^
    """, """
        <div class='definition'><pre>test_package
        type <b>Foo</b> = <a href="psi_element://Result">Result</a>&lt;(), i32&gt;</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test pub type alias`() = doTest("""
        pub type Foo = Result<(), i32>;
                //^
    """, """
        <div class='definition'><pre>test_package
        pub type <b>Foo</b> = <a href="psi_element://Result">Result</a>&lt;(), i32&gt;</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic type alias`() = doTest("""
        type Foo<T> = Result<T, i32>;
            //^
    """, """
        <div class='definition'><pre>test_package
        type <b>Foo</b>&lt;T&gt; = <a href="psi_element://Result">Result</a>&lt;T, i32&gt;</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic type alias with where clause`() = doTest("""
        type Foo<T> where T: Into<String> = Result<T, i32>;
             //^
    """, """
        <div class='definition'><pre>test_package
        type <b>Foo</b>&lt;T&gt; = <a href="psi_element://Result">Result</a>&lt;T, i32&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test impl assoc type`() = doTest("""
        trait Trait {
            type AssocType;
        }
        struct Foo(i32);
        impl Trait for Foo {
            type AssocType = Option<i32>;
                //^
        }
    """, """
        <div class='definition'><pre>test_package<br>impl <a href="psi_element://Trait">Trait</a> for <a href="psi_element://Foo">Foo</a>
        type <b>AssocType</b> = <a href="psi_element://Option">Option</a>&lt;i32&gt;</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic impl assoc type`() = doTest("""
        trait Trait {
            type AssocType;
        }
        struct Foo<T>(T);
        impl<T> Trait for Foo<T> {
            type AssocType = Option<T>;
                //^
        }
    """, """
        <div class='definition'><pre>test_package<br>impl&lt;T&gt; <a href="psi_element://Trait">Trait</a> for <a href="psi_element://Foo">Foo</a>&lt;T&gt;
        type <b>AssocType</b> = <a href="psi_element://Option">Option</a>&lt;T&gt;</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic impl assoc type with where clause`() = doTest("""
        trait Trait {
            type AssocType;
        }
        struct Foo<T>(T);
        impl<T> Trait for Foo<T> where T: Into<String> {
            type AssocType = Option<T>;
                //^
        }
    """, """
        <div class='definition'><pre>test_package<br>impl&lt;T&gt; <a href="psi_element://Trait">Trait</a> for <a href="psi_element://Foo">Foo</a>&lt;T&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,
        type <b>AssocType</b> = <a href="psi_element://Option">Option</a>&lt;T&gt;</pre></div>
    """)

    fun `test trait assoc type`() = doTest("""
        trait MyTrait {
            /// Documented
            type Awesome;
               //^
        }
    """, """
        <div class='definition'><pre>test_package::MyTrait
        type <b>Awesome</b></pre></div>
        <div class='content'><p>Documented</p></div>
    """)

    fun `test generic trait assoc type`() = doTest("""
        trait MyTrait<T> {
            /// Documented
            type Awesome;
               //^
        }
    """, """
        <div class='definition'><pre>test_package::MyTrait&lt;T&gt;
        type <b>Awesome</b></pre></div>
        <div class='content'><p>Documented</p></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic trait assoc type with where clause`() = doTest("""
        trait MyTrait<T> where T: Into<String> {
            /// Documented
            type Awesome;
               //^
        }
    """, """
        <div class='definition'><pre>test_package::MyTrait&lt;T&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,
        type <b>Awesome</b></pre></div>
        <div class='content'><p>Documented</p></div>
    """)

    fun `test const`() = doTest("""
        fn main() {
            /// Documented
            const AWESOME: i32 = 1;
                //^
        }
    """, """
        <div class='definition'><pre>test_package
        const <b>AWESOME</b>: i32 = 1</pre></div>
        <div class='content'><p>Documented</p></div>
    """)

    fun `test static`() = doTest("""
        fn main() {
            /// Documented
            static AWESOME: i32 = 1;
                 //^
        }
    """, """
        <div class='definition'><pre>test_package
        static <b>AWESOME</b>: i32 = 1</pre></div>
        <div class='content'><p>Documented</p></div>
    """)

    fun `test trait const`() = doTest("""
        trait MyTrait {
            /// Documented
            const AWESOME: i32;
                //^
        }
    """, """
        <div class='definition'><pre>test_package::MyTrait
        const <b>AWESOME</b>: i32</pre></div>
        <div class='content'><p>Documented</p></div>
    """)

    fun `test impl assoc const`() = doTest("""
        trait Trait {
            const AWESOME: u8;
        }
        struct Foo(i32);
        impl Trait for Foo {
            const AWESOME: u8 = 42;
                //^
        }
    """, """
        <div class='definition'><pre>test_package<br>impl <a href="psi_element://Trait">Trait</a> for <a href="psi_element://Foo">Foo</a>
        const <b>AWESOME</b>: u8 = 42</pre></div>
    """)

    fun `test trait method`() = doTest("""
        trait MyTrait {
            /// Documented
            fn my_func();
             //^
        }
    """, """
        <div class='definition'><pre>test_package::MyTrait
        fn <b>my_func</b>()</pre></div>
        <div class='content'><p>Documented</p></div>
    """)

    fun `test generic trait method`() = doTest("""
        trait MyTrait<T> {
            /// Documented
            fn my_func();
             //^
        }
    """, """
        <div class='definition'><pre>test_package::MyTrait&lt;T&gt;
        fn <b>my_func</b>()</pre></div>
        <div class='content'><p>Documented</p></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic trait method with where clause`() = doTest("""
        trait MyTrait<T> where T: Into<String> {
            /// Documented
            fn my_func();
             //^
        }
    """, """
        <div class='definition'><pre>test_package::MyTrait&lt;T&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,
        fn <b>my_func</b>()</pre></div>
        <div class='content'><p>Documented</p></div>
    """)

    fun `test trait method impl`() = doTest("""
        trait Trait {
            fn foo();
        }
        struct Foo;
        impl Trait for Foo {
            fn foo() {}
              //^
        }
    """, """
        <div class='definition'><pre>test_package<br>impl <a href="psi_element://Trait">Trait</a> for <a href="psi_element://Foo">Foo</a>
        fn <b>foo</b>()</pre></div>
    """)

    fun `test generic trait method impl`() = doTest("""
        trait Trait<T> {
            fn foo();
        }
        struct Foo<T>(T);
        impl<T, F> Trait<T> for Foo<F> {
            fn foo() {}
              //^
        }
    """, """
        <div class='definition'><pre>test_package<br>impl&lt;T, F&gt; <a href="psi_element://Trait">Trait</a>&lt;T&gt; for <a href="psi_element://Foo">Foo</a>&lt;F&gt;
        fn <b>foo</b>()</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test generic trait method impl with where clause`() = doTest("""
        trait Trait<T> {
            fn foo();
        }
        struct Foo<T>(T);
        impl<T, F> Trait<T> for Foo<F> where T: Ord, F: Into<String> {
            fn foo() {}
              //^
        }
    """, """
        <div class='definition'><pre>test_package<br>impl&lt;T, F&gt; <a href="psi_element://Trait">Trait</a>&lt;T&gt; for <a href="psi_element://Foo">Foo</a>&lt;F&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Ord">Ord</a>,<br>&nbsp;&nbsp;&nbsp;&nbsp;F: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,
        fn <b>foo</b>()</pre></div>
    """)

    fun `test trait method provided`() = doTest("""
        trait MyTrait {
            fn my_func() {
             //^
                //! Inner doc
            }
        }
    """, """
        <div class='definition'><pre>test_package::MyTrait
        fn <b>my_func</b>()</pre></div>
        <div class='content'><p>Inner doc</p></div>
    """)

    fun `test mod inner docstring`() = doTest("""
        /// *outer*
        mod foo {
            //! **inner**
            fn bar() {}
        }

        fn main() {
            foo::bar()
          //^
        }
    """, """
        <div class='definition'><pre>test_package::foo
        </pre></div>
        <div class='content'><p><em>outer</em>
        <strong>inner</strong></p></div>
    """)

    fun `test fn inner docstring`() = doTest("""
        fn foo() {
            //! Inner doc.
        }

        fn main() {
            foo()
          //^
        }
    """, """
        <div class='definition'><pre>test_package
        fn <b>foo</b>()</pre></div>
        <div class='content'><p>Inner doc.</p></div>
    """)

    fun `test file inner docstring`() = doTest("""
        //! Module level docs
        //! Module level docs

        fn foo() { }

        fn main() {
            self::foo();
            //^
        }
    """, """
        <div class='definition'><pre>test_package
        </pre></div>
        <div class='content'><p>Module level docs
        Module level docs</p></div>
    """)

    fun `test macro outer docstring 1`() = doTest("""
        /// Outer documentation
        macro_rules! makro {
                   //^
            () => { };
        }

        fn main() {
        }
    """, """
        <div class='definition'><pre>test_package
        macro <b>makro</b></pre></div>
        <div class='content'><p>Outer documentation</p></div>
    """)

    fun `test macro outer docstring 2`() = doTest("""
        pub mod foo {
            /// Outer documentation
            macro_rules! makro {
                       //^
                () => { };
            }
        }

        fn main() {
        }
    """, """
        <div class='definition'><pre>test_package
        macro <b>makro</b></pre></div>
        <div class='content'><p>Outer documentation</p></div>
    """)

    fun `test macro 2 outer docs 1`() = doTest("""
        pub struct Foo;

        /// Outer doc
        /// [link](Foo)
        pub macro bar() {}
                 //^
    """, """
        <div class='definition'><pre>test_package
        pub macro <b>bar</b></pre></div>
        <div class='content'><p>Outer doc
        <a href="psi_element://Foo">link</a></p></div>
    """)

    fun `test macro 2 outer docs 2`() = doTest("""
        pub mod foo_bar {
            pub struct Foo;

            /// Outer doc
            /// [link](Foo)
            pub macro bar() {}
        }           //^
    """, """
        <div class='definition'><pre>test_package::foo_bar
        pub macro <b>bar</b></pre></div>
        <div class='content'><p>Outer doc
        <a href="psi_element://Foo">link</a></p></div>
    """)

    fun `test qualified name`() = doTest("""
        mod q {
            /// Blurb.
            fn foo() {

            }
        }

        fn main() {
            q::foo();
                //^
        }
    """, """
        <div class='definition'><pre>test_package::q
        fn <b>foo</b>()</pre></div>
        <div class='content'><p>Blurb.</p></div>
    """)

    fun `test fn arg`() = doTest("""
        fn foo(x: i32) -> i32 { x }
             //^
    """, """
        <div class='definition'><pre>value parameter <b>x</b>: i32</pre></div>
    """)

    fun `test variable`() = doTest("""
        fn main() {
            let x = "bar";
            println!(x);
                   //^
        }
    """, """
        <div class='definition'><pre>variable <b>x</b>: &amp;str</pre></div>
    """)

    fun `test generic enum variable`() = doTest("""
        enum E<T> {
            L,
            R(T),
        }

        fn main() {
            let x = E::R(123);
            x;
          //^
        }
    """, """
        <div class='definition'><pre>variable <b>x</b>: E&lt;i32&gt;</pre></div>
    """)

    fun `test generic struct variable`() = doTest("""
        struct S<T> { s: T }

        fn main() {
            let x = S { s: 123 };
            x;
          //^
        }
    """, """
        <div class='definition'><pre>variable <b>x</b>: S&lt;i32&gt;</pre></div>
    """)

    fun `test variable type alias`() = doTest("""
        struct S;
        type T = S;

        fn main() {
            let x: T = S;
              //^
        }
    """, """
        <div class='definition'><pre>variable <b>x</b>: T</pre></div>
    """)

    fun `test tuple destructuring`() = doTest("""
        fn main() {
            let (a, b) = (1, ("foo", 1));
                  //^
        }
    """, """
        <div class='definition'><pre>variable <b>b</b>: (&amp;str, i32)</pre></div>
    """)

    fun `test conditional binding`() = doTest("""
        fn main() {
            let x: (i32, f64) = unimplemented!();
            if let (1, y) = x {
                     //^
            }
        }
    """, """
        <div class='definition'><pre>condition binding <b>y</b>: f64</pre></div>
    """)

    fun `test struct field`() = doTest("""
        struct Foo {
            /// Documented
            pub foo: i32
        }

        fn main() {
            let x = Foo { foo: 8 };
            x.foo
              //^
        }
    """, """
        <div class='definition'><pre>test_package::Foo
        pub <b>foo</b>: i32</pre></div>
        <div class='content'><p>Documented</p></div>
    """)

    fun `test enum variant field`() = doTest("""
        enum Foo {
            Bar {
                /// Documented
                baz: i32
            }
        }

        fn foo(f: Foo) {
            if let Foo::Bar { baz: x } = f {}
                             //^
        }
    """, """
        <div class='definition'><pre>test_package::Foo::Bar
        <b>baz</b>: i32</pre></div>
        <div class='content'><p>Documented</p></div>
    """)

    fun `test type parameter`() = doTest("""
        fn foo<T>() { unimplemented!() }
             //^
    """, """
        <div class='definition'><pre>type parameter <b>T</b></pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test type parameter with single bound`() = doTest("""
        use std::borrow::Borrow;

        fn foo<T: Borrow<Q>>() { unimplemented!() }
             //^
    """, """
        <div class='definition'><pre>type parameter <b>T</b>: <a href="psi_element://Borrow">Borrow</a>&lt;Q&gt;</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test type parameter with multiple bounds`() = doTest("""
        use std::borrow::Borrow;
        use std::hash::Hash;

        fn foo<Q, T: Eq + Hash + Borrow<Q>>() { unimplemented!() }
                //^
    """, """
        <div class='definition'><pre>type parameter <b>T</b>: <a href="psi_element://Eq">Eq</a> + <a href="psi_element://Hash">Hash</a> + <a href="psi_element://Borrow">Borrow</a>&lt;Q&gt;</pre></div>
    """)

    fun `test type parameter with lifetime bound`() = doTest("""
        fn foo<'a, T: 'a>() { unimplemented!() }
                 //^
    """, """
        <div class='definition'><pre>type parameter <b>T</b>: &#39;a</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test type parameter with default value`() = doTest("""
        use std::collections::hash_map::RandomState;

        fn foo<T = RandomState>() { unimplemented!() }
             //^
    """, """
        <div class='definition'><pre>type parameter <b>T</b> = <a href="psi_element://RandomState">RandomState</a></pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test type parameter with bounds in where clause`() = doTest("""
        use std::borrow::Borrow;
        use std::hash::Hash;

        fn foo<Q, T>() where T: Eq + Hash + Borrow<Q> { unimplemented!() }
                //^
    """, """
        <div class='definition'><pre>type parameter <b>T</b>: <a href="psi_element://Eq">Eq</a> + <a href="psi_element://Hash">Hash</a> + <a href="psi_element://Borrow">Borrow</a>&lt;Q&gt;</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test type parameter with complex bounds`() = doTest("""
        use std::borrow::Borrow;
        use std::hash::Hash;

        fn foo<'a, Q, T: 'a + Eq>() where T: Hash + Borrow<Q> { unimplemented!() }
                    //^
    """, """
        <div class='definition'><pre>type parameter <b>T</b>: &#39;a + <a href="psi_element://Eq">Eq</a> + <a href="psi_element://Hash">Hash</a> + <a href="psi_element://Borrow">Borrow</a>&lt;Q&gt;</pre></div>
    """)

    fun `test don't create link for unresolved items`() = doTest("""
        fn foo() -> Foo { unimplemented!() }
           //^
    """, """
        <div class='definition'><pre>test_package
        fn <b>foo</b>() -&gt; Foo</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test assoc type bound`() = doTest("""
        trait Foo {
            type Bar;
        }

        fn foo<T>(t: T) where T: Foo, T::Bar: Into<String> {}
          //^
    """, """
        <div class='definition'><pre>test_package
        fn <b>foo</b>&lt;T&gt;(t: T)<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Foo">Foo</a>,<br>&nbsp;&nbsp;&nbsp;&nbsp;T::<a href="psi_element://T::Bar">Bar</a>: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,</pre></div>
    """)

    fun `test type qual`() = doTest("""
        trait Foo1 {
            type Bar;
        }

        trait Foo2 {
            type Bar;
        }

        struct S;

        impl Foo1 for S {
            type Bar = ();
        }

        impl Foo2 for S {
            type Bar = <Self as Foo1>::Bar;
                //^
        }
    """, """
        <div class='definition'><pre>test_package<br>impl <a href="psi_element://Foo2">Foo2</a> for <a href="psi_element://S">S</a>
        type <b>Bar</b> = &lt;Self as <a href="psi_element://Foo1">Foo1</a>&gt;::<a href="psi_element://&lt;Self as Foo1&gt;::Bar">Bar</a></pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test type value parameters 1`() = doTest("""
        fn run<F, T>(f: F, t: T) where F: FnOnce(T) { f(t); }
          //^
    """, """
        <div class='definition'><pre>test_package
        fn <b>run</b>&lt;F, T&gt;(f: F, t: T)<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;F: <a href="psi_element://FnOnce">FnOnce</a>(T),</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test type value parameters 2`() = doTest("""
        fn call<F, T, R>(f: F, t: T) -> R where F: FnOnce(T) -> R { f(t) }
          //^
    """, """
        <div class='definition'><pre>test_package
        fn <b>call</b>&lt;F, T, R&gt;(f: F, t: T) -&gt; R<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;F: <a href="psi_element://FnOnce">FnOnce</a>(T) -&gt; R,</pre></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test type value parameters 3`() = doTest("""
        pub trait Iter {
            type Item;

            fn scan<St, B, F>(self, initial_state: St, f: F) -> Scan<Self, St, F>
              //^
                where Self: Sized, F: FnMut(&mut St, Self::Item) -> Option<B> { unimplemented!() }
        }
    """, """
        <div class='definition'><pre>test_package::Iter
        fn <b>scan</b>&lt;St, B, F&gt;(self, initial_state: St, f: F) -&gt; Scan&lt;Self, St, F&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;Self: <a href="psi_element://Sized">Sized</a>,<br>&nbsp;&nbsp;&nbsp;&nbsp;F: <a href="psi_element://FnMut">FnMut</a>(&amp;mut St, Self::<a href="psi_element://Self::Item">Item</a>) -&gt; <a href="psi_element://Option">Option</a>&lt;B&gt;,</pre></div>
    """)

    fun `test markdown sections`() = doTest("""
        /// Some text
        ///
        /// # Section level 1
        ///
        /// Some text
        ///
        /// ## Section level 2
        ///
        /// Some other text
        struct Foo;
              //^
    """, """
        <div class='definition'><pre>test_package
        struct <b>Foo</b></pre></div>
        <div class='content'><p>Some text</p><h2>Section level 1</h2><p>Some text</p><h3>Section level 2</h3><p>Some other text</p></div>
    """)

    fun `test lang item links in doc comment`() = doTest("""
        struct Bar;
        /// [`Bar`]
        ///
        /// [`Bar`]: struct.Bar.html
        struct Foo;
              //^
    """, """
        <div class='definition'><pre>test_package
        struct <b>Foo</b></pre></div>
        <div class='content'><p><a href="psi_element://test_package/struct.Bar.html"><code>Bar</code></a></p></div>
    """)

    fun `test lang item links in doc comment 2`() = doTest("""
        enum Bar { V }
        mod foo {
            /// [`Bar`]
            ///
            /// [`Bar`]: ../enum.Bar.html
            struct Foo;
                 //^
        }
    """, """
        <div class='definition'><pre>test_package::foo
        struct <b>Foo</b></pre></div>
        <div class='content'><p><a href="psi_element://test_package/enum.Bar.html"><code>Bar</code></a></p></div>
    """)

    fun `test lang item links in doc comment 3`() = doTest("""
        struct Foo;

        impl Foo {
            /// [`Foo::bar`]
            ///
            /// [`Foo::bar`]: #method.bar
            fn foo(&self) {}
              //^
            fn bar(&self) {}
        }
    """, """
        <div class='definition'><pre>test_package<br>impl <a href="psi_element://Foo">Foo</a>
        fn <b>foo</b>(&amp;self)</pre></div>
        <div class='content'><p><a href="psi_element://test_package/struct.Foo.html#method.bar"><code>Foo::bar</code></a></p></div>
    """)

    fun `test trait item doc 1`() = doTest("""
        trait Foo {
            /// Trait doc
            fn foo();
        }
        struct Bar;

        impl Foo for Bar {
            /// Impl doc
            fn foo() {}
              //^
        }
    """, """
        <div class='definition'><pre>test_package<br>impl <a href="psi_element://Foo">Foo</a> for <a href="psi_element://Bar">Bar</a>
        fn <b>foo</b>()</pre></div>
        <div class='content'><p>Impl doc</p></div>
    """)

    fun `test trait item doc 2`() = doTest("""
        trait Foo {
            /// Trait doc
            type Baz;
        }
        struct Bar;

        impl Foo for Bar {
            type Baz = ();
               //^
        }
    """, """
        <div class='definition'><pre>test_package<br>impl <a href="psi_element://Foo">Foo</a> for <a href="psi_element://Bar">Bar</a>
        type <b>Baz</b> = ()</pre></div>
        <div class='content'><p>Trait doc</p></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test primitive type doc`() = doTestRegex("""
        fn foo() -> i32 {}
                   //^
    """, """
        <div class='definition'><pre>std
        primitive type <b>i32</b></pre></div><div class='content'><p>.+</p></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test primitive type doc in stdlib`() = doTestRegex("""
        const C: u32 = std::f64::DIGITS;
                                //^
    """, """
        <div class='definition'><pre>std
        primitive type <b>u32</b></pre></div><div class='content'><p>.+</p></div>
    """) {
        val element = findElementWithDataAndOffsetInEditor<RsElement>().first
        val const = element.reference?.resolve() as? RsConstant ?: error("Failed to resolve `${element.text}`")
        val originalElement = (const.typeReference as RsBaseType).path!!

        myFixture.openFileInEditor(const.containingFile.virtualFile)
        originalElement to originalElement.textOffset
    }

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test keyword doc`() = doTestRegex("""
        enum Foo { V1 }
        //^
    """, """
        <div class='definition'><pre>std
        keyword <b>enum</b></pre></div><div class='content'><p>.+</p></div>
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test async keyword doc`() = doTestRegex("""
        async fn foo() {}
        //^
    """, """
        <div class='definition'><pre>std
        keyword <b>async</b></pre></div><div class='content'><p>.+</p></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test dyn keyword doc`() = doTestRegex("""
        trait Foo {}
        fn foo(x: &dyn Foo) {}
                 //^
    """, """
        <div class='definition'><pre>std
        keyword <b>dyn</b></pre></div><div class='content'><p>.+</p></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test boolean value doc`() = doTestRegex("""
        fn main() {
            let a = false;
                    //^
        }
    """, """
        <div class='definition'><pre>std
        keyword <b>false</b></pre></div><div class='content'><p>.+</p></div>
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2015)
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test await doc 1`() = doTest("""
        fn main() {
            foo().await;
                  //^
        }
    """, null)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test await doc 2`() = doTestRegex("""
        fn main() {
            foo().await;
                  //^
        }
    """, """
        <div class='definition'><pre>std
        keyword <b>await</b></pre></div><div class='content'><p>.+</p></div>
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test keyword doc in stdlib`() = doTestRegex("""
        const C: u32 = std::f64::DIGITS;
                                //^
    """, """
        <div class='definition'><pre>std
        keyword <b>const</b></pre></div><div class='content'><p>.+</p></div>
    """) {
        val element = findElementWithDataAndOffsetInEditor<RsElement>().first
        val const = element.reference?.resolve() as? RsConstant ?: error("Failed to resolve `${element.text}`")
        val originalElement = const.const!!

        myFixture.openFileInEditor(const.containingFile.virtualFile)
        originalElement to originalElement.textOffset
    }

    @ExpandMacros
    fun `test documentation provided via macro definition 1`() = doTest("""
        macro_rules! foobar {
            ($ name:ident) => {
                /// Say hello!
                pub fn $ name() {
                    println!("Hello!")
                }
            }
        }

        foobar!(foo);

        fn main() {
            foo();
            //^
        }
    """, """
        <div class='definition'><pre>test_package
        pub fn <b>foo</b>()</pre></div>
        <div class='content'><p>Say hello!</p></div>
    """)

    @ExpandMacros
    fun `test documentation provided via macro definition 2`() = doTest("""
        pub struct Bar {
            pub bar: i32
        }

        macro_rules! foobar {
            ($ Name:ident) => {
                impl $ Name {
                    /// Say hello
                    pub fn hello(&self) {
                        println!("hello: {}", self.bar)
                    }
                }
            }
        }

        foobar!(Bar);

        pub fn say_hello() {
            let foo = Bar { bar: 1 };
            foo.hello();
               //^
        }
    """, """
        <div class='definition'><pre>test_package<br>impl <a href="psi_element://Bar">Bar</a>
        pub fn <b>hello</b>(&amp; self)</pre></div>
        <div class='content'><p>Say hello</p></div>
    """)

    @ExpandMacros
    fun `test documentation provided via macro call`() = doTest("""
        macro_rules! foo {
            ($ i:item) => { $ i }
        }
        foo! {
            /// Some docs
            fn foo() {}
        }
        fn main() {
            foo();
            //^
        }
    """, """
        <div class='definition'><pre>test_package
        fn <b>foo</b>()</pre></div>
        <div class='content'><p>Some docs</p></div>
    """)

    private fun doTest(@Language("Rust") code: String, @Language("Html") expected: String?)
        = doTest(code, expected, block = RsDocumentationProvider::generateDoc)

    private fun doTestRegex(
        @Language("Rust") code: String,
        @Language("Html") expected: String,
        findElement: () -> Pair<PsiElement, Int> = { findElementAndOffsetInEditor() }
    ) = doTest(code, Regex(expected.trimIndent(), setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)), findElement, RsDocumentationProvider::generateDoc)
}
