/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.lang.core.psi.ext.ArithmeticOp
import org.rust.lang.core.types.infer.TypeInferenceMarks

class RsTypeAwareGenericResolveTest : RsResolveTestBase() {
    fun `test fn`() = checkByCode("""
        fn foo<T>() -> T {
             //X
            let x: T = unimplemented!();
                 //^
            }
    """)

    fun `test impl method`() = checkByCode("""
        struct S;

        impl S {
            fn foo<Param>(
                    //X
                param: Param
            ) {}      //^
        }
    """)

    fun `test trait method`() = checkByCode("""
        trait T {
            fn f<Param>()
                //X
                -> Param;
                    //^
        }
    """)

    fun `test struct`() = checkByCode("""
        struct S<Thing> {
                //X
            field: Vec<Thing>
                      //^
        }
    """)

    fun `test enum`() = checkByCode("""
        enum E<T> {
             //X
            V(T)
            //^
        }
    """)

    fun `test trait`() = checkByCode("""
        trait T<Param> {
                //X
            fn new() -> Param;
                        //^
        }
    """)

    fun `test impl`() = checkByCode("""
        struct S<T> { field: T }

        impl<T> S<T> {
           //X
            fn foo() -> T { }
                      //^
        }
    """)

    fun `test type alias`() = checkByCode("""
        use std::result;

        pub type Result<T> =
                      //X
            result::Result<T, Error>;
                         //^
    """)

    fun `test no leak in enum`() = checkByCode("""
        enum E<T> { X }

        fn main() { let _ = E::T; }
                             //^ unresolved
    """)

    fun `test no leak in struct`() = checkByCode("""
        struct S<T>;

        fn main() { let _: S::T = unreachable!(); }
                            //^ unresolved
    """)

    fun `test don't check type parameter name when looking for impls`() = checkByCode("""
        struct S<FOO> { field: FOO }

        fn main() {
            let s: S = S;

            s.transmogrify();
                //^
        }

        impl<BAR> S<BAR> {
            fn transmogrify(&self) { }
                //X
        }
    """)

    fun `test method call on trait from bound`() = checkByCode("""
        trait Spam { fn eggs(&self); }
                        //X

        fn foo<T: Spam>(x: T) { x.eggs() }
                                  //^
    """)

    fun `test method call on trait from bound in where`() = checkByCode("""
        trait Spam { fn eggs(&self); }
                        //X

        fn foo<T>(x: T) where T: Spam { x.eggs() }
                                          //^
    """)

    fun `test method call on trait from bound's closure`() = checkByCode("""
        trait A { fn foo(&self) {} }
                    //X
        trait B : A {}
        trait C : B {}
        trait D : C {}

        struct X;
        impl D for X {}

        fn bar<T: D>(x: T) { x.foo() }
                              //^
    """)

    fun `test method call on trait with cyclic bounds`() = checkByCode("""
        trait A: B {}
        trait B: A {}

        fn bar<T: A>(x: T) { x.foo() }
                              //^ unresolved
    """)

    fun `test trait bound propagates type arguments`() = checkByCode("""
        trait I<A> {
            fn foo(&self) -> A;
        }

        struct S;
        impl S {
            fn bar(&self) {}
        }     //X

        fn baz<T: I<S>>(t: T) {
            t.foo().bar()
        }         //^
    """)

    fun `test method call on trait from bound on reference type`() = checkByCode("""
        trait Foo { fn foo(&self) {} }
                     //X
        fn foo<'a, T>(t: &'a T) where &'a T: Foo {
            t.foo();
        }   //^
    """)

    fun `test associated function call on trait from bound for type without type parameter`() = checkByCode("""
        struct S2;
        trait From<T> { fn from(_: T) -> Self; }
                         //X
        fn foo<T>(t: T) -> S2 where S2: From<T> {
            S2::from(t)
        }     //^
    """)

    fun `test method call on trait from bound for associated type`() = checkByCode("""
        trait Foo {
            type Item;
            fn foo(&self) -> Self::Item;
        }
        trait Bar<A> { fn bar(&self); }
                        //X
        fn baz<T1, T2>(t: T1)
            where T1: Foo,
                  T1::Item: Bar<T2> {
            t.foo().bar();
        }         //^
    """)

    fun `test method call on Self trait from bound for Self`() = checkByCode("""
        trait Foo {
            fn foo(&self) -> i32;
        }    //X

        trait Bar: Sized {
            fn bar(s: Self) where Self: Foo {
                s.foo();
            }   //^
        }
    """)

    fun `test method call on self trait from bound for Self`() = checkByCode("""
        trait Foo {
            fn foo(&self) -> i32;
        }    //X

        trait Bar: Sized {
            fn bar(&self) where Self: Foo {
                self.foo();
            }      //^
        }
    """)

