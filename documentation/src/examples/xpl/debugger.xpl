<p:declare-step xmlns:p="http://www.w3.org/ns/xproc"
                xmlns:ex="https://xmlcalabash.com/ns/examples"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                version="3.0">
<p:output port="result" sequence="true"/>

<p:declare-step type="ex:ident">
  <p:input port="source" sequence="true"/>
  <p:output port="result" sequence="true"/>
  <p:identity/>
</p:declare-step>

<p:xinclude>
  <p:with-input href="../xml/default-input.xml"/>
</p:xinclude>

<ex:ident name="ex1"/>

<p:for-each>
  <p:with-input select="/ex:doc/*"/>
  <p:identity name="id1"/>
  <p:add-attribute name="add"
                   attribute-name="role" attribute-value="test"/>
</p:for-each>

<p:wrap-sequence expand-text="false"
                 wrapper="Q{https://xmlcalabash.com/ns/examples}set"/>

<p:identity name="id2"/>

</p:declare-step>
