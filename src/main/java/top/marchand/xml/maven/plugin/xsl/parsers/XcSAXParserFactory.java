/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package top.marchand.xml.maven.plugin.xsl.parsers;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.apache.xerces.jaxp.SAXParserFactoryImpl;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.EntityResolver2;

/**
 *
 * @author cmarchand
 */
public class XcSAXParserFactory extends SAXParserFactory {
    private final SAXParserFactory innerFactory;
    ThreadLocal<EntityResolver2> thEntityResolver;
    
    public XcSAXParserFactory() {
        innerFactory = new SAXParserFactoryImpl() {
            @Override
            public SAXParser newSAXParser() throws ParserConfigurationException {
                return new XcSAXParser(super.newSAXParser());
            }
        };
    }

    @Override
    public SAXParser newSAXParser() throws ParserConfigurationException, SAXException {
        return innerFactory.newSAXParser();
    }

    @Override
    public void setFeature(String name, boolean value) throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
        innerFactory.setFeature(name, value);
    }

    @Override
    public boolean getFeature(String name) throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
        return innerFactory.getFeature(name);
    }
    
    class XcSAXParser extends SAXParser {
        private ThreadLocal<EntityResolver2> thEntityResolver;

        private final SAXParser innerParser;
        public XcSAXParser(SAXParser innerParser) {
            super();
            this.innerParser=innerParser;
        }

        @Override @SuppressWarnings("deprecation")
        public Parser getParser() throws SAXException {
            return innerParser.getParser();
        }

        @Override
        public XMLReader getXMLReader() throws SAXException {
            XMLReader reader = innerParser.getXMLReader();
            if(thEntityResolver!=null && thEntityResolver.get()!=null) {
                reader.setEntityResolver(thEntityResolver.get());
            }
            return reader;
        }

        @Override
        public boolean isNamespaceAware() {
            return innerParser.isNamespaceAware();
        }

        @Override
        public boolean isValidating() {
            return innerParser.isValidating();
        }

        @Override
        public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
            innerParser.setProperty(name, value);
        }

        @Override
        public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
            return innerParser.getProperty(name);
        }
    }
    
}
