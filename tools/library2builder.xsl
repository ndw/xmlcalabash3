<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:cx="http://xmlcalabash.com/ns/extensions"
                xmlns:f="http://xmlcalabash.com/ns/functions"
                xmlns:e="http://www.w3.org/1999/XSL/Spec/ElementSyntax"
                xmlns:p="http://www.w3.org/ns/xproc"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="#all"
                expand-text="yes"
                version="3.0">

<xsl:output method="text" encoding="utf-8"/>

<xsl:variable name="nl" select="'&#10;'"/>

<xsl:template match="p:library">
  <xsl:text>package com.xmlcalabash.datamodel{$nl}</xsl:text>
  <xsl:text>{$nl}</xsl:text>

  <xsl:text>import com.xmlcalabash.io.MediaType{$nl}</xsl:text>
  <xsl:text>import com.xmlcalabash.namespace.NsCx{$nl}</xsl:text>
  <xsl:text>import com.xmlcalabash.namespace.NsP{$nl}</xsl:text>
  <xsl:text>import com.xmlcalabash.namespace.NsXml{$nl}</xsl:text>
  <xsl:text>import com.xmlcalabash.namespace.NsXs{$nl}</xsl:text>
  <xsl:text>import net.sf.saxon.s9api.QName{$nl}</xsl:text>
  <xsl:text>import net.sf.saxon.s9api.XdmAtomicValue{$nl}</xsl:text>
  <xsl:text>import java.net.URI{$nl}</xsl:text>
  <xsl:text>{$nl}</xsl:text>

  <xsl:text>// This source file is automatically generated from tools/library.xpl{$nl}</xsl:text>
  <xsl:text>// Do not edit this file by hand.{$nl}</xsl:text>
  <xsl:text>{$nl}</xsl:text>

  <xsl:text>class StandardLibrary private constructor(builder: PipelineBuilder, private val stepConfig: InstructionConfiguration) {{{$nl}</xsl:text>
  <xsl:text>    companion object {{{$nl}</xsl:text>
  <xsl:text>        internal fun getInstance(builder: PipelineBuilder): StandardLibraryInstruction {{{$nl}</xsl:text>
  <xsl:text>            val nodeContext = builder.stepConfig{$nl}</xsl:text>
  <xsl:text>                .with("p", NsP.namespace){$nl}</xsl:text>
  <xsl:text>                .with("cx", NsCx.namespace){$nl}</xsl:text>
  <xsl:text>                .with("xml", NsXml.namespace){$nl}</xsl:text>
  <xsl:text>                .with("xs", NsXs.namespace){$nl}</xsl:text>
  <xsl:text>                .with(Location(URI("https://xmlcalabash.com/library/library.xpl"))){$nl}</xsl:text>
  <xsl:text>            val localConfig = InstructionConfiguration(builder.stepConfig, nodeContext){$nl}</xsl:text>
  <xsl:text>            val standardLibrary = StandardLibrary(builder, localConfig){$nl}</xsl:text>
  <xsl:text>            val library = standardLibrary.create(){$nl}</xsl:text>
  <xsl:text>            library.findDeclarations(){$nl}</xsl:text>
  <xsl:text>            library.elaborateInstructions(){$nl}</xsl:text>
  <xsl:text>            return library{$nl}</xsl:text>
  <xsl:text>        }}{$nl}</xsl:text>
  <xsl:text>    }}{$nl}</xsl:text>
  <xsl:text>{$nl}</xsl:text>
  <xsl:text>    private val library = StandardLibraryInstruction(builder, stepConfig){$nl}</xsl:text>
  <xsl:text>{$nl}</xsl:text>

  <xsl:text>    private fun create(): StandardLibraryInstruction {{{$nl}</xsl:text>
  <xsl:text>        library.version = 3.1{$nl}</xsl:text>
  <xsl:for-each select="p:declare-step">
    <xsl:text>        {f:method-name(@type)}(){$nl}</xsl:text>
  </xsl:for-each>

  <xsl:text>        return library{$nl}</xsl:text>
  <xsl:text>    }}{$nl}</xsl:text>
  <xsl:apply-templates select="p:declare-step"/>
  <xsl:text>}}{$nl}</xsl:text>
