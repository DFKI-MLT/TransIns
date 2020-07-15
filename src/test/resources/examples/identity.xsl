<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version="2.0" >
	
	<xsl:output method="xml" encoding="UTF-8"/>
		
	<!-- This is a simple identity function -->	
	<xsl:template match="@*|node()|processing-instruction()|comment()">
		<xsl:copy>
			<xsl:apply-templates select="node()|@*|text()|processing-instruction()|comment()"/>
		</xsl:copy>
	</xsl:template>		
	
	<xsl:template match="comment()">
		<xsl:comment><xsl:value-of select="."/></xsl:comment> 
	</xsl:template>		
</xsl:stylesheet> 