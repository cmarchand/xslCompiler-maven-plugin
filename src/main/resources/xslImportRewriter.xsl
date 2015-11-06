<?xml version="1.0" encoding="UTF-8"?>
<!--
Copyright © 2015, Christophe Marchand
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the <organization> nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xd="http://www.oxygenxml.com/ns/doc/xsl"
    exclude-result-prefixes="xs xd" version="2.0">
    
    <xsl:param name="p_libraries" as="xs:string?" required="yes"/>
    <xsl:param name="p_baseUrl" as="xs:string" required="yes"/>

    <xsl:variable name="libraries" as="xs:string*" select="tokenize($p_libraries, ',')"/>
    <xsl:variable name="baseUrl" as="xs:string"
        select="
            if (ends-with($p_baseUrl, '/')) then
                $p_baseUrl
            else
                concat($p_baseUrl, '/')"/>

    <xsl:template match="xsl:import | xsl:include">
        <xsl:variable name="newPath">
            <xsl:choose>
                <xsl:when test="contains(@href, ':')">
                    <xsl:variable name="prefixUrl" select="concat(substring-before(@href, ':'),':')" as="xs:string*"/>
                    <xsl:variable name="postfixUri" select="substring-after(@href, ':')" as="xs:string"/>
                    <xsl:choose>
                        <xsl:when test="$prefixUrl = $libraries">
                            <xsl:variable name="relativeUri" select="substring(base-uri(.), string-length($baseUrl), string-length(base-uri(.))-string-length($baseUrl)+1)"/>
                            <xsl:variable name="depth" as="xs:integer" select="count(tokenize($relativeUri, '/'))-1"/>
                            <xsl:choose>
                                <xsl:when test="$depth = 1"><xsl:value-of select="substring($postfixUri,2)"/></xsl:when>
                                <xsl:otherwise>
                                    <xsl:variable name="path" as="xs:string+">
                                        <xsl:for-each select="1 to ($depth - 1)"><xsl:value-of select="'..'"/></xsl:for-each><xsl:value-of select="substring($postfixUri,2)"/>
                                    </xsl:variable>
                                    <xsl:value-of select="string-join($path,'/')"/>
                                </xsl:otherwise>
                            </xsl:choose>
                        </xsl:when>
                    </xsl:choose>
                </xsl:when>
                <xsl:otherwise><xsl:value-of select="@href"/></xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:copy>
            <xsl:attribute name="href" select="$newPath"/>
            <xsl:apply-templates select="@* except @href"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="*">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:apply-templates select="node() | comment() | processing-instruction()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="@* | text() | comment() | processing-instruction()">
        <xsl:copy-of select="."/>
    </xsl:template>
</xsl:stylesheet>
