package com.xmlcalabash.steps

import com.xmlcalabash.io.MediaType
import com.xmlcalabash.documents.XProcDocument
import com.xmlcalabash.io.DocumentConverter
import com.xmlcalabash.namespace.Ns
import net.sf.saxon.s9api.QName
import net.sf.saxon.s9api.XdmValue

open class CastContentTypeStep(): AbstractAtomicStep() {
    lateinit var document: XProcDocument
    var docContentType = MediaType.ANY
    var contentType = MediaType.ANY
    var parameters = mapOf<QName,XdmValue>()

    override fun run() {
        super.run()
        document = queues["source"]!!.first()

        docContentType = document.contentType ?: MediaType.OCTET_STREAM
        contentType = mediaTypeBinding(Ns.contentType)
        parameters = qnameMapBinding(Ns.parameters)

        if (contentType == docContentType) {
            receiver.output("result", document)
            return
        }

        val converter = DocumentConverter(stepConfig, document, contentType, parameters)
        val result = converter.convert()
        receiver.output("result", result)
    }
    override fun toString(): String = "p:cast-content-type"
}