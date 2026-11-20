package demo.teavm;

import org.eclipse.epsilon.flexmi.xml.Location;

import java.util.ArrayDeque;
import java.util.Deque;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.UserDataHandler;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Builds a DOM from SAX events and records the {@link Location} of each element and
 * processing instruction as user data under {@link Location#ID}, as Flexmi expects.
 * <p>
 * Flexmi builds the same DOM with an identity XSLT transformation from a SAX source to a DOM
 * result, recording locations through DOM mutation events (its LocationRecorder). Xerces has
 * no XSLT engine, so in the browser Flexmi parses with this class instead (see {@link Replacements}).
 */
public class DomBuilder extends DefaultHandler implements LexicalHandler {

	protected static final UserDataHandler LOCATION_DATA_HANDLER = new UserDataHandler() {
		@Override
		public void handle(short operation, String key, Object data, Node src, Node dst) {
			if (src != null && dst != null) {
				Object location = src.getUserData(Location.ID);
				if (location != null) {
					dst.setUserData(Location.ID, location, this);
				}
			}
		}
	};

	protected final Document document;
	protected final Deque<Node> nodes = new ArrayDeque<>();
	protected final Deque<int[]> starts = new ArrayDeque<>();
	protected final StringBuilder text = new StringBuilder();
	protected Locator locator;

	public DomBuilder(Document document) {
		this.document = document;
		nodes.push(document);
	}

	/** Parses the source into a new document. */
	public static Document parse(InputSource source) throws Exception {
		Document document = newDocument();
		parse(source, document);
		return document;
	}

	/** Parses the source and appends its content to the document. */
	public static void parse(InputSource source, Document document) throws Exception {
		XMLReader reader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
		DomBuilder builder = new DomBuilder(document);
		reader.setContentHandler(builder);
		reader.setErrorHandler(builder);
		reader.setProperty("http://xml.org/sax/properties/lexical-handler", builder);
		reader.parse(source);
	}

	public static Document newDocument() throws ParserConfigurationException {
		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		document.setStrictErrorChecking(false);
		return document;
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		appendText();
		Element element = document.createElementNS(uri, qName);
		for (int i = 0; i < attributes.getLength(); i++) {
			if (attributes.getLocalName(i).isEmpty()) {
				element.setAttribute(attributes.getQName(i), attributes.getValue(i));
			}
			else {
				element.setAttributeNS(attributes.getURI(i), attributes.getQName(i), attributes.getValue(i));
			}
		}
		nodes.peek().appendChild(element);
		nodes.push(element);
		starts.push(new int[] {locator.getLineNumber(), locator.getColumnNumber()});
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		appendText();
		Node element = nodes.pop();
		int[] start = starts.pop();
		setLocation(element, new Location(start[0], start[1], locator.getLineNumber(), locator.getColumnNumber()));
	}

	@Override
	public void characters(char[] ch, int start, int length) {
		if (nodes.peek() != document) {
			text.append(ch, start, length);
		}
	}

	@Override
	public void processingInstruction(String target, String data) {
		appendText();
		Node pi = nodes.peek().appendChild(document.createProcessingInstruction(target, data));
		setLocation(pi, new Location(locator.getLineNumber(), locator.getColumnNumber(),
				locator.getLineNumber(), locator.getColumnNumber()));
	}

	@Override
	public void comment(char[] ch, int start, int length) {
		appendText();
		nodes.peek().appendChild(document.createComment(new String(ch, start, length)));
	}

	protected void appendText() {
		if (text.length() > 0) {
			nodes.peek().appendChild(document.createTextNode(text.toString()));
			text.setLength(0);
		}
	}

	protected void setLocation(Node node, Location location) {
		node.setUserData(Location.ID, location, LOCATION_DATA_HANDLER);
	}

	@Override public void startDTD(String name, String publicId, String systemId) {}
	@Override public void endDTD() {}
	@Override public void startEntity(String name) {}
	@Override public void endEntity(String name) {}
	@Override public void startCDATA() {}
	@Override public void endCDATA() {}
}
