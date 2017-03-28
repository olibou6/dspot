package fr.inria.diversify.mutant.descartes;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 23/03/17
 */
public class DescartesInjector {

    private final static String[] mutators = {"null", "void", "0", "false"};

    private static Node getNodeNamedFromOrBuildIfDoesnotExist(Document doc, Node startNode, String name, Node parent) {
        Node currentNode = DescartesChecker.getNodeNamedFrom(startNode, name);
        if (currentNode == null) {
            currentNode = doc.createElement(name);
            parent.appendChild(currentNode);
        }
        return currentNode;
    }

    private static List<Node> buildNodesDependency(Document doc, String groupIdValue,
                                                   String artifactIdValue, String versionValue) {
        final Element groupId = doc.createElement("groupId");
        groupId.setTextContent(groupIdValue);
        final Element artifactId = doc.createElement("artifactId");
        artifactId.setTextContent(artifactIdValue);
        final Element version = doc.createElement("version");
        version.setTextContent(versionValue);
        return Arrays.asList(groupId, artifactId, version);
    }

    private static Node buildDependencyToPitTest(Document doc) {
        final Element dependency = doc.createElement("dependency");
        buildNodesDependency(doc, "org.pitest", "pitest-maven", "1.1.11").forEach(dependency::appendChild);
        return dependency;
    }

    private static Node buildPlugin(Document doc) {
        final Element plugin = doc.createElement("plugin");
        buildNodesDependency(doc, "org.pitest", "pitest-maven", "1.1.11").forEach(plugin::appendChild);
        plugin.appendChild(buildConfiguration(doc));
        plugin.appendChild(buildDependencies(doc));
        return plugin;
    }

    private static Node buildDependency(Document doc) {
        final Element dependency = doc.createElement("dependency");
        buildNodesDependency(doc, "fr.inria.stamp", "descartes", "0.1-SNAPSHOT").forEach(dependency::appendChild);
        return dependency;
    }

    private static Node buildDependencies(Document doc) {
        final Element dependencies = doc.createElement("dependencies");
        dependencies.appendChild(buildDependency(doc));
        return dependencies;
    }

    private static Node buildMutators(Document doc, String name) {
        final Element mutator = doc.createElement("mutator");
        mutator.setTextContent(name);
        return mutator;
    }

    private static List<Node> buildListOfMutators(Document doc) {
        return Arrays.stream(mutators)
                .collect(ArrayList<Node>::new,
                        (nodes, name) -> nodes.add(buildMutators(doc, name)),
                        ArrayList<Node>::addAll
                );
    }

    private static Node buildConfiguration(Document doc) {
        final Element configuration = doc.createElement("configuration");
        final Element mutationEngine = doc.createElement("mutationEngine");
        mutationEngine.setTextContent("descartes");
        configuration.appendChild(mutationEngine);
        final Element mutators = doc.createElement("mutators");
        buildListOfMutators(doc).forEach(mutators::appendChild);
        configuration.appendChild(mutators);
        return configuration;
    }

    public static void injectDescartesIntoPom(String pathToPom) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(pathToPom);

            final Node root = doc.getFirstChild();
            getNodeNamedFromOrBuildIfDoesnotExist(doc, root,
                    "dependencies", root)
                    .appendChild(buildDependencyToPitTest(doc));
            getNodeNamedFromOrBuildIfDoesnotExist(doc, root,
                    "plugins",
                    getNodeNamedFromOrBuildIfDoesnotExist(doc, root,
                            "build", root))
                    .appendChild(buildPlugin(doc));

            // write the content into xml file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(pathToPom));
            transformer.transform(source, result);
        } catch (ParserConfigurationException | TransformerException | IOException | SAXException pce) {
            throw new RuntimeException(pce);
        }
    }
}