    fun `test UFCS assoc function call on self trait from bound for Self`() = checkByCode("""
        trait Foo {
            fn foo() {}
        }    //X
        trait Bar {
            fn bar() where Self: Foo {
                Self::foo();
            }        //^
        }
    """)

    fun `test Result unwrap`() = checkByCode("""
        enum Result<T, E> { Ok(T), Err(E)}

        impl<T, E: fmt::Debug> Result<T, E> {
            pub fn unwrap(self) -> T { unimplemented!() }
        }

        struct S { field: u32 }
                    //X
        fn foo() -> Result<S, ()> { unimplemented!() }

        fn main() {
            let s = foo().unwrap();
            s.field;
            //^
        }
    """)

    fun `test unwrap with aliased Result`() = checkByCode("""
        enum Result<T, E> { Ok(T), Err(E)}
        impl<T, E: fmt::Debug> Result<T, E> {
            pub fn unwrap(self) -> T { unimplemented!() }
        }

        mod io {
            pub struct Error;
            pub type Result<T> = super::Result<T, Error>;
        }

        struct S { field: u32 }
                    //X
        fn foo() -> io::Result<S> { unimplemented!() }

        fn main() {
            let s = foo().unwrap();
            s.field;
              //^
        }
    """)

    fun `test generic function call`() = checkByCode("""
        struct S;
        impl S { fn m(&self) {} }
                  //X
        fn f<T>(t: T) -> T { t }
        fn main() {
            f(S).m();
               //^
        }
    """)

    fun `test iterator for loop resolve`() = checkByCode("""
        #[lang = "core::iter::Iterator"]
        trait Iterator { type Item; fn next(&mut self) -> Option<Self::Item>; }
        #[lang = "core::iter::IntoIterator"]
        trait IntoIterator {
            type Item;
            type IntoIter: Iterator<Item=Self::Item>;
            fn into_iter(self) -> Self::IntoIter;
        }
        impl<I: Iterator> IntoIterator for I {
            type Item = I::Item;
            type IntoIter = I;
            fn into_iter(self) -> I { self }
        }

        struct S;
        impl S { fn foo(&self) {} }
                  //X
        struct I;
        impl Iterator for I {
            type Item = S;
            fn next(&mut self) -> Option<S> { None }
        }

        fn main() {
            for s in I {
                s.foo();
            }    //^
        }
    """)

    fun `test into iterator for loop resolve`() = checkByCode("""
        #[lang = "core::iter::Iterator"]
        trait Iterator { type Item; fn next(&mut self) -> Option<Self::Item>; }
        #[lang = "core::iter::IntoIterator"]
        trait IntoIterator {
            type Item;
            type IntoIter: Iterator<Item=Self::Item>;
            fn into_iter(self) -> Self::IntoIter;
        }

        struct S;
        impl S { fn foo(&self) {} }
                  //X
        struct I;
        impl Iterator for I {
            type Item = S;
            fn next(&mut self) -> Option<S> { None }
        }

        struct II;
        impl IntoIterator for II {
            type Item = S;
            type IntoIter = I;
            fn into_iter(self) -> Self::IntoIter { I }
        }

        fn main() {
            for s in II {
                s.foo()
            }   //^
        }
    """)

    // really unresolved in rustc, but IDE will resolve it anyway
    fun `test no stack overflow if struct name is the same as generic type`() = checkByCode("""
        struct S;
        trait Tr1 {}
        trait Tr2 { fn some_fn(&self) {} }
                     //X
        impl<S: Tr1> Tr2 for S {}
        fn main(v: S) {
            v.some_fn();
            //^
        }
    """)

    fun `test single auto deref`() = checkByCode("""
        struct A;
        struct B;
        impl B { fn some_fn(&self) { } }
                    //X
        #[lang = "deref"]
        trait Deref { type Target; }
        impl Deref for A { type Target = B; }

        fn foo(a: A) {
            a.some_fn()
            //^
        }
    """)

    fun `test multiple auto deref`() = checkByCode("""
        struct A;
        struct B;
        struct C;
        impl C { fn some_fn(&self) { } }
                    //X
        #[lang = "deref"]
        trait Deref { type Target; }
        impl Deref for A { type Target = B; }
        impl Deref for B { type Target = C; }

        fn foo(a: A) {
            a.some_fn()
            //^
        }
    """)

