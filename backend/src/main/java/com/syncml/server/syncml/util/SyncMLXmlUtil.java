package com.syncml.server.syncml.util;

import com.syncml.server.syncml.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.util.*;

/**
 * SyncML XML 파싱 및 생성 유틸리티
 */
@Component
@Slf4j
public class SyncMLXmlUtil {

    /**
     * XML 문자열을 SyncMLMessage로 파싱
     */
    public SyncMLMessage parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));

        Element root = doc.getDocumentElement();
        if (!"SyncML".equals(root.getLocalName())) {
            throw new IllegalArgumentException("Not a SyncML document");
        }

        SyncHdr syncHdr = parseSyncHdr(getFirstChildElement(root, "SyncHdr"));
        SyncMLMessage.SyncBody syncBody = parseSyncBody(getFirstChildElement(root, "SyncBody"));

        return SyncMLMessage.builder()
                .syncHdr(syncHdr)
                .syncBody(syncBody)
                .build();
    }

    private SyncHdr parseSyncHdr(Element hdrElement) {
        if (hdrElement == null) return null;

        SyncHdr.SyncHdrBuilder builder = SyncHdr.builder()
                .verDTD(getTextContent(hdrElement, "VerDTD"))
                .verProto(getTextContent(hdrElement, "VerProto"))
                .sessionId(getTextContent(hdrElement, "SessionID"))
                .msgId(parseInteger(getTextContent(hdrElement, "MsgID")));

        // Target
        Element target = getFirstChildElement(hdrElement, "Target");
        if (target != null) {
            builder.targetUri(getTextContent(target, "LocURI"));
        }

        // Source
        Element source = getFirstChildElement(hdrElement, "Source");
        if (source != null) {
            builder.sourceUri(getTextContent(source, "LocURI"));
            builder.sourceName(getTextContent(source, "LocName"));
        }

        // Cred
        Element credElement = getFirstChildElement(hdrElement, "Cred");
        if (credElement != null) {
            Cred cred = parseCred(credElement);
            builder.cred(cred);
        }

        return builder.build();
    }

    private Cred parseCred(Element credElement) {
        Cred.CredBuilder builder = Cred.builder()
                .data(getTextContent(credElement, "Data"));

        Element meta = getFirstChildElement(credElement, "Meta");
        if (meta != null) {
            builder.format(getTextContent(meta, "Format"));
            builder.type(getTextContent(meta, "Type"));
        }

        return builder.build();
    }

    private SyncMLMessage.SyncBody parseSyncBody(Element bodyElement) {
        if (bodyElement == null) return null;

        List<Alert> alerts = new ArrayList<>();
        List<Status> statuses = new ArrayList<>();
        List<Result> results = new ArrayList<>();
        List<Command> commands = new ArrayList<>();
        boolean finalFlag = false;

        NodeList children = bodyElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;

            Element elem = (Element) node;
            String name = elem.getLocalName();

            switch (name) {
                case "Alert" -> alerts.add(parseAlert(elem));
                case "Status" -> statuses.add(parseStatus(elem));
                case "Results" -> results.add(parseResults(elem));
                case "Get", "Exec", "Replace", "Add", "Delete" ->
                    commands.add(parseCommand(elem, name));
                case "Final" -> finalFlag = true;
            }
        }

        return SyncMLMessage.SyncBody.builder()
                .alerts(alerts)
                .statuses(statuses)
                .results(results)
                .commands(commands)
                .finalFlag(finalFlag)
                .build();
    }

    private Alert parseAlert(Element elem) {
        Alert.AlertBuilder builder = Alert.builder()
                .cmdId(parseInteger(getTextContent(elem, "CmdID")))
                .data(parseInteger(getTextContent(elem, "Data")))
                .correlator(getTextContent(elem, "Correlator"));

        Element item = getFirstChildElement(elem, "Item");
        if (item != null) {
            Alert.Item alertItem = Alert.Item.builder()
                    .sourceUri(getLocUri(item, "Source"))
                    .targetUri(getLocUri(item, "Target"))
                    .data(getTextContent(item, "Data"))
                    .build();
            builder.item(alertItem);
        }

        return builder.build();
    }

    private Status parseStatus(Element elem) {
        return Status.builder()
                .cmdId(parseInteger(getTextContent(elem, "CmdID")))
                .msgRef(parseInteger(getTextContent(elem, "MsgRef")))
                .cmdRef(parseInteger(getTextContent(elem, "CmdRef")))
                .cmd(getTextContent(elem, "Cmd"))
                .data(parseInteger(getTextContent(elem, "Data")))
                .targetRef(getTextContent(elem, "TargetRef"))
                .sourceRef(getTextContent(elem, "SourceRef"))
                .build();
    }

    private Result parseResults(Element elem) {
        Result.ResultBuilder builder = Result.builder()
                .cmdId(parseInteger(getTextContent(elem, "CmdID")))
                .msgRef(parseInteger(getTextContent(elem, "MsgRef")))
                .cmdRef(parseInteger(getTextContent(elem, "CmdRef")));

        List<Result.Item> items = new ArrayList<>();
        NodeList itemNodes = elem.getElementsByTagName("Item");
        for (int i = 0; i < itemNodes.getLength(); i++) {
            Element itemElem = (Element) itemNodes.item(i);
            items.add(Result.Item.builder()
                    .sourceUri(getLocUri(itemElem, "Source"))
                    .targetUri(getLocUri(itemElem, "Target"))
                    .data(getTextContent(itemElem, "Data"))
                    .build());
        }
        builder.items(items);

        return builder.build();
    }

    private Command parseCommand(Element elem, String cmdType) {
        Command.CommandType type = Command.CommandType.valueOf(cmdType.toUpperCase());

        List<Command.Item> items = new ArrayList<>();
        NodeList itemNodes = elem.getElementsByTagName("Item");
        for (int i = 0; i < itemNodes.getLength(); i++) {
            Element itemElem = (Element) itemNodes.item(i);
            items.add(Command.Item.builder()
                    .sourceUri(getLocUri(itemElem, "Source"))
                    .targetUri(getLocUri(itemElem, "Target"))
                    .data(getTextContent(itemElem, "Data"))
                    .build());
        }

        return Command.builder()
                .type(type)
                .cmdId(parseInteger(getTextContent(elem, "CmdID")))
                .items(items)
                .build();
    }

    // ========== XML 생성 ==========

    /**
     * SyncMLMessage를 XML 문자열로 변환
     */
    public String toXml(SyncMLMessage message) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element syncML = doc.createElement("SyncML");
        syncML.setAttribute("xmlns", "SYNCML:SYNCML1.2");
        doc.appendChild(syncML);

        // SyncHdr
        if (message.getSyncHdr() != null) {
            syncML.appendChild(createSyncHdr(doc, message.getSyncHdr()));
        }

        // SyncBody
        if (message.getSyncBody() != null) {
            syncML.appendChild(createSyncBody(doc, message.getSyncBody()));
        }

        return documentToString(doc);
    }

    private Element createSyncHdr(Document doc, SyncHdr hdr) {
        Element elem = doc.createElement("SyncHdr");

        appendTextElement(doc, elem, "VerDTD", hdr.getVerDTD());
        appendTextElement(doc, elem, "VerProto", hdr.getVerProto());
        appendTextElement(doc, elem, "SessionID", hdr.getSessionId());
        appendTextElement(doc, elem, "MsgID", String.valueOf(hdr.getMsgId()));

        // Target
        if (hdr.getTargetUri() != null) {
            Element target = doc.createElement("Target");
            appendTextElement(doc, target, "LocURI", hdr.getTargetUri());
            elem.appendChild(target);
        }

        // Source
        if (hdr.getSourceUri() != null) {
            Element source = doc.createElement("Source");
            appendTextElement(doc, source, "LocURI", hdr.getSourceUri());
            if (hdr.getSourceName() != null) {
                appendTextElement(doc, source, "LocName", hdr.getSourceName());
            }
            elem.appendChild(source);
        }

        // Cred
        if (hdr.getCred() != null) {
            elem.appendChild(createCred(doc, hdr.getCred()));
        }

        return elem;
    }

    private Element createCred(Document doc, Cred cred) {
        Element elem = doc.createElement("Cred");

        Element meta = doc.createElement("Meta");
        if (cred.getFormat() != null) {
            appendTextElement(doc, meta, "Format", cred.getFormat());
        }
        if (cred.getType() != null) {
            appendTextElement(doc, meta, "Type", cred.getType());
        }
        elem.appendChild(meta);

        appendTextElement(doc, elem, "Data", cred.getData());

        return elem;
    }

    private Element createSyncBody(Document doc, SyncMLMessage.SyncBody body) {
        Element elem = doc.createElement("SyncBody");

        // Status
        if (body.getStatuses() != null) {
            for (Status status : body.getStatuses()) {
                elem.appendChild(createStatus(doc, status));
            }
        }

        // Commands
        if (body.getCommands() != null) {
            for (Command cmd : body.getCommands()) {
                elem.appendChild(createCommand(doc, cmd));
            }
        }

        // Final
        if (body.isFinalFlag()) {
            elem.appendChild(doc.createElement("Final"));
        }

        return elem;
    }

    private Element createStatus(Document doc, Status status) {
        Element elem = doc.createElement("Status");

        appendTextElement(doc, elem, "CmdID", String.valueOf(status.getCmdId()));
        appendTextElement(doc, elem, "MsgRef", String.valueOf(status.getMsgRef()));
        appendTextElement(doc, elem, "CmdRef", String.valueOf(status.getCmdRef()));
        appendTextElement(doc, elem, "Cmd", status.getCmd());
        appendTextElement(doc, elem, "Data", String.valueOf(status.getData()));

        if (status.getTargetRef() != null) {
            appendTextElement(doc, elem, "TargetRef", status.getTargetRef());
        }
        if (status.getSourceRef() != null) {
            appendTextElement(doc, elem, "SourceRef", status.getSourceRef());
        }

        // Chal (인증 챌린지)
        if (status.getChal() != null) {
            elem.appendChild(createChal(doc, status.getChal()));
        }

        return elem;
    }

    private Element createChal(Document doc, Status.Chal chal) {
        Element elem = doc.createElement("Chal");

        Element meta = doc.createElement("Meta");
        appendTextElement(doc, meta, "Type", chal.getType());
        appendTextElement(doc, meta, "Format", chal.getFormat());
        if (chal.getNonce() != null) {
            Element nextNonce = doc.createElement("NextNonce");
            nextNonce.setTextContent(chal.getNonce());
            meta.appendChild(nextNonce);
        }
        elem.appendChild(meta);

        return elem;
    }

    private Element createCommand(Document doc, Command cmd) {
        Element elem = doc.createElement(cmd.getType().name().substring(0, 1)
                + cmd.getType().name().substring(1).toLowerCase());

        appendTextElement(doc, elem, "CmdID", String.valueOf(cmd.getCmdId()));

        if (cmd.getItems() != null) {
            for (Command.Item item : cmd.getItems()) {
                Element itemElem = doc.createElement("Item");

                if (item.getTargetUri() != null) {
                    Element target = doc.createElement("Target");
                    appendTextElement(doc, target, "LocURI", item.getTargetUri());
                    itemElem.appendChild(target);
                }
                if (item.getSourceUri() != null) {
                    Element source = doc.createElement("Source");
                    appendTextElement(doc, source, "LocURI", item.getSourceUri());
                    itemElem.appendChild(source);
                }
                if (item.getData() != null) {
                    appendTextElement(doc, itemElem, "Data", item.getData());
                }

                elem.appendChild(itemElem);
            }
        }

        return elem;
    }

    // ========== Helper Methods ==========

    private Element getFirstChildElement(Element parent, String name) {
        NodeList list = parent.getElementsByTagName(name);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private String getTextContent(Element parent, String childName) {
        Element child = getFirstChildElement(parent, childName);
        return child != null ? child.getTextContent() : null;
    }

    private String getLocUri(Element parent, String containerName) {
        Element container = getFirstChildElement(parent, containerName);
        return container != null ? getTextContent(container, "LocURI") : null;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void appendTextElement(Document doc, Element parent, String name, String value) {
        if (value != null) {
            Element elem = doc.createElement(name);
            elem.setTextContent(value);
            parent.appendChild(elem);
        }
    }

    private String documentToString(Document doc) throws TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}

