package com.xmlcalabash.datamodel

import com.xmlcalabash.io.MediaType
import net.sf.saxon.s9api.QName
import java.net.URI

interface StepContainerInterface {
    var psviRequired: Boolean?
    var xpathVersion: Double?
    var version: Double?
    fun option(name: QName): OptionInstruction
    fun option(name: QName, staticValue: XProcExpression): OptionInstruction
    fun declareStep(): DeclareStepInstruction
    fun importFunctions(href: URI, contentType: MediaType?, namespace: String?): ImportFunctionsInstruction
    fun import(import: StepContainerInterface)
}