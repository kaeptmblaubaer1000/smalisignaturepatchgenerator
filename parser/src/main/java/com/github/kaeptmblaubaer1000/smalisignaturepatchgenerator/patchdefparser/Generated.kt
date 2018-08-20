// This file is generated by updateParser. DO NOT CHANGE! MAKE CHANGES THERE!
package com.github.kaeptmblaubaer1000.smalisignaturepatchgenerator.patchdefparser

data class NullablePatchDef(
        var humanName: String? = null,
        var modifiedClass: String? = null,
        var modifiedMethod: String? = null
) {
    fun toNonNullable(): PatchDef? {
        return PatchDef(
                humanName = humanName ?: return null,
                modifiedClass = modifiedClass ?: return null,
                modifiedMethod = modifiedMethod ?: return null
        )
    }
}

class PatchDefListenerImpl(val patchDef: NullablePatchDef) : PatchDefBaseListener() {
    override fun exitHumanNameAssignment(ctx: PatchDefParser.HumanNameAssignmentContext) {
        patchDef.humanName = unquoteUnescapeJavaString(ctx.StringLiteral().text)
    }

    override fun exitModifiedClassAssignment(ctx: PatchDefParser.ModifiedClassAssignmentContext) {
        patchDef.modifiedClass = unquoteUnescapeJavaString(ctx.StringLiteral().text)
    }

    override fun exitModifiedMethodAssignment(ctx: PatchDefParser.ModifiedMethodAssignmentContext) {
        patchDef.modifiedMethod = unquoteUnescapeJavaString(ctx.StringLiteral().text)
    }
}
