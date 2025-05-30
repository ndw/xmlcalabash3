package com.xmlcalabash.steps.file

import com.xmlcalabash.io.MediaType
import com.xmlcalabash.documents.XProcDocument
import com.xmlcalabash.exceptions.XProcError
import com.xmlcalabash.namespace.Ns
import com.xmlcalabash.namespace.NsC
import com.xmlcalabash.namespace.NsP
import com.xmlcalabash.util.SaxonTreeBuilder
import com.xmlcalabash.util.UriUtils
import com.xmlcalabash.util.Urify
import java.io.File
import java.nio.file.Files

open class DirectoryListStep(): FileStep(NsP.directoryList) {
    private lateinit var rootPath: String
    private var detailed = false
    private val includeFilters = mutableListOf<String>()
    private val excludeFilters = mutableListOf<String>()

    override fun run() {
        super.run()

        val path = uriBinding(Ns.path)!!
        detailed = booleanBinding(Ns.detailed)!!
        val maxDepth = stringBinding(Ns.maxDepth)!!

        if (options.containsKey(Ns.includeFilter)) {
            val include = valueBinding(Ns.includeFilter)
            for (item in include.value.iterator()) {
                includeFilters.add(item.stringValue)
            }
        }

        if (options.containsKey(Ns.excludeFilter)) {
            val exclude = valueBinding(Ns.excludeFilter)
            for (item in exclude.value.iterator()) {
                excludeFilters.add(item.stringValue)
            }
        }

        if (path.scheme != "file") {
            throw stepConfig.exception(XProcError.xcUnsupportedScheme(path.scheme))
        }

        rootPath = path.toString()
        val dir = File(UriUtils.path(path))
        if (!dir.isDirectory) {
            throw stepConfig.exception(XProcError.xcNotADirectory(UriUtils.path(path)))
        }

        val depth = if (maxDepth == "unbounded") {
            -1
        } else {
            try {
                val num = Integer.parseInt(maxDepth)
                if (num < 0) {
                    throw stepConfig.exception(XProcError.xdValueDoesNotSatisfyType(maxDepth, "xs:nonNegativeInteger"))
                }
                num
            } catch (e: NumberFormatException) {
                throw stepConfig.exception(XProcError.xdValueDoesNotSatisfyType(maxDepth, "xs:nonNegativeInteger"))
            }
        }

        val builder = SaxonTreeBuilder(stepConfig)
        builder.startDocument(dir.toURI())
        val hierarchy = matchingFiles(dir, depth)
        directoryList(builder, hierarchy)
        builder.endDocument()
        val list = builder.result

        receiver.output("result", XProcDocument.ofXml(list, stepConfig))
    }

    protected open fun matchingFiles(dir: File, depth: Int): DirectoryEntry {
        return directoryList(dir, depth, true)
    }

    protected open fun directoryList(dir: File, depth: Int, root: Boolean = false): DirectoryEntry {
        var include = true
        var overrideContentType: MediaType? = null
        if (!root && (includeFilters.isNotEmpty() || excludeFilters.isNotEmpty() || overrideContentTypes.isNotEmpty())) {
            val path = if (dir.isDirectory) {
                dir.absolutePath + "/"
            } else {
                dir.absolutePath
            }

            val urifyPath = Urify.urify(path)
            val matchString = urifyPath.substring(rootPath.length + 1)

            if (includeFilters.isNotEmpty()) {
                include = false
                for (filter in includeFilters) {
                    if (filter.toRegex().find(matchString) != null) {
                        include = true
                        break
                    }
                }
            }

            if (include && excludeFilters.isNotEmpty()) {
                for (filter in excludeFilters) {
                    if (filter.toRegex().find(matchString) != null) {
                        include = false
                        break
                    }
                }
            }

            if (include) {
                for (pair in overrideContentTypes) {
                    if (pair.first.toRegex().find(matchString) != null) {
                        overrideContentType = pair.second
                        break
                    }
                }
            }
        }

        if (dir.isDirectory) {
            val entry = DirectoryDir(dir, include)

            // N.B. If depth starts negative, this is unbounded
            if (dir.isDirectory && depth != 0) {
                for (file in dir.listFiles()!!) {
                    val child = directoryList(file, depth - 1)
                    if (child.include) {
                        entry.include = true
                        entry.entries.add(child)
                    }
                }
            }

            return entry
        } else {
            val ctype = overrideContentType
                ?: MediaType.parse(stepConfig.documentManager.mimetypesFileTypeMap.getContentType(dir))
            return DirectoryFile(dir, include, ctype)
        }
    }

    protected open fun directoryList(builder: SaxonTreeBuilder, entry: DirectoryEntry, parent: DirectoryDir? = null) {
        val atts = if (entry is DirectoryFile) {
            fileAttributes(entry.file, detailed, entry.contentType, parent?.file)
        } else {
            fileAttributes(entry.file, detailed, null, parent?.file)
        }

        if (Files.isRegularFile(entry.file.toPath())) {
            builder.addStartElement(NsC.file, atts)
        } else {
            if (entry is DirectoryDir) {
                builder.addStartElement(NsC.directory, atts)
            } else {
                builder.addStartElement(NsC.other, atts)
            }
        }

        if (entry is DirectoryDir) {
            for (child in entry.entries) {
                directoryList(builder, child, entry)
            }
        }

        builder.addEndElement()
    }
}