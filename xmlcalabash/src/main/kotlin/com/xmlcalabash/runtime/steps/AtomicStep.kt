package com.xmlcalabash.runtime.steps

import com.xmlcalabash.documents.DocumentProperties
import com.xmlcalabash.documents.XProcDocument
import com.xmlcalabash.exceptions.XProcError
import com.xmlcalabash.namespace.Ns
import com.xmlcalabash.namespace.NsCx
import com.xmlcalabash.namespace.NsP
import com.xmlcalabash.runtime.LazyValue
import com.xmlcalabash.runtime.XProcStepConfiguration
import com.xmlcalabash.runtime.api.Receiver
import com.xmlcalabash.runtime.model.AtomicBuiltinStepModel
import com.xmlcalabash.runtime.parameters.RuntimeStepParameters
import com.xmlcalabash.steps.AbstractAtomicStep
import com.xmlcalabash.util.MediaClassification
import com.xmlcalabash.util.SaxonXsdValidator
import net.sf.saxon.s9api.ValidationMode
import net.sf.saxon.s9api.XdmValue
import org.apache.logging.log4j.kotlin.logger

open class AtomicStep(config: XProcStepConfiguration, atomic: AtomicBuiltinStepModel): AbstractStep(config, atomic), Receiver {
    final override val params = RuntimeStepParameters(atomic.type, atomic.name, atomic.location,
        atomic.inputs, atomic.outputs, atomic.options)
    val implementation = atomic.provider()
    val contextDocuments = mutableSetOf<XProcDocument>()
    val inputPorts = mutableSetOf<String>()
    val initiallyOpenPorts = mutableSetOf<String>()
    private val openPorts = mutableSetOf<String>()
    private var message: XdmValue? = null
    private val inputErrors = mutableListOf<XProcError>()
    override val stepTimeout = atomic.model.step.timeout
    private var validator: SaxonXsdValidator? = null

    private var notReadyReason: String
    init {
        inputPorts.addAll(atomic.inputs.keys)
        for ((name, port) in atomic.inputs) {
            if (!port.weldedShut) {
                initiallyOpenPorts.add(name)
            }
        }

        openPorts.addAll(initiallyOpenPorts)
        val sb = StringBuilder()
        for (portName in openPorts) {
            val port = params.inputs[portName]!!
            sb.append(port.name).append(" ")
        }
        notReadyReason = sb.toString()
        if (notReadyReason.isEmpty()) {
            logger.debug { "WAIT4 ${this}: <nothing>" }
        } else {
            logger.debug { "WAIT4 ${this}: ${notReadyReason}" }
        }

        implementation.setup(stepConfig, this, params)
        implementation.extensionAttributes(atomic.extensionAttributes)
    }

    override val readyToRun: Boolean
        get() {
            var isReady = true
            lateinit var reason: String
            synchronized(openPorts) {
                val sb = StringBuilder()
                for (portName in openPorts) {
                    val port = params.inputs[portName]!!
                    if (!port.unbound) {
                        sb.append(port.name).append(" ")
                        isReady = false
                    }
                }
                reason = sb.toString()
            }

            if (isReady) {
                logger.debug { "READY ${this}" }
                notReadyReason = reason
                return true
            }

            if (notReadyReason != reason) {
                logger.debug { "NORDY ${this}: ${reason}" }
                notReadyReason = reason
            }

            return false
        }

    override fun input(port: String, doc: XProcDocument) {
        synchronized(this) {
            val error = checkInputPort(port, doc, params.inputs[port])

            logger.debug { "RECVD ${this} input on $port ${error ?: ""}: ${inputCount[port]}" }

            if (error == null) {
                if (port.startsWith("Q{")) {
                    contextDocuments.add(doc)
                    val name = stepConfig.typeUtils.parseQName(port)
                    if ((type.namespaceUri == NsP.namespace && name == Ns.message)
                        || (type.namespaceUri != NsP.namespace && name == NsP.message)) {
                        message = doc.value
                    } else {
                        implementation.option(name, LazyValue(doc, stepConfig))
                    }
                } else {
                    if (stepConfig.validationMode != ValidationMode.DEFAULT && params.inputs[port]?.primary == true) {
                        val curVal = doc.properties[NsCx.validationMode]?.underlyingValue?.stringValue
                        val validatable = doc.contentType?.classification() == MediaClassification.XML
                        if (validatable && (curVal == null || (curVal == "lax" && stepConfig.validationMode != ValidationMode.STRICT))) {
                            if (validator == null) {
                                validator = SaxonXsdValidator(stepConfig)
                            }
                            val valid = validator!!.validate(doc)
                            stepConfig.saxonConfig.addProperties(valid)
                            implementation.input(port, valid)
                        } else {
                            // This one's already validated or can't be validated
                            implementation.input(port, doc)
                        }
                    } else {
                        implementation.input(port, doc)
                    }
                }
            } else {
                inputErrors.add(error)
            }
        }
    }

