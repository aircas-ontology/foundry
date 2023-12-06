package com.aircas.ptr.foundry.common.util;

import de.odysseus.staxon.json.JsonXMLConfig;
import de.odysseus.staxon.json.JsonXMLConfigBuilder;
import de.odysseus.staxon.json.JsonXMLInputFactory;
import de.odysseus.staxon.json.JsonXMLOutputFactory;
import de.odysseus.staxon.xml.util.PrettyXMLEventWriter;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlJsonUtil {

    /**
     * xml to json
     */
    public static String xmlToJson(String xmlString, String root) {

        StringReader input = new StringReader(xmlString);
        StringWriter output = new StringWriter();
        JsonXMLConfig config = new JsonXMLConfigBuilder().autoArray(true).autoPrimitive(true).prettyPrint(true).virtualRoot(root)
                .repairingNamespaces(true).build();
        try {
            XMLEventReader reader = XMLInputFactory.newInstance().createXMLEventReader(input);
            XMLEventWriter writer = new JsonXMLOutputFactory(config).createXMLEventWriter(output);
            writer.add(reader);
            reader.close();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                output.close();
                input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return output.toString();
    }

    /**
     * json to xml
     */
    public static String jsonToXml(String jsonString, String root) {
        StringReader input = new StringReader(jsonString);
        StringWriter output = new StringWriter();
        JsonXMLConfig config = new JsonXMLConfigBuilder().multiplePI(false).repairingNamespaces(false).virtualRoot(root).build();
        try {
            XMLEventReader reader = new JsonXMLInputFactory(config).createXMLEventReader(input);
            XMLEventWriter writer = XMLOutputFactory.newInstance().createXMLEventWriter(output);
            writer = new PrettyXMLEventWriter(writer);
            writer.add(reader);
            reader.close();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                output.close();
                input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return output.toString();
    }

    /**
     * 去掉xml中的换行和空格
     */
    public static String jsonToXmlReplaceBlank(String jsonString, String root) {
        String str = XmlJsonUtil.jsonToXml(jsonString, root);
        String dest = "";
        if (str != null) {
            Pattern p = Pattern.compile("\\s*|\t|\r|\n");
            Matcher m = p.matcher(str);
            dest = m.replaceAll("");
        }
        return dest;
    }

    public static void main(String[] args) {
        String s = "<?xml version=\"1.0\" encoding=\"GB2312\"?>\n" +
                "<InterFaceFile>\n" +
                "  <FileHeader>\n" +
                "    <messageType>DISPDTTREADY</messageType>\n" +
                "    <messageID>000002</messageID>\n" +
                "    <originatorAddress>IAS-C</originatorAddress>\n" +
                "    <recipientAddress>SHARE</recipientAddress>\n" +
                "    <creationTime>2022-01-01T01:13:33</creationTime>\n" +
                "  </FileHeader>\n" +
                "  <FileBody>\n" +
                "    <reportID />\n" +
                "    <reportTime />\n" +
                "    <taskID>211229_IAS-C-TR_00018_001</taskID>\n" +
                "    <proPlanID>000690410</proPlanID>\n" +
                "    <result>TRUE</result>\n" +
                "    <remark />\n" +
                "    <disAddress>侦察处3,指挥所</disAddress>\n" +
                "    <productType1 />\n" +
                "    <startTime>2021-12-31T19:08:37</startTime>\n" +
                "    <endTime>2021-12-31T19:18:36</endTime>\n" +
                "    <targetName />\n" +
                "    <prodLonUL />\n" +
                "    <prodLatUL />\n" +
                "    <prodLonBR />\n" +
                "    <prodLatBR />\n" +
                "    <productID />\n" +
                "    <productName />\n" +
                "    <productCaption />\n" +
                "    <productKeys />\n" +
                "    <productDisc />\n" +
                "    <productMagazine />\n" +
                "    <productCreator />\n" +
                "    <productAssessor />\n" +
                "    <productType>DZ</productType>\n" +
                "    <productLevel>E2</productLevel>\n" +
                "    <productPathName>IAS-C/JB8A-1/2022/0101/211229_IAS-C-TR_00018_001\\E2.rar</productPathName>\n" +
                "    <informationFile />\n" +
                "  </FileBody>\n" +
                "</InterFaceFile>";
        System.out.println(XmlJsonUtil.xmlToJson(s, "InterFaceFile"));
    }

}
