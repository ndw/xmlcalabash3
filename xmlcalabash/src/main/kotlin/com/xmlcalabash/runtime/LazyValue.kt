package com.xmlcalabash.runtime

import com.xmlcalabash.datamodel.XProcExpression
import com.xmlcalabash.documents.DocumentContext
import com.xmlcalabash.documents.XProcDocument
import net.sf.saxon.s9api.XdmValue

/**
 * Holds a value that will be evaluated lazily.
 *
 * This slightly awkward construction is necessary because we don't want to evaluate
 * options that aren't used and we can't construct an XProcConstantExpression at
 * runtime. Perhaps a better solution would be to resolve the conflict between
 * StepConfiguration and RuntimeStepConfiguration, but we'll do this kludge for now.
 */
class LazyValue private constructor(val context: DocumentContext, config: XProcStepConfiguration) {
    private var expression: XProcExpression? = null
    private var constant: XProcDocument? = null
    private val resolvedConfig = config.copy()

    init {
        resolvedConfig.putAllNamespaces(context.inscopeNamespaces)
    }

    constructor(context: DocumentContext, expression: XProcExpression, config: XProcStepConfiguration): this(context, config) {
        this.expression = expression
    }

    constructor(doc: XProcDocument, config: XProcStepConfiguration): this(doc.context, config) {
        this.constant = doc
    }

    val value: XdmValue by lazy {
        if (constant != null) {
            constant!!.value
        } else {
            expression!!.evaluate(resolvedConfig)
        }
    }
}