    fun `test recursive auto deref`() = checkByCode("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct A;
        struct B;
        struct C;

        impl C { fn some_fn(&self) { } }
                    //X

        impl Deref for A { type Target = B; }
        impl Deref for B { type Target = C; }
        impl Deref for C { type Target = A; }

        fn foo(a: A) {
            // compiler actually bails with `reached the recursion limit while auto-dereferencing B`
            a.some_fn()
            //^
        }
    """)

    fun `test method with same name on different deref levels`() = checkByCode("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct A;
        struct B;

        impl Deref for A { type Target = B; }

        impl B { fn foo(&self) {} }
        impl A { fn foo(&self) {} }
                  //X
        fn main() {
            A.foo();
        }   //^
    """, TypeInferenceMarks.methodPickDerefOrder)

    fun `test non inherent impl 2`() = checkByCode("""
        trait T { fn foo(&self); }
        struct S;

        impl T for S { fn foo(&self) { println!("non ref"); } }
                         //X
        impl<'a> T for &'a S { fn foo(&self) { println!("ref"); } }

        fn main() {
            (&S).foo()
               //^
        }
    """, TypeInferenceMarks.methodPickDerefOrder)

    fun `test non inherent impl 3`() = checkByCode("""
        trait T { fn foo(&self); }
        struct S;

        impl T for S { fn foo(&self) { println!("non ref"); } }
        impl<'a> T for &'a S { fn foo(&self) { println!("ref"); } }
                                 //X

        fn main() {
            (&&S).foo()
                //^
        }
    """, TypeInferenceMarks.methodPickDerefOrder)

    fun `test non inherent impl 4`() = checkByCode("""
        trait T1 { fn foo(&mut self); }
        trait T2 { fn foo(&self); }
        struct S;

        impl T1 for S { fn foo(&mut self) { println!("non ref"); } }
        impl<'a> T2 for &'a S { fn foo(&self) { println!("ref"); } }
                                 //X
        fn main() {
            (&S).foo()
               //^
        }
    """, TypeInferenceMarks.methodPickDerefOrder)

    fun `test non inherent impl 5`() = checkByCode("""
        trait T1 { fn foo(&self); }
        trait T2 { fn foo(self); }
        struct S;

        impl T1 for S { fn foo(&self) { println!("non ref"); } }
        impl<'a> T2 for &'a S { fn foo(self) { println!("ref"); } }

        fn main() {
            (&S).foo();
        }      //^ unresolved
    """, TypeInferenceMarks.methodPickDerefOrder)

    fun `test indexing`() = checkByCode("""
        #[lang = "index"]
        trait Index<Idx: ?Sized> {
            type Output: ?Sized;
            fn index(&self, index: Idx) -> &Self::Output;
        }

        struct Container;
        struct Elem;
        impl Elem { fn foo(&self) {} }
                      //X

        impl Index<usize> for Container {
            type Output = Elem;
            fn index(&self, index: usize) -> &Elem { unimplemented!() }
        }

        fn bar(c: Container) {
            c[0].foo()
                //^
        }
    """)

    fun `test indexing with multiple impls`() = checkByCode("""
        #[lang = "index"]
        trait Index<Idx: ?Sized> {
            type Output: ?Sized;
            fn index(&self, index: Idx) -> &Self::Output;
        }

        struct Container;
        struct Elem1;
        impl Elem1 { fn foo(&self) {} }
        struct Elem2;
        impl Elem2 { fn foo(&self) {} }
                       //X

        impl Index<usize> for Container {
            type Output = Elem1;
            fn index(&self, index: usize) -> &Elem1 { unimplemented!() }
        }

        impl Index<f64> for Container {
            type Output = Elem2;
            fn index(&self, index: f64) -> &Elem2 { unimplemented!() }
        }

        fn bar(c: Container) {
            c[0.0].foo()
                  //^
        }
    """)

    fun `test simple generic function argument`() = checkByCode("""
        struct Foo<F>(F);
        struct Bar;
        impl Bar {
            fn bar(&self) { unimplemented!() }
              //X
        }
        fn foo<T>(xs: Foo<T>) -> T { unimplemented!() }
        fn main() {
            let x = foo(Foo(Bar()));
            x.bar();
             //^
        }
    """)

    fun `test complex generic function argument`() = checkByCode("""
        struct Foo<T1, T2>(T1, T2);
        enum Bar<T3> { V(T3) }
        struct FooBar<T4, T5>(T4, T5);
        struct S;

        impl S {
            fn bar(&self) { unimplemented!() }
              //X
        }

        fn foo<F1, F2, F3>(x: FooBar<Foo<F1, F2>, Bar<F3>>) -> Foo<F2, F3> { unimplemented!() }
        fn main() {
            let x = foo(FooBar(Foo(123, "foo"), Bar::V(S())));
            x.1.bar();
              //^
        }
    """)

