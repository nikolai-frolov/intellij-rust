/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.render
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.*
import org.rust.lang.core.types.infer.TypeFoldable
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.stdext.dequeOf
import java.util.*

/**
 * Represents both a type, like `i32` or `S<Foo, Bar>`, as well
 * as an unbound constructor `S`.
 *
 * The name `Ty` is short for `Type`, inspired by the Rust
 * compiler.
 */
abstract class Ty(override val flags: TypeFlags = 0) : Kind, TypeFoldable<Ty> {

    override fun foldWith(folder: TypeFolder): Ty = folder.foldTy(this)

    override fun superFoldWith(folder: TypeFolder): Ty = this

    override fun visitWith(visitor: TypeVisitor): Boolean = visitor.visitTy(this)

    override fun superVisitWith(visitor: TypeVisitor): Boolean = false

    open val aliasedBy: BoundElement<RsTypeAlias>? = null

    open fun withAlias(aliasedBy: BoundElement<RsTypeAlias>) = this

    /**
     * Bindings between formal type parameters and actual type arguments.
     */
    open val typeParameterValues: Substitution get() = emptySubstitution

    /**
     * User visible string representation of a type
     */
    final override fun toString(): String = render(useAliasNames = false, skipUnchangedDefaultTypeArguments = false)

    /**
     * Use it instead of [equals] if you want to check that the types are the same from the Rust perspective.
     *
     * ```rust
     * type A = i32;
     * fn foo(a: A, b: i32) {
     *     // Types `A` and `B` are *equivalent*, but not equal
     * }
     * ```
     */
    fun isEquivalentTo(other: Ty?): Boolean = other != null && isEquivalentToInner(other)

    protected open fun isEquivalentToInner(other: Ty): Boolean = equals(other)
}

enum class Mutability {
    MUTABLE,
    IMMUTABLE;

    val isMut: Boolean get() = this == MUTABLE

    companion object {
        fun valueOf(mutable: Boolean): Mutability =
            if (mutable) MUTABLE else IMMUTABLE

        val DEFAULT_MUTABILITY = MUTABLE
    }
}

fun Ty.getTypeParameter(name: String): TyTypeParameter? {
    return typeParameterValues.typeParameterByName(name)
}

val Ty.isSelf: Boolean
    get() = this is TyTypeParameter && this.parameter is TyTypeParameter.Self

fun Ty.walk(): TypeIterator = TypeIterator(this)

/**
 * Iterator that walks `root` and any types reachable from
 * `root`, in depth-first order.
 */
class TypeIterator(root: Ty) : Iterator<Ty> {
    private val stack: Deque<Ty> = dequeOf(root)
    private var lastSubtreeSize: Int = 0

    override fun hasNext(): Boolean = stack.isNotEmpty()

    override fun next(): Ty {
        val ty = stack.pop()
        lastSubtreeSize = stack.size
        pushSubTypes(stack, ty)
        return ty
    }
}

private fun pushSubTypes(stack: Deque<Ty>, parentTy: Ty) {
    // Types on the stack are pushed in reverse order so as to
    // maintain a pre-order traversal. It is like the
    // natural order one would expect — the order of the
    // types as they are written.

    when (parentTy) {
        is TyAdt ->
            parentTy.typeArguments.asReversed().forEach(stack::push)
        is TyAnon, is TyTraitObject, is TyProjection ->
            parentTy.typeParameterValues.types.reversed().forEach(stack::push)
        is TyArray ->
            stack.push(parentTy.base)
        is TyPointer ->
            stack.push(parentTy.referenced)
        is TyReference ->
            stack.push(parentTy.referenced)
        is TySlice ->
            stack.push(parentTy.elementType)
        is TyTuple ->
            parentTy.types.asReversed().forEach(stack::push)
        is TyFunction -> {
            stack.push(parentTy.retType)
            parentTy.paramTypes.asReversed().forEach(stack::push)
        }
    }
}

fun Ty.builtinDeref(explicit: Boolean = true): Pair<Ty, Mutability>? =
    when {
        this is TyReference -> Pair(referenced, mutability)
        this is TyPointer && explicit -> Pair(referenced, mutability)
        else -> null
    }

tailrec fun Ty.stripReferences(): Ty =
    when (this) {
        is TyReference -> referenced.stripReferences()
        else -> this
    }

/**
 * TODO:
 * There are some problems with `Self` inference (e.g. https://github.com/intellij-rust/intellij-rust/issues/2530)
 * so for now just assume `Self` is always copyable
 */
fun Ty.isMovesByDefault(lookup: ImplLookup): Boolean =
    when (this) {
        is TyUnknown, is TyReference, is TyPointer -> false
        is TyTuple -> types.any { it.isMovesByDefault(lookup) }
        is TyArray -> base.isMovesByDefault(lookup)
        is TySlice -> elementType.isMovesByDefault(lookup)
        is TyTypeParameter -> !(parameter == TyTypeParameter.Self || lookup.isCopy(this))
        else -> !lookup.isCopy(this)
    }

val Ty.isBox: Boolean
    get() = this is TyAdt && item == item.knownItems.Box

val Ty.isIntegral: Boolean
    get() = this is TyInteger || this is TyInfer.IntVar

val Ty.isFloat: Boolean
    get() = this is TyFloat || this is TyInfer.FloatVar

val Ty.isScalar: Boolean
    get() = isIntegral ||
        isFloat ||
        this is TyBool ||
        this is TyChar ||
        this is TyUnit ||
        this is TyFunction || // really TyFnDef & TyFnPtr
        this is TyPointer
