package com.xmlcalabash.tracing

import com.xmlcalabash.documents.XProcDocument
import com.xmlcalabash.io.XProcSerializer
import com.xmlcalabash.namespace.Ns
import com.xmlcalabash.runtime.XProcStepConfiguration
import com.xmlcalabash.util.SaxonTreeBuilder
import net.sf.saxon.s9api.QName
import net.sf.saxon.s9api.XdmValue
import net.sf.saxon.value.QNameValue
import java.io.FileOutputStream
import java.nio.file.Files
import kotlin.io.path.Path

class DetailTraceListener: StandardTraceListener() {
    val savedDocuments = mutableMapOf<Long, String>()

    override fun sendDocument(from: Pair<String, String>, to: Pair<String, String>, document: XProcDocument) {
        super.sendDocument(from, to, document)

        if (document.id !in savedDocuments) {
            val path = Path("/tmp/x/trace")
            val prefix = "${from.first}."
            val suffix = document.contentType?.extension() ?: ".bin"
            val tempFile = Files.createTempFile(path, prefix, suffix).toFile()
            savedDocuments[document.id] = tempFile.absolutePath

            val inputMap = document.properties.getSerialization()
            val map = mutableMapOf<QName, XdmValue>()
            for (key in inputMap.keySet()) {
                val value = inputMap.get(key)
                val qvalue = key.underlyingValue
                val qkey = if (qvalue is QNameValue) {
                    QName(qvalue.prefix, qvalue.namespaceURI.toString(), qvalue.localName)
                } else {
                    throw RuntimeException("Expected map of QName keys")
                }
                map.put(qkey, value)
            }

            val serializer = XProcSerializer(document.context.processor)
            val fileOutputStream = FileOutputStream(tempFile)
            serializer.write(document, fileOutputStream, document.contentType, map)
            fileOutputStream.close()
        }
    }

    override fun documentSummary(config: XProcStepConfiguration, builder: SaxonTreeBuilder, detail: DocumentDetail) {
        val _id = QName("id")
        val _port = QName("port")
        val _filename = QName("filename")

        val atts = mutableMapOf<QName, String>()
        atts[_id] = "${detail.id}"
        if (detail.contentType != null) {
            atts[Ns.contentType] = detail.contentType.toString()
        }
        builder.addStartElement(NsTrace.document, config.attributeMap(atts))

        builder.addStartElement(NsTrace.from, config.attributeMap(mapOf(
            _id to detail.from.first,
            _port to detail.from.second)))
        builder.addEndElement()
        builder.addStartElement(NsTrace.to, config.attributeMap(mapOf(
            _id to detail.to.first,
            _port to detail.to.second)))
        builder.addEndElement()
        builder.addStartElement(NsTrace.location, config.attributeMap(mapOf(
            _filename to savedDocuments[detail.id]!!,
        )))
        builder.addEndElement()
        builder.addEndElement()
    }

}