</xsl:template>

<xsl:template match="p:declare-step">
  <xsl:for-each select="@* except (@type|@xml:id)">
    <xsl:message>Unexpected attribute: {node-name(.)}</xsl:message>
  </xsl:for-each>

  <xsl:text>{$nl}    private fun {f:method-name(@type)}(): DeclareStepInstruction {{{$nl}</xsl:text>
  <xsl:text>        val decl = library.declareAtomicStep(){$nl}</xsl:text>
  <xsl:text>        decl._type = stepConfig.typeUtils.parseQName("{@type}"){$nl}{$nl}</xsl:text>
  <xsl:if test="p:input">
    <xsl:sequence select="if (count(p:input) = 1) then '        val ' else '        var '"/>
    <xsl:apply-templates select="p:input"/>
  </xsl:if>
  <xsl:if test="p:output">
    <xsl:if test="p:input">{$nl}</xsl:if>
    <xsl:sequence select="if (count(p:output) = 1) then '        val ' else '        var '"/>
    <xsl:apply-templates select="p:output"/>
  </xsl:if>
  <xsl:if test="p:option">
    <xsl:if test="p:input or p:output">{$nl}</xsl:if>
    <xsl:sequence select="if (count(p:option) = 1) then '        val ' else '        var '"/>
    <xsl:apply-templates select="p:option"/>
  </xsl:if>
  <xsl:text>{$nl}        return decl{$nl}</xsl:text>
  <xsl:text>    }}{$nl}</xsl:text>

  <xsl:for-each select="* except (p:input|p:output|p:option)">
    <xsl:message>Unexpected element: {node-name(.)}</xsl:message>
  </xsl:for-each>
</xsl:template>

<xsl:template match="p:input">
  <xsl:if test="preceding-sibling::p:input">
    <xsl:text>        </xsl:text>
  </xsl:if>

  <xsl:for-each select="@* except (@port|@primary|@sequence|@content-types)">
    <xsl:message>Unexpected attribute: {node-name(.)}</xsl:message>
  </xsl:for-each>

  <xsl:variable name="primary" select="(@primary, count(../p:input) = 1)[1]"/>
  <xsl:variable name="sequence" select="@sequence = 'true'"/>

  <xsl:text>input = decl.input("{@port}", primary={$primary}, sequence={$sequence}){$nl}</xsl:text>

  <xsl:apply-templates select="@content-types">
    <xsl:with-param name="varname" select="'input'"/>
  </xsl:apply-templates>

  <xsl:apply-templates mode="connections">
    <xsl:with-param name="varname" select="'input'"/>
  </xsl:apply-templates>
</xsl:template>

<xsl:template match="p:output">
  <xsl:if test="preceding-sibling::p:output">
    <xsl:text>        </xsl:text>
  </xsl:if>

  <xsl:for-each select="@* except (@port|@primary|@sequence|@content-types)">
    <xsl:message>Unexpected attribute: {node-name(.)}</xsl:message>
  </xsl:for-each>

  <xsl:variable name="primary" select="(@primary, count(../p:output) = 1)[1]"/>
  <xsl:variable name="sequence" select="@sequence = 'true'"/>

  <xsl:text>output = decl.output("{@port}", primary={$primary}, sequence={$sequence}){$nl}</xsl:text>

  <xsl:apply-templates select="@content-types">
    <xsl:with-param name="varname" select="'output'"/>
  </xsl:apply-templates>

  <xsl:apply-templates mode="connections">
    <xsl:with-param name="varname" select="'output'"/>
  </xsl:apply-templates>
</xsl:template>

