package io.kestra.plugin.kvm;

import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.libvirt.Domain;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Utility class for parsing Libvirt XML configurations.
 */
class LibvirtXmlParser {
    /**
     * Extracts local disk file paths from a Libvirt domain's XML description.
     *
     * @param domain The Libvirt domain to parse.
     * @return A list of file paths for 'disk' devices of type 'file'.
     * @throws Exception If XML parsing or XPath evaluation fails.
     */
    static Map<String, List<String>> getVolumesGroupedByPool(Domain domain) throws Exception {
        String xml = domain.getXMLDesc(0);

        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));

        XPath xpathFactory = XPathFactory.newInstance().newXPath();

        NodeList nodes = (NodeList) xpathFactory.evaluate(
                "/domain/devices/disk[@type='volume' and @device='disk']/source",
                doc,
                XPathConstants.NODESET);

        return IntStream.range(0, nodes.getLength())
                .mapToObj(i -> (Element) nodes.item(i))
                .collect(Collectors.groupingBy(
                        el -> el.getAttribute("pool"),
                        Collectors.mapping(el -> el.getAttribute("volume"), Collectors.toList())));
    }
}