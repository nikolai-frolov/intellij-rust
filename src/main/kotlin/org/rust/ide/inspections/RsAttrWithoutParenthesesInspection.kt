/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.RsAttr
import org.rust.lang.core.psi.ext.name
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsAttrWithoutParenthesesInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitMetaItem(metaItem: RsMetaItem) {
            if (metaItem.parent !is RsAttr) return
            val name = metaItem.name ?: return
            if (name in ATTRIBUTES_WITH_PARENTHESES && metaItem.metaItemArgs == null) {
                RsDiagnostic.NoAttrParentheses(metaItem, name).addToHolder(holder)
            }
        }
    }

    companion object {
        private val ATTRIBUTES_WITH_PARENTHESES = setOf(
            "link",
            "repr",
            "derive",
            "cfg",
            "cfg_attr",
            "allow",
            "warn",
            "forbid",
            "deny",
            "proc_macro_derive"
        )
    }

}
