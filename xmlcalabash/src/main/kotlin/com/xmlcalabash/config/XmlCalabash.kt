package com.xmlcalabash.config

import com.xmlcalabash.datamodel.DeclareStepInstruction
import com.xmlcalabash.datamodel.PipelineBuilder
import com.xmlcalabash.datamodel.PipelineCompilerContext
import com.xmlcalabash.datamodel.StandardLibrary
import com.xmlcalabash.datamodel.Visibility
import com.xmlcalabash.documents.XProcDocument
import com.xmlcalabash.util.DefaultXmlCalabashConfiguration
import com.xmlcalabash.parsers.xpl.XplParser
import com.xmlcalabash.runtime.XProcExecutionContext
import com.xmlcalabash.runtime.XProcStepConfiguration
import com.xmlcalabash.spi.Configurer
import com.xmlcalabash.spi.ConfigurerServiceProvider
import com.xmlcalabash.util.BufferingMessageReporter
import com.xmlcalabash.util.DefaultMessageReporter
import com.xmlcalabash.util.LoggingMessageReporter
import java.util.*
import kotlin.collections.set

// XmlCalabash and SaxonConfiguration are intertwingled in an unappealing way
class XmlCalabash private constructor(val xmlCalabashConfig: XmlCalabashConfiguration) {
    private lateinit var _saxonConfig: SaxonConfiguration
    private lateinit var _commonEnvironment: CommonEnvironment

    val saxonConfig: SaxonConfiguration
        get() = _saxonConfig

    val commonEnvironment: CommonEnvironment
        get() = _commonEnvironment

    private val _configurers = mutableListOf<Configurer>()
    internal val configurers: List<Configurer>
        get() = _configurers

    companion object {
        fun newInstance(): XmlCalabash {
            return newInstance(DefaultXmlCalabashConfiguration())
        }

        fun newInstance(config: XmlCalabashConfiguration): XmlCalabash {
            val xmlCalabash = XmlCalabash(config)
            xmlCalabash._commonEnvironment = CommonEnvironment(xmlCalabash)

            val defaultReporter = DefaultMessageReporter(xmlCalabash.xmlCalabashConfig.messagePrinter, LoggingMessageReporter())
            defaultReporter.threshold = config.verbosity
            xmlCalabash._commonEnvironment.messageReporter = { BufferingMessageReporter(config.messageBufferSize, defaultReporter) }

            val environment = PipelineCompilerContext(xmlCalabash)
            val saxonConfig = SaxonConfiguration.newInstance(environment)
            xmlCalabash._saxonConfig = saxonConfig

            val library = StandardLibrary.getInstance(environment, saxonConfig)
            for (decl in library.children.filterIsInstance<DeclareStepInstruction>()) {
                if (decl.type != null && decl.visibility != Visibility.PRIVATE) {
                    environment.commonEnvironment._standardSteps[decl.type!!] = decl
                }
            }

            for (provider in ConfigurerServiceProvider.providers()) {
                val configurer = provider.create()
                xmlCalabash._configurers.add(configurer)
                configurer.configure(xmlCalabash)
                configurer.configureSaxon(saxonConfig.configuration)
                xmlCalabash.commonEnvironment.configure(configurer)
            }

            return xmlCalabash
        }
    }

    fun print(message: String) {
        xmlCalabashConfig.messagePrinter.print(message)
    }

    fun println(message: String) {
        xmlCalabashConfig.messagePrinter.println(message)
    }

    fun newPipelineBuilder(): PipelineBuilder {
        return PipelineBuilder.newInstance(saxonConfig.newConfiguration())
    }

    fun newPipelineBuilder(version: Double): PipelineBuilder {
        return PipelineBuilder.newInstance(saxonConfig.newConfiguration(), version)
    }

    fun newXProcParser(): XplParser {
        return newXProcParser(newPipelineBuilder())
    }

    fun newXProcParser(builder: PipelineBuilder): XplParser {
        return XplParser(builder)
    }

    private val executables = mutableMapOf<Long, Stack<XProcExecutionContext>>()

    internal fun newExecutionContext(stepConfig: XProcStepConfiguration): XProcExecutionContext {
        synchronized(executables) {
            var stack = executables[Thread.currentThread().id]
            val context = if (stack == null) {
                stack = Stack()
                executables[Thread.currentThread().id] = stack
                XProcExecutionContext(stepConfig)
            } else {
                if (stack.isEmpty()) {
                    XProcExecutionContext(stepConfig)
                } else {
                    XProcExecutionContext(stack.peek()!!)
                }
            }
            stack.push(context)
            //println("New ${this}: ${context} for ${Thread.currentThread().id} (${stack.size})")
            return context
        }
    }

    internal fun getExecutionContext(): XProcExecutionContext {
        synchronized(executables) {
            val stack: Stack<XProcExecutionContext> = executables[Thread.currentThread().id]!!
            //println("Get ${this}: ${stack.peek()} for ${Thread.currentThread().id} (${stack.size})")
            return stack.peek()
        }
    }

    internal fun setExecutionContext(dynamicContext: XProcExecutionContext) {
        synchronized(executables) {
            var stack = executables[Thread.currentThread().id]
            if (stack == null) {
                stack = Stack()
                executables[Thread.currentThread().id] = stack
            }
            stack.push(dynamicContext)
            //println("Set ${this}: ${dynamicContext} for ${Thread.currentThread().id} (${stack.size})")
        }
    }

    internal fun releaseExecutionContext() {
        synchronized(executables) {
            val stack: Stack<XProcExecutionContext> = executables[Thread.currentThread().id]!!
            val context = stack.pop()
            //println("Rel ${this}: ${context} for ${Thread.currentThread().id} (${stack.size})")
        }
    }

    internal fun discardExecutionContext() {
        synchronized(executables) {
            val stack: Stack<XProcExecutionContext>? = executables[Thread.currentThread().id]
            if (stack != null) {
                stack.clear()
            }
            //println("Discard ${this}: ${context} for ${Thread.currentThread().id}")
        }
    }

    internal fun preserveExecutionContext(): Stack<XProcExecutionContext> {
        synchronized(executables) {
            val stack = executables[Thread.currentThread().id] ?: Stack()
            val saveStack = Stack<XProcExecutionContext>()
            while (stack.isNotEmpty()) {
                saveStack.push(stack.pop())
            }
            return saveStack
        }
    }

    internal fun restoreExecutionContext(contextStack: Stack<XProcExecutionContext>) {
        synchronized(executables) {
            var stack: Stack<XProcExecutionContext>? = executables[Thread.currentThread().id]
            if (stack == null) {
                stack = Stack()
                executables[Thread.currentThread().id] = stack
            }
            stack.clear()
            while (contextStack.isNotEmpty()) {
                stack.push(contextStack.pop())
            }
        }
    }

    internal fun addProperties(doc: XProcDocument?) {
        if (doc == null) {
            return
        }
        val stack: Stack<XProcExecutionContext> = executables[Thread.currentThread().id]!!
        stack.peek()!!.addProperties(doc)
    }

    internal fun removeProperties(doc: XProcDocument?) {
        if (doc == null) {
            return
        }
        val stack: Stack<XProcExecutionContext> = executables[Thread.currentThread().id]!!
        stack.peek()!!.removeProperties(doc)
    }
}
