package com.xmlcalabash.steps.internal

import com.xmlcalabash.namespace.Ns
import com.xmlcalabash.documents.DocumentProperties
import com.xmlcalabash.documents.XProcDocument
import com.xmlcalabash.exceptions.XProcError
import com.xmlcalabash.steps.AbstractAtomicStep
import com.xmlcalabash.runtime.parameters.DocumentStepParameters
import net.sf.saxon.ma.map.MapItem
import net.sf.saxon.s9api.*
import java.net.URISyntaxException

open class DocumentStep(val params: DocumentStepParameters): AbstractAtomicStep() {
    override fun run() {
        super.run()

        val props = DocumentProperties()
        if (options.containsKey(Ns.documentProperties)) {
            val docProps = options[Ns.documentProperties]!!.value
            if (docProps is XdmMap) {
                props.setAll(stepConfig.typeUtils.asMap(stepConfig.typeUtils.forceQNameKeys(docProps.underlyingValue as MapItem)))
            }
        }

        if (params.contentType != null) {
            if (props.has(Ns.contentType)) {
                if (params.contentType.toString() != props[Ns.contentType].toString()) {
                    throw stepConfig.exception(XProcError.xdContentTypesDiffer(params.contentType.toString(), props[Ns.contentType].toString()))
                }
            } else {
                props[Ns.contentType] = XdmAtomicValue(params.contentType.toString())
            }
        }

        val params = mutableMapOf<QName, XdmValue>()
        if (options.containsKey(Ns.parameters)) {
            val docParams = options[Ns.parameters]!!.value
            if (docParams is XdmMap) {
                params.putAll(stepConfig.typeUtils.asMap(stepConfig.typeUtils.forceQNameKeys(docParams.underlyingValue as MapItem)))
            }
        }

        val href = try {
            uriBinding(Ns.href)!!
        } catch (ex: Exception) {
            when (ex) {
                is URISyntaxException, is IllegalArgumentException -> {
                    val badURI = options[Ns.href]!!.value.underlyingValue.stringValue
                    throw stepConfig.exception(XProcError.xdInvalidUri(badURI), ex)
                }
                else -> throw ex
            }
        }

        val manager = stepConfig.environment.documentManager
        val doc = manager.load(href, stepConfig, props, params)
        receiver.output("result", doc);
    }

    override fun reset() {
        super.reset()
    }

    override fun toString(): String = "cx:document"
}