    override fun output(port: String, document: XProcDocument) {
        if (aborted) {
            return
        }

        checkOutputPort(port, document, params.outputs[port])

        val rpair = receiver[port]
        if (rpair == null) {
            logger.debug { "No receiver for ${port} from ${this} (in step)" }
            return
        }

        val targetStep = rpair.first
        val targetPort = rpair.second

        val vmode = try {
            document.properties[NsCx.validationMode] != null
        } catch (ex: Exception) {
            // We may have caused an otherwise lazy expression to be evaluated raising an error
            // Ignore the error. If the expression is actually used, it'll get raised again.
            false
        }

        var outdoc = if (vmode && this.type !in listOf(NsP.identity, NsCx.splitter, NsCx.joiner)) {
            val props = DocumentProperties(document.properties)
            props.remove(NsCx.validationMode)
            document.with(props)
        } else {
            document
        }

        for (monitor in stepConfig.environment.monitors) {
            outdoc = monitor.sendDocument(Pair(this, port), Pair(targetStep, targetPort), outdoc)
        }

        targetStep.input(targetPort, outdoc)
    }

    override fun close(port: String) {
        synchronized(openPorts) {
            logger.debug { "CLOSE ${this} port ${port}: ${inputCount[port]}" }
            openPorts.remove(port)
        }
    }

    override fun instantiate() {
        // nop
    }

    override fun prepare() {
        for ((name, details) in staticOptions) {
            if ((type.namespaceUri == NsP.namespace && name == Ns.message)
                || (type.namespaceUri != NsP.namespace && name == NsP.message)) {
                message = details.staticValue.evaluate(stepConfig)
            } else {
                implementation.option(name, LazyValue(details.stepConfig, details.staticValue, stepConfig))
            }
        }

        if (inputErrors.isNotEmpty()) {
            throw stepConfig.exception(inputErrors.first())
        }

        synchronized(openPorts) {
            for (portName in openPorts) {
                val port = params.inputs[portName]!!
                if (!port.weldedShut && port.defaultBindings.isEmpty()) {
                    throw stepConfig.exception(XProcError.xiImpossible("Unbound input port with no default bindings?"))
                }
                for (binding in port.defaultBindings) {
                    for (document in defaultBindingDocuments(binding)) {
                        input(portName, document)
                    }
                }
            }
        }

        // If no input was sent to a port, it will not have been checked.
        for (portName in inputPorts) {
            if ((inputCount[portName] ?: 0) == 0) {
                val flange = params.inputs[portName]
                if (flange != null && !flange.sequence) {
                    throw stepConfig.exception(XProcError.xdInputSequenceForbidden(portName))
                }
            }
        }
    }

    override fun run() {
        if (message != null) {
            stepConfig.info { "${message}" }
            message = null
        }

        val stepConfig = (implementation as AbstractAtomicStep).stepConfig

        val execContext = stepConfig.saxonConfig.newExecutionContext(stepConfig)
        try {
            for (doc in contextDocuments) {
                execContext.addProperties(doc)
            }

            runImplementation()

            for (doc in contextDocuments) {
                execContext.removeProperties(doc)
            }
        } finally {
            stepConfig.saxonConfig.releaseExecutionContext()
        }

        for ((_, rpair) in receiver) {
            rpair.first.close(rpair.second)
        }
    }

    internal open fun runImplementation() {
        implementation.run()
    }

    override fun abort() {
        super.abort()
        implementation.abort()
    }

    override fun reset() {
        super.reset()
        implementation.reset()
        contextDocuments.clear()
        openPorts.clear()
        openPorts.addAll(initiallyOpenPorts)
        inputErrors.clear()
    }
}