    fun `test generic method argument`() = checkByCode("""
        struct Foo<F>(F);
        enum Bar<B> { V(B) }
        struct FooBar<E1, E2>(E1, E2);
        struct S;

        impl<T1> Foo<T1> {
            fn foo<T2>(&self, bar: Bar<T2>) -> FooBar<T1, T2> { unimplemented!() }
        }

        impl S {
            fn bar(&self) { unimplemented!() }
              //X
        }

        fn main() {
            let x = Foo(123).foo(Bar::V(S()));
            x.1.bar();
              //^
        }
    """)

    fun `test arithmetic operations`() {
        for ((traitName, itemName, fnName, sign) in ArithmeticOp.values()) {
            checkByCode("""
                #[lang = "$itemName"]
                pub trait $traitName<RHS=Self> {
                    type Output;
                    fn $fnName(self, rhs: RHS) -> Self::Output;
                }

                struct Foo;
                struct Bar;

                impl Bar {
                    fn bar(&self) { unimplemented!() }
                      //X
                }

                impl $traitName<i32> for Foo {
                    type Output = Bar;
                    fn $fnName(self, rhs: i32) -> Bar { unimplemented!() }
                }

                fn foo(lhs: Foo, rhs: i32) {
                    let x = lhs $sign rhs;
                    x.bar()
                     //^
                }
            """)
        }
    }

    fun `test arithmetic operations with multiple impls`() {
        for ((traitName, itemName, fnName, sign) in ArithmeticOp.values()) {
            checkByCode("""
                #[lang = "$itemName"]
                pub trait $traitName<RHS=Self> {
                    type Output;
                    fn $fnName(self, rhs: RHS) -> Self::Output;
                }

                struct Foo;
                struct Bar;
                struct FooBar;

                impl Bar {
                    fn foo(&self) { unimplemented!() }
                }

                impl FooBar {
                    fn foo(&self) { unimplemented!() }
                      //X
                }

                impl $traitName<f64> for Foo {
                    type Output = Bar;
                    fn $fnName(self, rhs: f64) -> Bar { unimplemented!() }
                }

                impl $traitName<i32> for Foo {
                    type Output = FooBar;
                    fn $fnName(self, rhs: i32) -> FooBar { unimplemented!() }
                }

                fn foo(lhs: Foo, rhs: i32) {
                    let x = lhs $sign rhs;
                    x.foo()
                     //^
                }
            """)
        }
    }

    fun `test generic method with type parameters`() = checkByCode("""
        struct S;
        impl S {
            fn make_t<T>(&self) -> T { unimplemented!() }
        }

        struct X;
        impl X { fn foo(&self) {} }
                   //X
        fn main() {
            let t = S.make_t::<X>();
            t.foo();
        }    //^
    """)

    fun `test trait with bounds on itself`() = checkByCode("""
        trait Foo<T: Foo<T>> {
            fn foo(&self) { }
        }     //X

        impl Foo<()> for () { }

        fn bar<T: Foo<T>>(t: T) {
            t.foo()
        }    //^

        fn main() { bar(()) }
    """)

    fun `test bound associated type`() = checkByCode("""
        trait Tr { type Item; }
                      //X
        struct S<A>(A);
        impl<B: Tr> S<B> { fn foo(self) -> B::Item { unimplemented!() } }
                                            //^
    """)

    fun `test bound associated type in explicit UFCS form`() = checkByCode("""
        trait Tr { type Item; }
                      //X
        struct S<A>(A);
        impl<B: Tr> S<B> { fn foo(self) -> <B as Tr>::Item { unimplemented!() } }
                                                    //^
    """)

    fun `test bound inherited associated type`() = checkByCode("""
        trait Tr1 { type Item; }
                       //X
        trait Tr2: Tr1 {}
        struct S<A>(A);
        impl<B: Tr2> S<B> { fn foo(self) -> B::Item { unimplemented!() } }
                                             //^
    """)

    fun `test no stack overflow on self unification with Eq bound`() = checkByCode("""
        pub trait PartialEq<Rhs: ?Sized> {}
        pub trait Eq: PartialEq<Self> {}
        struct S<A>(A);

        impl<T: Eq> S<T> {
            fn foo(self) {
                self.bar()
            }      //^ unresolved
        }
    """)

    fun `test no stack overflow (issue 1523)`() = checkByCode("""
        struct S1;
        struct S2<A>(A);

        trait Tr { type Item: Sized; }
        impl Tr for S1 { type Item = S2<S1>; }
        impl<B: Tr> S2<B> { fn foo(&self) {} }
                             //X
        fn main() {
            S2(S1).foo()
        }         //^
    """)

    fun `test no stack overflow (issue 1578)`() = checkByCode("""
        pub trait Rem<RHS=Self> {
            type Output = Self;
        }

        struct S<A>(A);
        impl<B: Rem> S<B> { fn foo(&self) {} }
                             //X
        fn foo<C: Rem>(t: C) {
            S(t).foo()
        }      //^
    """)

