package com.xmlcalabash.runtime.steps

import com.xmlcalabash.documents.XProcDocument
import com.xmlcalabash.exceptions.XProcError
import com.xmlcalabash.namespace.Ns
import com.xmlcalabash.namespace.NsCx
import com.xmlcalabash.namespace.NsP
import com.xmlcalabash.runtime.XProcStepConfiguration
import com.xmlcalabash.runtime.model.HeadModel
import com.xmlcalabash.runtime.parameters.RuntimeStepParameters
import com.xmlcalabash.util.MediaClassification
import com.xmlcalabash.util.SaxonXsdValidator
import com.xmlcalabash.util.Verbosity
import net.sf.saxon.s9api.*
import org.apache.logging.log4j.kotlin.logger
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class CompoundStepHead(config: XProcStepConfiguration, val parent: CompoundStep, step: HeadModel): AbstractStep(config, step, NsCx.head, "${step.name}/head") {
    override val params = RuntimeStepParameters(NsCx.head, "!head",
        step.location, step.inputs, step.outputs, step.options)
    val defaultInputs = step.defaultInputs
    internal val openPorts = mutableSetOf<String>()
    internal val unboundInputs = mutableSetOf<String>()
    private var message: XdmValue? = null
    internal var showMessage = true
    internal val _cache: ConcurrentMap<String, List<XProcDocument>> = ConcurrentHashMap()
    internal val _options: ConcurrentMap<QName, List<XProcDocument>> = ConcurrentHashMap()
    private val inputErrors = mutableListOf<XProcError>()
    override val stepTimeout: Duration = Duration.ZERO
    private var validator: SaxonXsdValidator? = null

    val cache: Map<String, List<XProcDocument>>
        get() = _cache

    val options: Map<QName, List<XProcDocument>>
        get() = _options

    private var notReadyReason: String
    init {
        val sb = StringBuilder()
        // Inputs = step inputs that aren't passed on to the subpipeline; !source on p:for-each, for example
        for ((name, port) in params.inputs) {
            if (!port.weldedShut) {
                sb.append(port.name).append(" ")
                openPorts.add(name)
            }
        }
        // Outputs = step inputs that are passed on to the subpipeline, caches and current on p:for-each, for example
        for ((name, port) in params.outputs) {
            if (!port.weldedShut) {
                sb.append(port.name).append(" ")
                openPorts.add(name)
            }
        }

        // Also wait for any bindings that are expected
        for ((name, port) in params.options) {
            val eqname = "Q{${name.namespaceUri}}${name.localName}"
            sb.append(eqname).append(" ")
            openPorts.add(eqname)
        }

        notReadyReason = sb.toString()
        if (notReadyReason.isEmpty()) {
            logger.debug { "WAIT4 ${this}: <nothing>" }
        } else {
            logger.debug { "WAIT4 ${this}: ${notReadyReason}" }
        }
    }

    override val readyToRun: Boolean
        get() {
            for ((name, _) in parent.staticOptions) {
                openPorts.remove("Q{${name.namespaceUri}}${name.localName}")
            }

            var isReady = true
            lateinit var reason: String
            synchronized(openPorts) {
                val sb = StringBuilder()
                for (port in openPorts) {
                    if (!unboundInputs.contains(port)) {
                        sb.append(port).append(" ")
                        isReady = false
                    }
                }
                reason = sb.toString()
            }

            if (isReady) {
                logger.debug { "READY ${this}: ${reason}" }
                notReadyReason = reason
                return true
            }

            if (notReadyReason != reason) {
                logger.debug { "NORDY ${this}: ${reason}" }
                notReadyReason = reason
            }
            return false
        }

    internal fun cacheClear() {
        _cache.clear()
        inputCount.clear()
    }

    private fun cacheInput(port: String, documents: List<XProcDocument>) {
        val count = (inputCount[port] ?: 0) + documents.size
        inputCount[port] = count

        val docList = mutableListOf<XProcDocument>()
        docList.addAll(_cache[port] ?: emptyList())
        docList.addAll(documents)

        _cache[port] = docList
    }

    internal fun cacheInputs(inputs: Map<String,List<XProcDocument>>) {
        for ((port, docs) in inputs) {
            cacheInput(port, docs)
        }
    }

    override fun input(port: String, doc: XProcDocument) {
        synchronized(this) {
            logger.debug { "RECVD ${this} input on $port" }

            // N.B. inputs and outputs are swapped in the head
            if (port.startsWith("Q{")) {
                val name = stepConfig.typeUtils.parseQName(port)

                if ((type.namespaceUri == NsP.namespace && name == Ns.message)
                    || (type.namespaceUri != NsP.namespace && name == NsP.message)) {
                    message = doc.value
                } else {
                    val olist = mutableListOf<XProcDocument>()
                    olist.addAll(_options[name] ?: emptyList())
                    olist.add(doc)
                    _options[name] = olist
                }
                return
            }

            val error = if (port in params.inputs) {
                checkInputPort(port, doc, params.inputs[port])
            } else {
                checkInputPort(port, doc, params.outputs[port])
            }

            if (error == null) {
                val list = mutableListOf<XProcDocument>()
                list.addAll(_cache[port] ?: emptyList())
                list.add(doc)
                _cache[port] = list
            } else {
                inputErrors.add(error)
            }
        }
    }

    override fun output(port: String, document: XProcDocument) {
        throw UnsupportedOperationException("Never send an output to a compound head")
    }

    override fun close(port: String) {
        synchronized(openPorts) {
            logger.debug { "CLOSE ${this} port ${port} ${_cache[port]?.size}" }
            openPorts.remove(port)
        }
    }

    override fun instantiate() {
        // nop
    }

    override fun prepare() {
        val ns = if (parent is PipelineStep && parent.stepType != null) {
            parent.stepType!!.namespaceUri
        } else {
            parent.type.namespaceUri
        }
        val matchingName = if (ns == NsP.namespace) {
            Ns.message
        } else {
            NsP.message
        }
        if (matchingName in staticOptions) {
            message = staticOptions[matchingName]!!.staticValue.evaluate(stepConfig)
        }
    }

    override fun run() {
        if (message == null) {
            if (parent.type.namespaceUri == NsP.namespace) {
                message = options[Ns.message]?.first()?.value
            } else {
                message = options[NsP.message]?.first()?.value
            }
        }

        if (showMessage && message != null) {
            stepConfig.info { "${message}" }
            message = null
            showMessage = false
        }

        // Work out what should appear on each input...
        for ((port, output) in params.outputs) {
            if (output.weldedShut) {
                continue
            }

            if (port !in cache) {
                if (port in defaultInputs && port in unboundInputs) {
                    val default = defaultInputs[port]!!
                    for (binding in default.inputs) {
                        for (document in defaultBindingDocuments(binding)) {
                            if (default.select != null) {
                                default.select.contextItem = document
                                val selected = default.select.evaluate(stepConfig)
                                for (item in selected) {
                                    if (item is XdmNode && item.nodeKind == XdmNodeKind.ATTRIBUTE) {
                                        throw stepConfig.exception(XProcError.xdInvalidSelection(item.nodeName))
                                    }
                                    if (item is XdmFunctionItem) {
                                        throw stepConfig.exception(XProcError.xdInvalidFunctionSelection())
                                    }
                                    val itemdoc = XProcDocument.ofValue(
                                        item,
                                        document.context,
                                        document.contentType,
                                        document.properties
                                    )
                                    val error = checkInputPort(port, itemdoc, params.outputs[port])
                                    if (error == null) {
                                        val list = mutableListOf<XProcDocument>()
                                        list.addAll(_cache[port] ?: emptyList())
                                        list.add(itemdoc)
                                        _cache[port] = list
                                    } else {
                                        inputErrors.add(error)
                                    }
                                }
                            } else {
                                val error = checkInputPort(port, document, params.outputs[port])
                                if (error == null) {
                                    val list = mutableListOf<XProcDocument>()
                                    list.addAll(_cache[port] ?: emptyList())
                                    list.add(document)
                                    _cache[port] = list
                                } else {
                                    inputErrors.add(error)
                                }
                            }
                        }
                    }
                }
            }
        }

        for ((port, output) in params.outputs) {
            if (output.weldedShut) {
                continue
            }

            val documents = mutableListOf<XProcDocument>()
            if (cache[port] != null) {
                if (params.outputs[port]?.primary == true) {
                    for (doc in cache[port]!!) {
                        documents.add(validate(doc))
                        //documents.add(doc)
                    }
                } else {
                    documents.addAll(cache[port]!!)
                }
            }

            val rpair = receiver[port]
            if (rpair == null) {
                if (stepConfig.xmlCalabashConfig.verbosity <= Verbosity.DEBUG) {
                    // Ultimately, I don't think these ever matter, but for debugging purposes...
                    if (((type == NsP.choose || type == NsP.`if`) && port == "!context")
                        || ((type == NsP.catch || type == NsP.finally) && port == "error")
                        || ((type == NsP.forEach || type == NsP.viewport) && port == "current")) {
                        // ignore
                    } else {
                        logger.debug { "No receiver for ${port} from ${this} (in head)" }
                    }
                }
            } else {
                val targetStep = rpair.first
                val targetPort = rpair.second

                for (doc in documents) {
                    var outdoc = doc
                    for (monitor in stepConfig.environment.monitors) {
                        outdoc = monitor.sendDocument(Pair(this, port), Pair(targetStep, targetPort), outdoc)
                    }
                    targetStep.input(targetPort, outdoc)
                }
            }

            val error = checkInputPort(port, params.outputs[port]!!)
            if (error != null) {
                inputErrors.add(error)
            }
        }

        if (inputErrors.isNotEmpty()) {
            throw inputErrors.first().exception()
        }

        for ((_, rpair) in receiver) {
            rpair.first.close(rpair.second)
        }

        _cache.clear()
    }

    private fun validate(document: XProcDocument): XProcDocument {
        if (stepConfig.validationMode == ValidationMode.DEFAULT || document.contentType?.classification() != MediaClassification.XML) {
            return document
        }

        val curVal = document.properties[NsCx.validationMode]?.underlyingValue?.stringValue
        if (curVal == "strict" || (curVal == "lax" && stepConfig.validationMode == ValidationMode.LAX)) {
            return document
        }

        if (validator == null) {
            validator = SaxonXsdValidator(stepConfig)
        }

        return validator!!.validate(document)
    }

    override fun reset() {
        super.reset()
        openPorts.clear()
        openPorts.addAll(params.outputs.keys)
        openPorts.addAll(params.inputs.keys)
        _cache.clear()
        inputCount.clear()
        _options.clear()
        showMessage = true
    }

    override fun toString(): String {
        return "(compound step head ${id})"
    }
}
