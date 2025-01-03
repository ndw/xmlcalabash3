package com.xmlcalabash.config

import com.xmlcalabash.api.XProcStep
import com.xmlcalabash.datamodel.DeclareStepInstruction
import com.xmlcalabash.datamodel.PipelineBuilder
import com.xmlcalabash.datamodel.StandardLibrary
import com.xmlcalabash.datamodel.Visibility
import com.xmlcalabash.documents.XProcDocument
import com.xmlcalabash.exceptions.DefaultErrorExplanation
import com.xmlcalabash.exceptions.ErrorExplanation
import com.xmlcalabash.exceptions.XProcError
import com.xmlcalabash.io.DocumentManager
import com.xmlcalabash.runtime.XProcExecutionContext
import com.xmlcalabash.runtime.XProcStepConfiguration
import com.xmlcalabash.runtime.parameters.StepParameters
import com.xmlcalabash.spi.AtomicStepManager
import com.xmlcalabash.spi.AtomicStepServiceProvider
import com.xmlcalabash.spi.DocumentResolverServiceProvider
import com.xmlcalabash.util.DefaultMessageReporter
import com.xmlcalabash.api.MessageReporter
import com.xmlcalabash.util.Verbosity
import net.sf.saxon.s9api.QName
import org.apache.logging.log4j.kotlin.logger
import java.net.URI
import javax.activation.MimetypesFileTypeMap

class CommonEnvironment(private val xmlCalabash: XmlCalabash) {
    companion object {
        // N.B. This is used in reverse in MediaType.extension()
        val defaultContentTypes = mapOf(
            "7z" to "application/x-7z-compressed",
            "a" to "application/x-archive",
            "arj" to "application/x-arj",
            "bmp" to "image/bmp",
            "bz2" to "application/bzip2",
            "cpio" to "application/x-cpio",
            "css" to "text/plain",
            "csv" to "text/csv",
            "epub" to "application/epub+zip",
            "eps" to "image/eps",
            "fo" to "application/xml",
            "gif" to "image/gif",
            "gz" to "application/gzip",
            "gzip" to "application/gzip",
            "jar" to "application/java-archive",
            "jpeg" to "image/jpeg",
            "jpg" to "image/jpeg",
            "json" to "application/json",
            "lzma" to "application/lzma",
            "nvdl" to "application/nvdl+xml",
            "pdf" to "application/pdf",
            "rnc" to "application/relax-ng-compact-syntax",
            "rng" to "application/relax-ng+xml",
            "sch" to "application/schematron+xml",
            "svg" to "image/svg+xml",
            "tar" to "application/x-tar",
            "text" to "text/plain",
            "toml" to "application/toml",
            "txt" to "text/plain",
            "xml" to "application/xml",
            "xpl" to "application/xproc+xml",
            "xq" to "application/xquery",
            "xqy" to "application/xquery",
            "xsd" to "application/xsd+xml",
            "xsl" to "application/xslt+xml",
            "xslt" to "application/xslt+xml",
            "xz" to "application/xz",
            "yml" to "application/x-yaml",
            "yaml" to "application/x-yaml",
            "zip" to "application/zip")
    }

    private var _documentManager: DocumentManager = DocumentManager()
    private var _errorExplanation: ErrorExplanation = DefaultErrorExplanation()
    private var _messageReporter: (() -> MessageReporter)? = null
    private var _mimeTypes = MimetypesFileTypeMap()
    private var _proxies = mutableMapOf<String, String>()
    internal val _standardSteps = mutableMapOf<QName, DeclareStepInstruction>()

    private val uniqueUris = mutableMapOf<String, Int>()
    private val stepManagers = mutableListOf<AtomicStepManager>()
    private val knownAtomicSteps = mutableSetOf<QName>()

    init {
        for ((contentType, extensions) in xmlCalabash.xmlCalabashConfig.mimetypes) {
            _mimeTypes.addMimeTypes("${contentType} ${extensions}")
        }

        for ((ext, contentType) in defaultContentTypes) {
            if (_mimeTypes.getContentType("test.${ext}") == "application/octet-stream") {
                logger.trace { "Assigning default content type to '.${ext}' files: ${contentType}" }
                _mimeTypes.addMimeTypes("${contentType} ${ext}")
            }
        }

        for (provider in AtomicStepServiceProvider.providers()) {
            val manager = provider.create();
            knownAtomicSteps.addAll(manager.stepTypes())
            stepManagers.add(manager)
        }

        for (provider in DocumentResolverServiceProvider.providers()) {
            provider.create().configure(_documentManager)
        }
    }

    var eagerEvaluation = false
    var documentManager: DocumentManager
        get() = _documentManager
        set(value) {
            _documentManager = value
            for (provider in DocumentResolverServiceProvider.providers()) {
                provider.create().configure(_documentManager)
            }
        }
    val mimeTypes: MimetypesFileTypeMap
        get() = _mimeTypes
    var errorExplanation: ErrorExplanation
        get() = _errorExplanation
        set(value) {
            _errorExplanation = value
        }
    var messageReporter: () -> MessageReporter
        get() {
            if (_messageReporter == null) {
                _messageReporter = { DefaultMessageReporter() }
            }
            return _messageReporter!!
        }
        set(value) {
            _messageReporter = value
        }
    val proxies: Map<String, String>
        get() = _proxies
    val standardSteps: Map<QName, DeclareStepInstruction>
        get() = _standardSteps

    fun uniqueUri(base: String): URI {
        if (!xmlCalabash.xmlCalabashConfig.uniqueInlineUris) {
            return URI(base)
        }
        synchronized(uniqueUris) {
            val count = uniqueUris[base] ?: 0
            uniqueUris[base] = count + 1
            if (count == 0 && base != "") {
                return URI(base)
            }
            return URI("${base}?uniqueid=${count}")
        }
    }

    fun stepProvider(params: StepParameters): () -> XProcStep {
        for (manager in stepManagers) {
            if (manager.stepAvailable(params.stepType)) {
                return manager.createStep(params)
            }
        }
        throw XProcError.xsMissingStepDeclaration(params.stepType).exception()
    }

    fun atomicStepAvailable(type: QName): Boolean {
        return knownAtomicSteps.contains(type)
    }

    internal fun newExecutionContext(stepConfig: XProcStepConfiguration): XProcExecutionContext {
        return xmlCalabash.newExecutionContext(stepConfig)
    }

    internal fun getExecutionContext(): XProcExecutionContext {
        return xmlCalabash.getExecutionContext()
    }

    internal fun setExecutionContext(dynamicContext: XProcExecutionContext) {
        return xmlCalabash.setExecutionContext(dynamicContext)
    }

    internal fun releaseExecutionContext() {
        return xmlCalabash.releaseExecutionContext()
    }

    internal fun discardExecutionContext() {
        return xmlCalabash.discardExecutionContext()
    }

    internal fun addProperties(doc: XProcDocument?) {
        return xmlCalabash.addProperties(doc)
    }

    internal fun removeProperties(doc: XProcDocument?) {
        return xmlCalabash.removeProperties(doc)
    }
}