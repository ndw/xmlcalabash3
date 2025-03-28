package com.xmlcalabash.datamodel

import com.xmlcalabash.config.SaxonConfiguration
import com.xmlcalabash.documents.DocumentProperties
import com.xmlcalabash.documents.XProcDocument
import com.xmlcalabash.exceptions.XProcError
import com.xmlcalabash.exceptions.XProcException
import com.xmlcalabash.namespace.Ns
import com.xmlcalabash.namespace.NsErr
import com.xmlcalabash.namespace.NsP
import com.xmlcalabash.runtime.PipelineContext
import net.sf.saxon.s9api.QName
import net.sf.saxon.s9api.XdmValue
import java.net.URI

class PipelineBuilder private constructor(val pipelineContext: PipelineEnvironment, val saxonConfig: SaxonConfiguration) {
    companion object {
        fun newInstance(config: SaxonConfiguration): PipelineBuilder {
            return newInstance(config, null)
        }

        fun newInstance(config: SaxonConfiguration, version: Double?): PipelineBuilder {
            val environment = PipelineCompilerContext(config.xmlCalabash)
            val builder = PipelineBuilder(environment, config)
            builder.version = version
            builder._stepConfig = InstructionConfigurationImpl.newInstance(builder)
            builder._stepConfig.putNamespace("p", NsP.namespace)
            return builder
        }

        internal fun newInstance(environment: PipelineCompilerContext, config: SaxonConfiguration): PipelineBuilder {
            val builder = PipelineBuilder(environment, config)
            builder.version = null
            builder._stepConfig = InstructionConfigurationImpl.newInstance(builder)
            builder._stepConfig.putNamespace("p", NsP.namespace)
            return builder
        }
    }

    var version: Double? = null

    lateinit private var _stepConfig: InstructionConfiguration
    val stepConfig: InstructionConfiguration
        get() = _stepConfig

    val staticOptionsManager = StaticOptionsManager()

    /*
    private val _options = mutableMapOf<QName, XdmValue>()
    val options: Map<QName, XdmValue>
        get() = _options
     */

    private var started = false

    fun load(uri: URI): XProcDocument {
        // Irrespective of what the filename suggests, if we're trying to load a pipeline
        // force the content type to be application/xml. This is mostly so that .xpl files
        // are XML even if the local system isn't configured that way.
        val properties = DocumentProperties()
        properties.set(Ns.contentType, "application/xml")
        try {
            return stepConfig.environment.documentManager.load(uri, stepConfig, properties)
        } catch (ex: XProcException) {
            if (ex.error.code == NsErr.xd(11)) {
                throw ex.error.with(NsErr.xs(52)).exception()
            }
            throw ex
        }
    }

    fun option(name: QName, value: XProcDocument) {
        option(name, value.value)
    }

    fun option(name: QName, value: XdmValue) {
        if (started) {
            throw stepConfig.exception(XProcError.xiTooLateForStaticOptions(name))
        }

        var curValue : XdmValue? = staticOptionsManager.useWhenOptions[name]
        if (curValue == null) {
            staticOptionsManager.useWhenValue(name, value)
        } else {
            staticOptionsManager.useWhenValue(name, curValue.append(value))
        }
    }

    fun newDeclareStep(): DeclareStepInstruction {
        for ((optname, value) in staticOptionsManager.useWhenOptions) {
            staticOptionsManager.compileTimeValue(optname, XProcExpression.constant(stepConfig, value))
        }

        for ((optname, value) in staticOptionsManager.useWhenOptions) {
            stepConfig.addStaticBinding(optname, value)
        }

        val decl = DeclareStepInstruction(null, stepConfig.copy())
        if (version != null) {
            decl.version = version
        }
        decl.builder = this
        return decl
    }

    fun newLibrary(): LibraryInstruction {
        for ((optname, value) in staticOptionsManager.useWhenOptions) {
            staticOptionsManager.compileTimeValue(optname, XProcExpression.constant(stepConfig, value))
        }
        val decl = LibraryInstruction(stepConfig.copy())
        if (version != null) {
            decl.version = version
        }
        decl.builder = this
        return decl
    }
}