    fun `test resolve assoc constant`() = checkByCode("""
        trait T {
            const C: i32;
        }       //X

        fn foo<X: T>() {
            X::C
        }    //^
    """)

    fun `test assoc type in fn parameter`() = checkByCode("""
        pub trait Iter {
            type Item;
                //X

            fn scan<St, B, F>(self, initial_state: St, f: F) -> Scan<Self, St, F>
                where Self: Sized, F: FnMut(&mut St, Self::Item) -> Option<B> { unimplemented!() }
        }                                                 //^
    """)

    fun `test direct trait methods wins over inherent via deref`() = checkByCode("""
        struct Foo;
        impl Foo {
            fn foo(&self) { println!("Inherent"); }
        }

        struct Bar(Foo);
        impl ::std::ops::Deref for Bar {
            type Target = Foo;
            fn deref(&self) -> &Foo {
                &self.0
            }
        }

        trait T {
            fn foo(&self) { println!("From a trait"); }
        }     //X
        impl T for Bar {}

        fn main() {
            let bar = Bar(Foo);
            bar.foo();
        }      //^
    """)

    fun `test impl for type parameter`() = checkByCode("""
        trait Foo {
            fn foo(&self) {}
              //X
        }
        impl<T> Foo for T {}
        struct Bar;
        fn main() {
            Bar.foo();
               //^
        }
    """)

    fun `test impl for type parameter with bound`() = checkByCode("""
        trait Bar {}
        trait Foo {
            fn foo(&self) {}
        }    //X

        impl<T: Bar> Foo for T {}

        struct S;
        impl Bar for S {}

        fn main() {
            S.foo();
             //^
        }
    """)

    // really unresolved in rustc, but IDE will resolve it anyway
    fun `test impl for type parameter with recursive bounds`() = checkByCode("""
        trait Foo { fn foo(&self) {} }
                     //X
        trait Bar { fn bar(&self) {} }

        impl<T: Bar> Foo for T {}
        impl<T: Foo> Bar for T {}

        struct S;

        fn main() {
            S.foo();
             //^
        }
    """)

    fun `test impl for reference of type parameter`() = checkByCode("""
        trait Foo {
            fn foo(&self) {}
              //X
        }
        impl<T> Foo for &T {}
        struct Bar;
        fn main() {
            (&Bar).foo();
                 //^
        }
    """)

    fun `test resolve method call with multiple impls of the same trait`() = checkByCode("""
        struct S; struct S1; struct S2;
        trait T<A> { fn foo(&self, _: A); }
        impl T<S1> for S { fn foo(&self, _: S1) {} }
        impl T<S2> for S { fn foo(&self, _: S2) {} }
                            //X
        fn main() {
            S.foo(S2)
        }    //^
    """, TypeInferenceMarks.methodPickCollapseTraits)

    fun `test resolve UFCS method call with multiple impls of the same trait`() = checkByCode("""
        struct S; struct S1; struct S2;
        trait T<A> { fn foo(&self, _: A); }
        impl T<S1> for S { fn foo(&self, _: S1) {} }
        impl T<S2> for S { fn foo(&self, _: S2) {} }
                            //X
        fn main() {
            T::foo(&S, S2);
        }    //^
    """)

    fun `test resolve trait associated function with multiple impls of the same trait`() = checkByCode("""
        struct S; struct S1; struct S2;
        trait T<A> { fn foo(_: A) -> Self; }
        impl T<S1> for S { fn foo(_: S1) -> Self { unimplemented!() } }
        impl T<S2> for S { fn foo(_: S2) -> Self { unimplemented!() } }
                            //X
        fn main() {
            let a: S = T::foo(S2);
        }               //^
    """)

    fun `test resolve trait associated function with multiple impls of the same trait 2`() = checkByCode("""
        struct S; struct S1; struct S2;
        trait T<A> { fn foo(_: A) -> Self; }
        impl T<S1> for S { fn foo(_: S1) -> Self { unimplemented!() } }
        impl T<S2> for S { fn foo(_: S2) -> Self { unimplemented!() } }
                            //X
        fn main() {
            S::foo(S2);
        }    //^
    """)