<xsl:template match="p:option">
  <xsl:if test="preceding-sibling::p:option">
    <xsl:text>        </xsl:text>
  </xsl:if>

  <xsl:for-each select="@* except (@name|@as|@select|@required|@values|@e:type)">
    <xsl:message>Unexpected attribute: {node-name(.)}</xsl:message>
  </xsl:for-each>

  <xsl:choose>
    <xsl:when test="contains(@name, ':')">
      <xsl:text>option = decl.option(stepConfig.parseQName("{@name}")){$nl}</xsl:text>
    </xsl:when>
    <xsl:otherwise>
      <xsl:text>option = decl.option(QName("{@name}")){$nl}</xsl:text>
    </xsl:otherwise>
  </xsl:choose>

  <xsl:if test="@select">
    <xsl:variable name="expr" select="replace(@select, '&quot;', '\\&quot;') => replace('\$', '\\\$')"/>
    <xsl:text>        option.select = XProcExpression.select(stepConfig, "{$expr}"){$nl}</xsl:text>
  </xsl:if>

  <xsl:if test="@as">
    <xsl:text>        option.asType = stepConfig.typeUtils.parseSequenceType("{@as}"){$nl}</xsl:text>
  </xsl:if>
  <xsl:if test="@e:type">
    <xsl:text>        option.specialType = stepConfig.typeUtils.parseSpecialType("{@e:type}"){$nl}</xsl:text>
  </xsl:if>

  <xsl:if test="@values">
    <xsl:variable name="expr" select="string(@values)"/>
    <xsl:variable name="values" as="xs:anyAtomicType*">
      <xsl:evaluate xpath="$expr" as="xs:anyAtomicType*"/>
    </xsl:variable>
    <xsl:text>        option.values = listOf(</xsl:text>
    <xsl:for-each select="$values">
      <xsl:if test="position() gt 1">, </xsl:if>
      <xsl:choose>
        <xsl:when test=". instance of xs:string">
          <xsl:text>XdmAtomicValue("{.}")</xsl:text>
        </xsl:when>
        <xsl:otherwise>
          <xsl:text>XdmAtomicValue({.})</xsl:text>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each>
    <xsl:text>){$nl}</xsl:text>
  </xsl:if>

  <xsl:if test="@required">
    <xsl:text>        option.required = true{$nl}</xsl:text>
  </xsl:if>

  <xsl:apply-templates mode="connections">
    <xsl:with-param name="varname" select="'option'"/>
  </xsl:apply-templates>
</xsl:template>

<xsl:template match="@content-types">
  <xsl:param name="varname" as="xs:string"/>
  <xsl:text>        {$varname}.contentTypes = MediaType.parseList("{.}"){$nl}</xsl:text>
</xsl:template>

<xsl:template match="p:empty" mode="connections">
  <xsl:param name="varname" as="xs:string"/>
  <xsl:text>        {$varname}.empty(){$nl}</xsl:text>
</xsl:template>

<xsl:template match="p:pipe" mode="connections">
  <xsl:param name="varname" as="xs:string"/>
  <xsl:message terminate="yes">p:pipe not implemented</xsl:message>
</xsl:template>

<xsl:template match="p:document" mode="connections">
  <xsl:param name="varname" as="xs:string"/>
  <xsl:message terminate="yes">p:document not implemented</xsl:message>
</xsl:template>

<xsl:template match="p:inline" mode="connections">
  <xsl:param name="varname" as="xs:string"/>
  <xsl:message terminate="yes">p:inline not implemented</xsl:message>
</xsl:template>

<xsl:template match="*" mode="connections">
  <xsl:param name="varname" as="xs:string"/>
  <xsl:message terminate="yes">Forbidden: <xsl:sequence select="node-name(.)"/></xsl:message>
</xsl:template>

<xsl:template match="text()|comment()" mode="connections">
  <p:param name="varname" as="xs:string"/>
</xsl:template>

<xsl:function name="f:method-name" as="xs:string">
  <xsl:param name="type" as="xs:string"/>
  <xsl:variable name="prefix" select="substring-before($type, ':')"/>
  <xsl:variable name="local" select="substring-after($type, ':')"/>
  <xsl:variable name="parts" as="xs:string+">
    <xsl:sequence select="$prefix"/>
    <xsl:sequence select="tokenize($local, '-') ! f:upper-first(.)"/>
  </xsl:variable>
  <xsl:sequence select="string-join($parts, '')"/>
</xsl:function>

<xsl:function name="f:upper-first" as="xs:string">
  <xsl:param name="text" as="xs:string"/>
  <xsl:sequence select="upper-case(substring($text, 1, 1)) || substring($text, 2)"/>
</xsl:function>

</xsl:stylesheet>
