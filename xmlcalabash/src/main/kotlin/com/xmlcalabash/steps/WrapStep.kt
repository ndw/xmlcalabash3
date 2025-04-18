package com.xmlcalabash.steps

import com.xmlcalabash.documents.XProcDocument
import com.xmlcalabash.exceptions.XProcError
import com.xmlcalabash.datamodel.DocumentContext
import com.xmlcalabash.namespace.Ns
import com.xmlcalabash.namespace.NsCx
import com.xmlcalabash.namespace.NsXml
import com.xmlcalabash.runtime.ProcessMatch
import com.xmlcalabash.runtime.ProcessMatchingNodes
import net.sf.saxon.om.AttributeMap
import net.sf.saxon.s9api.*
import java.net.URI
import java.util.*

class WrapStep(): AbstractAtomicStep(), ProcessMatchingNodes {
    var pattern = ""
    var _matcher: ProcessMatch? = null
    val matcher: ProcessMatch
        get() = _matcher ?: throw RuntimeException("Configuration error...")
    private val inGroup = Stack<Boolean>()
    private var wrapper = NsCx.unusedValue
    private var groupAdjacent: String? = null
    private var groupAdjacentContext: DocumentContext? = null
    lateinit var attributeMap: AttributeMap
    var baseUri: URI? = null

    override fun run() {
        super.run()

        val document = queues["source"]!!.first()
        wrapper = qnameBinding(Ns.wrapper)!!
        pattern = stringBinding(Ns.match)!!
        groupAdjacent = stringBinding(Ns.groupAdjacent)
        if (groupAdjacent != null) {
            groupAdjacentContext = options[Ns.groupAdjacent]!!.context
        }

        val attributeSet = mutableMapOf<QName,String?>()
        val attrMap = qnameMapBinding(Ns.attributes)
        for ((key, value) in attrMap) {
            forbidNamespaceAttribute(key)
            attributeSet[key] = (value as XdmAtomicValue).underlyingValue.stringValue
            if (key == NsXml.base) {
                baseUri = URI(value.underlyingValue.stringValue)
            }
        }
        attributeMap = stepConfig.typeUtils.attributeMap(attributeSet)

        inGroup.push(false)

        _matcher = ProcessMatch(stepConfig, this, valueBinding(Ns.match).context.inscopeNamespaces)
        matcher.process(document.value as XdmNode, pattern)
        receiver.output("result", XProcDocument.ofXml(matcher.result, stepConfig, document.properties))
    }

    override fun reset() {
        super.reset()
    }

    override fun startDocument(node: XdmNode): Boolean {
        matcher.startDocument(node.baseURI)
        matcher.addStartElement(wrapper, attributeMap, baseUri)
        matcher.addSubtree(node)
        matcher.addEndElement()
        matcher.endDocument()
        return false
    }

    override fun endDocument(node: XdmNode) {
        // nop
    }

    override fun startElement(node: XdmNode, attributes: AttributeMap): Boolean {
        if (!inGroup.peek()) {
            matcher.addStartElement(wrapper, attributeMap, baseUri)
        }

        inGroup.pop()
        inGroup.push(groupAdjacent != null && nextMatches(node))

        matcher.addStartElement(node, attributes)

        inGroup.push(false) // endElement will pop this; its value doesn't matter
        return true
    }

    override fun attributes(node: XdmNode,
                            matchingAttributes: AttributeMap,
                            nonMatchingAttributes: AttributeMap): AttributeMap? {
        throw stepConfig.exception(XProcError.xcInvalidSelection(pattern, "attribute").at(node))
    }

    override fun endElement(node: XdmNode) {
        matcher.addEndElement()
        inGroup.pop()
        if (!inGroup.peek()) {
            matcher.addEndElement()
        }
    }

    override fun text(node: XdmNode) {
        process(node)
    }

    override fun comment(node: XdmNode) {
        process(node)
    }

    override fun pi(node: XdmNode) {
        process(node)
    }

    private fun process(node: XdmNode) {
        if (!inGroup.peek()) {
            matcher.addStartElement(wrapper, attributeMap)
        }

        matcher.addSubtree(node)

        inGroup.pop()
        if (groupAdjacent != null && nextMatches(node)) {
            inGroup.push(true)
        } else {
            matcher.addEndElement()
            inGroup.push(false)
        }
    }

    private fun nextMatches(node: XdmNode): Boolean {
        val nodeValue = computeGroup(node) ?: return false

        for (chk in node.axisIterator(Axis.FOLLOWING_SIBLING)) {
            val skipable = when (chk.nodeKind) {
                XdmNodeKind.TEXT -> chk.stringValue.trim().isEmpty()
                XdmNodeKind.COMMENT -> true
                XdmNodeKind.PROCESSING_INSTRUCTION -> true
                else -> false
            }

            if (matcher.matches(chk)) {
                val nextItem = computeGroup(chk)!!
                return stepConfig.typeUtils.xpathDeepEqual(nodeValue, nextItem)
            }

            if (!skipable) {
                return false
            }
        }

        return false
    }

    private fun computeGroup(node: XdmNode): XdmItem? {
        val compiler = stepConfig.newXPathCompiler()
        compiler.baseURI = groupAdjacentContext!!.baseUri
        for ((prefix, uri) in groupAdjacentContext!!.inscopeNamespaces) {
            compiler.declareNamespace(prefix, uri.toString())
        }

        val exec = compiler.compile(groupAdjacent!!)
        val selector = exec.load()
        selector.resourceResolver = stepConfig.environment.documentManager
        selector.contextItem = node

        val values = selector.iterator()
        if (values.hasNext()) {
            return values.next()
        }
        return null
    }

    override fun toString(): String = "p:insert"
}