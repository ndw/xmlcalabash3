package com.xmlcalabash.spi

import com.xmlcalabash.config.StepConfiguration
import com.xmlcalabash.documents.XProcDocument
import com.xmlcalabash.io.MediaType
import net.sf.saxon.s9api.QName
import net.sf.saxon.s9api.XdmValue

interface ContentTypeConverter {
    fun canConvert(from: MediaType, to: MediaType): Boolean
    fun convert(stepConfig: StepConfiguration, doc: XProcDocument, convertTo: MediaType, serialization: Map<QName, XdmValue>): XProcDocument
}