    fun `test method with multiple impls of the same trait on multiple deref levels`() = checkByCode("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct A;
        struct B;
        impl Deref for A { type Target = B; }
        trait Tr<T1, T2> { fn foo(&self, t: T1) -> T2 { unimplemented!() } }
        impl Tr<u8, i8> for A { fn foo(&self, t: u8) -> i8 { unimplemented!() } }
        impl Tr<u16, i16> for A { fn foo(&self, t: u16) -> i16 { unimplemented!() } }
                                   //X
        impl Tr<u32, i32> for B { fn foo(&self, t: u32) -> i32 { unimplemented!() } }
        fn main() {
            A.foo(0u16);
        }    //^
    """, TypeInferenceMarks.methodPickCollapseTraits)

    fun `test method with multiple impls of the same trait on 2nd deref level`() = checkByCode("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct A;
        struct B;
        impl Deref for A { type Target = B; }
        trait Tr<T1, T2> { fn foo(&self, t: T1) -> T2 { unimplemented!() } }
        impl Tr<u8, i8> for B { fn foo(&self, t: u8) -> i8 { unimplemented!() } }
        impl Tr<u16, i16> for B { fn foo(&self, t: u16) -> i16 { unimplemented!() } }
                                   //X
        fn main() {
            A.foo(0u16);
        }    //^
    """, TypeInferenceMarks.methodPickCollapseTraits)

