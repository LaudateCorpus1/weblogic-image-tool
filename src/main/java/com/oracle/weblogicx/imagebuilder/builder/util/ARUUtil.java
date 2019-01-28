/* Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved. */

package com.oracle.weblogicx.imagebuilder.builder.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static com.oracle.weblogicx.imagebuilder.builder.impl.meta.FileMetaDataResolver.META_RESOLVER;
import static com.oracle.weblogicx.imagebuilder.builder.util.ARUConstants.ARU_LANG_URL;
import static com.oracle.weblogicx.imagebuilder.builder.util.ARUConstants.CACHE_KEY_SEPARATOR;
import static com.oracle.weblogicx.imagebuilder.builder.util.ARUConstants.CONFLICTCHECKER_URL;
import static com.oracle.weblogicx.imagebuilder.builder.util.ARUConstants.FMW_PROD_ID;
import static com.oracle.weblogicx.imagebuilder.builder.util.ARUConstants.LATEST_PSU_URL;
import static com.oracle.weblogicx.imagebuilder.builder.util.ARUConstants.PATCH_SEARCH_URL;
import static com.oracle.weblogicx.imagebuilder.builder.util.ARUConstants.REL_URL;
import static com.oracle.weblogicx.imagebuilder.builder.util.ARUConstants.WLS_PROD_ID;

public class ARUUtil {

    /**
     * Return All WLS releases information
     *
     * @param userId userid for support account
     * @param password password for support account
     * @throws IOException  when failed to access the aru api
     */

    public static Document getAllWLSReleases(String userId, String password) throws IOException {
        return getAllReleases("WLS", userId, password);
    }

    /**
     * Return release number of a WLS release by version
     *
     * @param version WLS version 12.2.1.3.0 etc ...
     * @param userId  user id for support account
     * @param password password for support account
     * @return release number or empty string if not found
     * @throws IOException when failed to access the aru api
     */
    private static String getWLSReleaseNumber(String version, String userId, String password) throws
        IOException {
        return getReleaseNumber("WLS", version, userId, password);
    }

    /**
     * Return release number of a FMW release by version
     *
     * @param version WLS version 12.2.1.3.0 etc ...
     * @param userId  user id for support account
     * @param password password for support account
     * @return release number or empty string if not found
     * @throws IOException when failed to access the aru api
     */
    private static String getFMWReleaseNumber(String version, String userId, String password) throws
        IOException {
        return getReleaseNumber("FMW", version, userId, password);
    }


    /**
     * Return All FMW releases information
     *
     * @param userId userid for support account
     * @param password password for support account
     * @throws IOException  when failed to access the aru api
     */
    public static void getAllFMWReleases(String userId, String password) throws IOException {
        getAllReleases("FMW", userId, password);
    }


    /**
     * Download the latest PSU for given category and release
     * @param category WLS or FMW
     * @param version version number like 12.2.1.3.0
     * @param userId user
     * @param password password
     * @return bug number
     * @throws IOException when failed
     */
    public static String getLatestPSUFor(String category, String version, String userId, String password) throws IOException {
        String releaseNumber = getReleaseNumber(category, version, userId, password);
        return getLatestPSU(category, releaseNumber, userId, password);
    }

    /**
     * Get list of PSU available for given category and release
     * @param category WLS or FMW
     * @param version version number like 12.2.1.3.0
     * @param userId user
     * @return Document listing of all patches (full details)
     * @throws IOException when failed
     */
    public static Document getAllPSUFor(String category, String version, String userId, String password) throws IOException {
        String releaseNumber = getReleaseNumber(category, version, userId, password);
        return getAllPSU(category, releaseNumber, userId, password);
    }



    /**
     * Download a list of FMW patches
     *
     * @param patches  A list of patches number
     * @param userId user email
     * @param password password
     * @return List of bug numbers
     * @throws IOException  when failed to access the aru api
     */
    public static List<String> getPatchesFor(String category, String version, List<String> patches, String userId, String password)
            throws
            IOException {
        List<String> results = new ArrayList<>();
        for (String patch : patches) {
            String rs = getPatch(category, version, patch, userId, password);
            if (rs != null) {
                results.add(rs);
            }
        }
        return results;
    }

    /**
     *
     * @param patches  A list of patches number
     * @param category WLS or FMW
     * @param version version of the prduct
     * @param userId userid for support account
     * @param password password for support account
     * @throws IOException  when failed to access the aru api
     */

    public static boolean validatePatches(List<String> patches, String category, String version, String userId, String
        password) throws IOException {

        StringBuffer payload = new StringBuffer
            ("<conflict_check_request><platform>2000</platform><target_patch_list/>");
        String releaseNumber = getReleaseNumber(category, version, userId, password);

        for (String patch : patches) {
            payload.append(String.format("<candidate_patch_list rel_id=\"%s\">%s</conflict_check_request>",
                releaseNumber, patch));
        }
        payload.append("</conflict_check_request>");

        Document result = HttpUtil.postCheckConflictRequest(CONFLICTCHECKER_URL, payload.toString(), userId, password);

        try {
            NodeList conflictSets = XPathUtil.applyXPathReturnNodeList(result, "/conflict_sets");
            if (conflictSets.getLength() == 0) {


                try {
                    String expression = "/conflict_check/conflict_sets/set/merge_patches";

                    NodeList nodeList = XPathUtil.applyXPathReturnNodeList(result, expression);

                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = dbf.newDocumentBuilder();
                    Document doc = builder.newDocument();
                    Element element = doc.createElement("conflict_check_results");

                    for (int i = 0; i < nodeList.getLength(); i++) {
                        Node n = nodeList.item(i);
                        Node copyNode = doc.importNode(n, true);

                        if (n instanceof Element )
                            element.appendChild(copyNode);
                    }

                    doc.appendChild(element);
                    System.out.println("===================================================");
                    System.out.println("There are conflicts between the patches requested:-");
                    System.out.println("===================================================");
                    System.out.println("");
                    XPathUtil.prettyPrint(doc);

                    return true;


                } catch (XPathExpressionException | ParserConfigurationException xpe) {
                    throw new IOException(xpe);
                }

            }

        } catch (XPathExpressionException xpe) {
            throw new IOException(xpe);

        }
        return false;
    }