    // https://github.com/intellij-rust/intellij-rust/issues/1649
    fun `test issue 1649`() = checkByCode("""
        trait Foo {}
        struct S<A: Foo> { a: A }
        struct C;
        impl Foo for C {}
        type S1<B> = S<B>;
        impl S<C> {
          fn bar() -> Self { unimplemented!() }
        }    //X
        fn main() {
          S1::bar();
        }   //^
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/1927
    fun `test no stack overflow with cyclic type of infinite size`() = checkByCode("""
        struct S<T>(T);
        fn foo<T>() -> T { unimplemented!() }
        fn unify<T>(_: T, _: T) { unimplemented!() }
        fn main() {
            let a = foo();
            let b = S(a);
            unify(a, b);
            b.bar();
            //^ unresolved
        }
    """, TypeInferenceMarks.cyclicType)

    fun `test resolve generic impl from impl trait`() = checkByCode("""
        trait Foo {}
        trait Bar { fn bar(&self) {} }
                     //X
        impl<T: Foo> Bar for T {}
        fn foo() -> impl Foo { unimplemented!() }
        fn main() {
            foo().bar();
        }       //^
    """)

    fun `test method refinement after unconstrained integer fallback to i32`() = checkByCode("""
        pub trait MyAdd<RHS=Self> {
            type Output;
            fn my_add(self, rhs: RHS) -> Self::Output;
        }
        impl MyAdd for i32 {
            type Output = i32;
            fn my_add(self, other: i32) -> i32 { self + other }
        }    //X
        impl MyAdd for u8 {
            type Output = u8;
            fn my_add(self, other: u8) -> u8 { self + other }
        }
        fn main() {
            0.my_add(0);
        }   //^
    """)

    fun `test specific method is not known during type inference`() = checkByCode("""
        pub trait MyAdd<RHS=Self> {
            type Output;
            fn my_add(self, rhs: RHS) -> Self::Output;
        }
        impl MyAdd for i32 {
            type Output = i32;
            fn my_add(self, other: i32) -> i32 { self + other }
        }
        impl MyAdd for u8 {
            type Output = u8;
            fn my_add(self, other: u8) -> u8 { self + other }
        }
        fn main() {
            // The first call will be resolved to impl only after typecheck of the
            // full function body, so second call can't be resolved
            0.my_add(0).my_add(0);
        }             //^ unresolved
    """)

    fun `test method in "impl for generic type" at type parameter with bound`() = checkByCode("""
        trait Bound {}
        trait Tr {
            fn foo(&self) {}
        }    //X
        impl<A: Bound> Tr for A {}
        fn foo<B: Bound>(b: B) {
            b.foo();
        }   //^
    """)

    fun `test "impl for generic type" is NOT used for associated type resolve`() = checkByCode("""
        trait Bound {}
        trait Tr { type Item; }
        impl<A: Bound> Tr for A { type Item = (); }
        fn foo<B: Bound>(b: B) {
            let a: B::Item;
        }           //^ unresolved
    """, NameResolutionTestmarks.skipAssocTypeFromImpl)

    fun `test "impl for generic type" is USED for associated type resolve UFCS 1`() = checkByCode("""
        trait Bound {}
        trait Tr { type Item; }
        impl<A: Bound> Tr for A { type Item = (); }
        fn foo<B: Bound>(b: B) {     //X
            let a: <B as Tr>::Item;
        }                   //^
    """)

    fun `test "impl for generic type" is USED for associated type resolve UFCS 2`() = checkByCode("""
        trait Bound { type Item; }
        impl<A: Bound> Bound for &A { type Item = (); }
                                          //X
        fn foo<B: Bound>(b: B) {
            let a: <&B as Bound>::Item;
        }                       //^
    """)

    fun `test non-inherent impl with unresolved trait does not affect name resolution`() = checkByCode("""
        struct S;
        struct X; struct Y; struct Z;

        trait Foo<T> { fn foo(_: T); }

        impl Foo<X> for S { fn foo(_: X) {} }
                             //X
        impl Foo<Y> for S { fn foo(_: Y) {} }
        impl Unknown<Z> for S { fn foo(_: Z) {} }

        fn main() {
            S::foo(X);
        }    //^
    """)

    fun `test generic trait object inherent impl`() = checkByCode("""
        trait Foo<T>{}
        impl<T> dyn Foo<T>{
            fn foo(&self){}
        }    //X
        fn foo(a: &dyn Foo<i32>){
            a.foo()
        }   //^
    """)

    fun `test impl for type parameter resolved for trait object`() = checkByCode("""
        trait Foo {}
        trait Bar { fn bar(&self); }
        impl<T: ?Sized> Bar for T {
            fn bar(&self) {}
        }    //X
        fn foo(a: &dyn Foo) {
            (*a).bar()
        }      //^
    """)

    fun `test trait bounds normalization`() = checkByCode("""
        trait Foo { fn foo(&self) {} }
                     //X
        trait Bar { type Item; }
        fn foo<A: Bar<Item=B>, B>(b: B) where A::Item: Foo {
            b.foo()
        }   //^
    """)

    fun `test assoc type bound method`() = checkByCode("""
        trait Foo { fn foo(&self) {} }
                     //X
        trait Bar where Self::Item: Foo { type Item; }
        fn bar<A: Bar>(_: A, b: A::Item) {
            b.foo();
        }   //^
    """)

    fun `test assoc type bound method 2`() = checkByCode("""
        trait Foo { type Item: Bar; }
        trait Bar: Baz {}
        trait Baz {
            fn baz(&self) {}
        }    //X

        fn foobar<T: Foo>(a: T::Item) {
            a.baz();
        }   //^
    """)

    fun `test assoc type bound method 3`() = checkByCode("""
        trait Foo { type Item: Bar1 + Bar2; }
        trait Bar1: Baz {}
        trait Bar2: Baz {}
        trait Baz {
            fn baz(&self) {}
        }    //X

        fn foobar<T: Foo>(a: T::Item) {
            a.baz();
        }   //^
    """)

    fun `test Self-qualified path in trait impl is resolved to assoc type of super trait (generic trait 1)`() = checkByCode("""
        struct S;
        trait Trait1<T> { type Item; }
        trait Trait2<T>: Trait1<T> { fn foo() -> i32; }

        impl Trait1<i32> for S {
            type Item = i32;
        }       //X
        impl Trait1<u8> for S {
            type Item = u8;
        }
        impl Trait2<i32> for S {
            fn foo() -> Self::Item { unreachable!() }
        }                   //^
    """, NameResolutionTestmarks.selfRelatedTypeSpecialCase)

    fun `test Self-qualified path in trait impl is resolved to assoc type of super trait (generic trait 2)`() = checkByCode("""
        struct S;
        trait Trait1<T=u8> { type Item; }
        trait Trait2<T>: Trait1<T> { fn foo() -> i32; }

        impl Trait1<i32> for S {
            type Item = i32;
        }       //X
        impl Trait1 for S {
            type Item = u8;
        }
        impl Trait2<i32> for S {
            fn foo() -> Self::Item { unreachable!() }
        }                   //^
    """, NameResolutionTestmarks.selfRelatedTypeSpecialCase)

    fun `test non-UFCS associated type in type alias with bound`() = checkByCode("""
        trait Trait {
            type Item;
        }      //X
        type Alias<T: Trait> = T::Item;
                                //^
    """)

    fun `test associated type in type alias with bound`() = checkByCode("""
        trait Trait {
            type Item;
        }      //X
        type Alias<T: Trait> = <T as Trait>::Item;
                                           //^
    """)

    fun `test associated type in type alias without bound`() = checkByCode("""
        trait Trait {
            type Item;
        }      //X
        type Alias<T> = <T as Trait>::Item;
                                    //^
    """)

    fun `test nested associated type in type alias without bound`() = checkByCode("""
        trait Trait {
            type Item;
        }      //X
        type Alias1<Q> = <<Q as Trait>::Item as Trait>::Item;
                                                      //^
    """)

    fun `test nested associated type in type alias without bound 2`() = checkByCode("""
        trait Trait {
            type Item: Trait2;
        }
        trait Trait2 {
            type Item;
        }      //X
        type Alias1<Q> = <<Q as Trait>::Item as Trait2>::Item;
                                                       //^
    """)

    fun `test associated type in type alias is resolved to trait when no applicable impl exists`() = checkByCode("""
        pub trait Trait<T> { type Item; }
        struct S;               //X
        impl Trait<i32> for S { type Item = (); }
        impl Trait<u8> for S { type Item = (); }

        pub type Alias<T> = <S as Trait<T>>::Item;
                                           //^
    """)

    fun `test method to impl with associated type projection through type alias`() = checkByCode("""
        struct A;
        pub trait Trait<T> { type Item; }
        impl Trait<i32> for A { type Item = u32; }
        impl Trait<i8> for A { type Item = u8; }

        pub type Unsigned<T> = <A as Trait<T>>::Item;

        struct B<T>(T);
        impl B<Unsigned<i32>> {
            fn foo(&self) {}
        }
        impl B<Unsigned<i8>> {
            fn foo(&self) {}
        }    //X
        fn foo(a: B<Unsigned<i8>>) {
            a.foo()
        }   //^
    """)

    fun `test type-qualified path to impl with associated type projection through type alias UFCS`() = checkByCode("""
        struct A;
        pub trait Trait<T> { type Item; }
        impl Trait<i32> for A { type Item = u32; }
        impl Trait<i8> for A { type Item = u8; }

        pub type Unsigned<T> = <A as Trait<T>>::Item;

        struct B<T>(T);
        impl B<Unsigned<i32>> {
            fn foo(&self) {}
        }
        impl B<Unsigned<i8>> {
            fn foo(&self) {}
        }    //X
        fn foo(a: B<Unsigned<i8>>) {
            <B<Unsigned<i8>>>::foo(a)
        }                    //^
    """)

    fun `test Self-qualified path to impl with associated type projection through type alias`() = checkByCode("""
        struct A;
        pub trait Trait<T> { type Item; }
        impl Trait<i32> for A { type Item = u32; }
        impl Trait<i8> for A { type Item = u8; }

        pub type Unsigned<T> = <A as Trait<T>>::Item;

        struct B<T>(T);
        impl B<Unsigned<i32>> {
            fn foo(&self) {}
        }
        impl B<Unsigned<i8>> {
            fn foo(&self) {}
             //X
            fn bar(a: &Self) {
                Self::foo(a)
            }       //^
        }
    """)

    fun `test explicit UFCS-like type-qualified path is resolved to correct impl when inapplicable blanket impl exists`() = checkByCode("""
        trait Trait { type Item; }
        trait Bound {}
        impl<I: Bound> Trait for I {
            type Item = I;
        }
        struct S;
        impl Trait for S {
            type Item = ();
        }      //X
        fn main() {
            let a: <S as Trait>::Item;
        }                      //^
    """)

    fun `test explicit UFCS-like generic type-qualified path to associated function`() = checkByCode("""
        trait Foo { fn foo(&self); }
                     //X
        impl<T> Foo for T { fn foo(&self) {} }
        trait Bar { fn foo(&self); }
        impl<T> Bar for T { fn foo(&self) {} }
        fn baz<T: Foo+Bar>(t: T) {
            <T as Foo>::foo(&t);
        }             //^
    """)

    fun `test assoc function related to type-parameter-qualified assoc type with trait bound`() = checkByCode("""
        trait Trait {
            type Item: AssocTypeBound;
        }
        trait AssocTypeBound {
            fn bar() {}
        }    //X
        fn foo<T: Trait>() {
            T::Item::bar();
        }          //^
    """)

    fun `test a enum variant wins an associated type in a struct literal context`() = checkByCode("""
        trait Trait { type Foo; }
        impl<T> Trait for T { type Foo = (); }
        enum E {
            Foo {},
            //X
            Bar {}
        }
        fn main() {
            let _ = E::Foo {};
        }            //^
    """)

    fun `test a enum variant wins an associated type in a pattern context`() = checkByCode("""
        trait Trait { type Foo; }
        impl<T> Trait for T { type Foo = (); }
        enum E {
            Foo {},
            //X
            Bar {}
        }
        fn main() {
            let a = E::Foo {};
            if let E::Foo {} = a {}
        }           //^
    """)

    fun `test a enum variant wins an associated function in a function call context`() = checkByCode("""
        trait Trait { fn Foo(a: i32); }
        impl<T> Trait for T { fn Foo(a: i32) {} }
        enum E {
            Foo(i32),
            //X
            Bar()
        }
        fn main123() {
            let a = E::Foo(1);
        }            //^
    """)

    fun `test a enum variant wins an associated function in a pattern context`() = checkByCode("""
        trait Trait { fn Foo(a: i32); }
        impl<T> Trait for T { fn Foo(a: i32) {} }
        enum E {
            Foo(i32),
            //X
            Bar()
        }
        fn main123() {
            let a = E::Foo(1);
            if let E::Foo(_) = a {}
        }           //^
    """)
}