    private static  Document getAllReleases(String category, String userId, String password) throws IOException {


        Document allReleases = HttpUtil.getXMLContent(REL_URL, userId, password);

        try {

            String expression;

            if ("WLS".equalsIgnoreCase(category)) {
                expression = "/results/release[starts-with(text(), 'Oracle WebLogic Server')]";
            } else {
                expression = "/results/release[starts-with(text(), 'Fusion Middleware Upgrade')]";
            }
            NodeList nodeList = XPathUtil.applyXPathReturnNodeList(allReleases, expression);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document doc = builder.newDocument();
            Element element = doc.createElement("results");

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node n = nodeList.item(i);
                Node copyNode = doc.importNode(n, true);

                if (n instanceof Element )
                    element.appendChild(copyNode);
            }

            doc.appendChild(element);
            //XPathUtil.prettyPrint(doc);

            return doc;

        } catch (XPathExpressionException | ParserConfigurationException xpe) {
            throw new IOException(xpe);
        }
    }

    private static String getLatestPSU(String category, String release, String userId, String password) throws
        IOException {

        String expression;
        if ("WLS".equalsIgnoreCase(category))
            expression = String.format(LATEST_PSU_URL, WLS_PROD_ID, release);
        else
            expression = String.format(LATEST_PSU_URL, FMW_PROD_ID, release);

        Document allPatches = HttpUtil.getXMLContent(expression, userId, password);
        return savePatch(allPatches, userId, password);
    }

    private static Document getAllPSU(String category, String release, String userId, String password) throws
        IOException {

        String expression;
        if ("WLS".equalsIgnoreCase(category))
            expression = String.format(LATEST_PSU_URL, WLS_PROD_ID, release);
        else
            expression = String.format(LATEST_PSU_URL, FMW_PROD_ID, release);

        return HttpUtil.getXMLContent(expression, userId, password);
    }

    private static String getPatch(String category, String version, String bugNumber, String userId, String password)
        throws
        IOException {

        String releaseNumber = getReleaseNumber(category, version, userId, password );
        String url;
        if ("WLS".equalsIgnoreCase(category))
            url = String.format(PATCH_SEARCH_URL, WLS_PROD_ID, bugNumber, releaseNumber);
        else
            url = String.format(PATCH_SEARCH_URL, FMW_PROD_ID, bugNumber, releaseNumber);

        Document allPatches = HttpUtil.getXMLContent(url, userId, password);

        return savePatch(allPatches, userId, password);
    }

    private static String savePatch(Document allPatches, String userId, String password) throws IOException {
        try {

            // TODO: needs to make sure there is one and some filtering if not sorting

            String downLoadLink = XPathUtil.applyXPathReturnString(allPatches, "string"
                + "(/results/patch[1]/files/file/download_url/text())");

            String downLoadHost = XPathUtil.applyXPathReturnString(allPatches, "string"
                + "(/results/patch[1]/files/file/download_url/@host)");

            String bugName  = XPathUtil.applyXPathReturnString(allPatches, "/results/patch[1]/name");

            String releaseNumber = XPathUtil.applyXPathReturnString(allPatches,
                "string(/results/patch[1]/release/@id)");

            String key = bugName + CACHE_KEY_SEPARATOR + releaseNumber;

            int index = downLoadLink.indexOf("patch_file=");

            if (index > 0) {
                String fileName = META_RESOLVER.getCacheDir() + File.separator + downLoadLink.substring(
                        index+"patch_file=".length());
                // this hasMatchingKeyValue is to make sure that the file value is same as the intended location.
                // cache dir can be changed
                if (!new File(fileName).exists() || !META_RESOLVER.hasMatchingKeyValue(key, fileName)) {
                    HttpUtil.downloadFile(downLoadHost+downLoadLink, fileName, userId, password);
                    META_RESOLVER.addToCache(key, fileName);
                } else {
                    System.out.println(String.format("patch %s already downloaded for bug %s", fileName, key));
                }
                return bugName;
            }

        } catch (XPathExpressionException xpe) {
            throw new IOException(xpe);
        }

        return null;
    }

    private static String getReleaseNumber(String category, String version, String userId, String password) throws
        IOException {
        Document allReleases = getAllReleases(category, userId, password);

        String expression = String.format("string(/results/release[@name = '%s']/@id)", version);
        try {
            return XPathUtil.applyXPathReturnString(allReleases, expression);
        } catch (XPathExpressionException xpe) {
            throw new IOException(xpe);
        }
    }

    public static boolean checkCredentials(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return false;
        }
        try {
            HttpUtil.getXMLContent(ARU_LANG_URL, username, password);
        } catch (IOException e) {
            Throwable cause = (e.getCause() == null)? e : e.getCause();
            if (cause.getClass().isAssignableFrom(HttpResponseException.class) &&
                ((HttpResponseException) cause).getStatusCode() == HttpStatus.SC_UNAUTHORIZED ) {
                return false;
            }
        }
        return true;
    }


}

