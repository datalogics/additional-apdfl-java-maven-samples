/*
 * PDFAuditor
 *
 * Copyright (c) 2024 Datalogics, Inc. All rights reserved.
 *
 * The information and code in this sample is for the exclusive use of Datalogics customers and evaluation users only.
 * Datalogics permits you to use, modify and distribute this file in accordance with the terms of your license
 * agreement. Sample code is for demonstrative purposes only and is not intended for production use.
 *
 */
package com.datalogics.pdfl.samples.InformationExtraction.AuditInfo;

import com.datalogics.PDFL.*;

import java.io.IOException;
import java.util.*;

class ObjectEvaluator extends PDFObjectEnumProc {

    private final HashSet<Integer> evaluatedObjects;
    private final HashMap<String,Long> byteTracker;
    private Boolean hasStreamXRef;
    private int highestindirectId;

    enum cosType { cosNull, Array, Boolean, Dict, Integer, Name, Real, Stream, String }

    static cosType getObjType(PDFObject obj)
    {
        if (obj instanceof PDFBoolean) { return cosType.Boolean; }
        else if (obj instanceof PDFInteger) { return cosType.Integer; }
        else if (obj instanceof PDFReal) { return cosType.Real; }
        else if (obj instanceof PDFName) { return cosType.Name; }
        else if (obj instanceof PDFString) { return cosType.String; }
        else if (obj instanceof PDFArray) { return cosType.Array; }
        else if (obj instanceof PDFDict) { return cosType.Dict; }
        else if (obj instanceof PDFStream) { return cosType.Stream; }
        else { return cosType.cosNull; }
    }

    ObjectEvaluator(HashMap<String,Long> sizeTracker)
    {
        evaluatedObjects = new HashSet<>();
        byteTracker =sizeTracker;
        hasStreamXRef = false; //until proven otherwise;
        highestindirectId = 0;
    }

    private static boolean hasType(PDFDict dict,String dictType)
    {
        return dict.contains("Type") && ((PDFName)dict.get("Type")).getValue().equals(dictType);
    }

    private static boolean hasSubtype(PDFDict dict,String dictType)
    {
        return dict.contains("Subtype") && ((PDFName)dict.get("Subtype")).getValue().equals(dictType);
    }

    private static boolean isObjectStream(PDFObject obj)
    {
        return (getObjType(obj) == cosType.Stream ) && hasType(((PDFStream)obj).getDict(),"ObjStm");
    }

    private static boolean isXRefStream(PDFObject obj)
    {
        return (getObjType(obj) == cosType.Stream ) && hasType(((PDFStream)obj).getDict(),"XRef");
    }

    private static boolean isImageXObject(PDFObject obj)
    {
        return (getObjType(obj) == cosType.Stream ) && hasSubtype(((PDFStream)obj).getDict(),"Image");
    }

    private static boolean isFormXObject(PDFObject obj)
    {
        return (getObjType(obj) == cosType.Stream ) && hasSubtype(((PDFStream)obj).getDict(),"Form");
    }

    private static boolean isMarkupAnnot(PDFDict dict)
    {
        final String[] markupAnnots = {"Text","FreeText","Line","Square","Circle","Polygon","PolyLine","Highlight","Underline","Squiggly","Stamp","Caret","Ink","Redact"};
        final Set<String> markups = new HashSet<>(Arrays.asList(markupAnnots));
        return dict.contains("Subtype") && markups.contains(((PDFName)dict.get("Subtype")).getValue());
    }

    private long sizeCosArray(PDFArray array, Boolean descend)
    {
        long bytesize=3; //[]
        if (array.getIndirect())
        {
            bytesize += String.valueOf(array.getID()).length() +1;
            bytesize += String.valueOf(array.getGeneration()).length() +5; // space + "obj" + space
        }

        for(int i = array.getLength()-1;i>=0;--i)
        {
            PDFObject arrayElem = array.get(i);
            if(arrayElem == null)
                continue;
            if(arrayElem.getIndirect()) {
                bytesize += String.valueOf(arrayElem.getID()).length() + 1;
                bytesize += String.valueOf(arrayElem.getGeneration()).length() + 2; //space + "R"
                if (descend && !evaluatedObjects.contains(arrayElem.getID())) {
                    bytesize += sizeObj(arrayElem, descend);
                    evaluatedObjects.add(arrayElem.getID());
                }
            }else
                bytesize += sizeObj(arrayElem,descend) +1;
        }

        return bytesize;
    }

    private long sizeCosDict(PDFDict dict,boolean descend)
    {
        long bytesize = 4; // <<>>
        if (dict.getIndirect())
        {
            bytesize += String.valueOf(dict.getID()).length() +1;
            bytesize += String.valueOf(dict.getGeneration()).length() +5; // space + "obj" + space
        }
        for (PDFObject pdfObject : dict.getKeys()) {
            PDFName key = (PDFName) pdfObject;
            bytesize += sizeObj(key, descend);

            PDFObject entry = dict.get(key);
            if (entry.getIndirect()) {
                bytesize += String.valueOf(entry.getID()).length() + 1;
                bytesize += String.valueOf(entry.getGeneration()).length() + 2; //space + "R"

                if(key.getValue().equals("P") || key.getValue().equals("Parent")) //no back-tracking to the parent object: avoids infinite-loops
                    continue;

                if (descend && !evaluatedObjects.contains(entry.getID())) {
                    bytesize += sizeObj(entry, descend);
                    evaluatedObjects.add(entry.getID());
                }
            } else
                bytesize += sizeObj(entry, descend) + 1;
        }

        return bytesize;
    }

    private long sizeCosStream(PDFStream stream,boolean descend)
    {
        long bytesize = 17; // stream\nendstream
        if(stream.getIndirect())
        {
            bytesize += String.valueOf(stream.getID()).length() +1;
            bytesize += String.valueOf(stream.getGeneration()).length() +5; // space + "obj" + space
        }
        bytesize += sizeObj(stream.getDict(),descend);
        bytesize += stream.getLength();

        return bytesize;
    }

    private long sizeObj(PDFObject obj, boolean descend)
    {
        switch(getObjType(obj)) {
            //these are the scalars.
            case cosNull:
                return 0;
            case Boolean:
                return (((PDFBoolean) obj).getValue()) ? 4 : 5;
            case Integer:
                return String.valueOf(((PDFInteger) obj).getValue()).length();
            case Real:
                return String.format("%01.5f", ((PDFReal) obj).getValue()).length();
            case Name:
                return ((PDFName) obj).getValue().length() + 1; //could be off for UTF8 Name objects or names with escapes
            case String:
                return ((PDFString) obj).getBytes().length;
            case Array:
                return sizeCosArray((PDFArray) obj,descend);
            case Dict:
                return sizeCosDict((PDFDict) obj,descend);
            case Stream:
                return sizeCosStream((PDFStream) obj,descend);
        }
        return 0;
    }

    private void record(int id, String category,long size)
    {
        if(evaluatedObjects.contains(id))
            return; // no double counting.

        long totalStreamSaving = byteTracker.get(category);
        byteTracker.put(category, totalStreamSaving + size);
        evaluatedObjects.add(id);
    }

    @Override
    public boolean Call(PDFObject obj, PDFObject val)
    {
        if(obj.getIndirect() &&  evaluatedObjects.contains(obj.getID()))
            return true;

        if(obj.getID() > highestindirectId)
            highestindirectId = obj.getID();

        if(getObjType(obj) == cosType.Stream)
        {
            if(isImageXObject(obj))
            {
                record(obj.getID(),PDFAuditor.IMAGES,sizeObj(obj,true));
            }
            else if (isFormXObject(obj)) {
                record(obj.getID(),PDFAuditor.XOBJECT_FORMS,sizeObj(obj,false));
                if(((PDFStream)obj).getDict().contains("Resources")) {
                    PDFDict streamDict = ((PDFStream) obj).getDict();
                    processResources((PDFDict) streamDict.get("Resources"));
                }

                if(((PDFStream)obj).getDict().contains("PieceInfo")) {
                    processPieceInfo(((PDFStream)obj).getDict());
                }

            }
            else if(isObjectStream(obj))
            {
                PDFStream objStrm =(PDFStream)obj;
                int decompressedSize=0;
                //find the decompressed size of the objects stored in this object stream.
                try {
                    decompressedSize = objStrm.getFilteredStream().readAllBytes().length;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // subtract out the decompressed size as the individual objects will be counted elsewhere; this avoids double-counting.
                record(obj.getID(),PDFAuditor.OBJECT_COMPRESSION,sizeObj(obj,false)-decompressedSize);
            }
            else if (isXRefStream(obj))
            {
                hasStreamXRef = true;
                record((obj.getID()),PDFAuditor.XREF_TABLE,sizeObj(obj,false) );
            }
        } else if (getObjType(obj) == cosType.Array)
        {
            PDFArray array = (PDFArray)obj;
            if(array.getLength() >0 ) {
                PDFObject firstArrayElem = array.get(0);
                if (getObjType(firstArrayElem) == cosType.Name) {
                    PDFName firstName = (PDFName) firstArrayElem;

                    // check for colorspace arrays
                    if (firstName.getValue().equals("ICCBased") ||
                            firstName.getValue().equals("Indexed") ||
                            firstName.getValue().equals("Separation") ||
                            firstName.getValue().equals("DeviceN") ||
                            firstName.getValue().equals("Lab") ||
                            firstName.getValue().equals("CalGray") ||
                            firstName.getValue().equals("CalRGB")) {
                        record((obj.getID()), PDFAuditor.COLOR_SPACES, sizeObj(obj,true));
                    }
                }
            }
        } else if (getObjType(obj) == cosType.Dict)
            {
                PDFDict dict= (PDFDict)obj;
                if(hasType(dict,"ExtGState"))
                    record(dict.getID(),PDFAuditor.EXT_GRAPHIC_STATES,sizeObj(dict,true));

                if(hasType(dict,"Font"))
                    record(dict.getID(),PDFAuditor.FONTS,sizeObj(dict,true));

                if(hasType(dict,"FileSpec"))
                    record(dict.getID(),PDFAuditor.EMBEDDED_FILES,sizeObj(dict,true));

                if(hasSubtype(dict,"Link"))
                    record(dict.getID(),PDFAuditor.LINK_ANNOTATIONS,sizeObj(dict,false)); //may need to dig deeper for full size.
                else if (hasSubtype(dict,"FileAttachment"))
                    processFileAttachmentAnnot(dict);
                else if (hasSubtype(dict,"3D"))
                    record(dict.getID(),PDFAuditor.COMMENTS_AND_3D,sizeObj(dict,true));
                else if (isMarkupAnnot(dict))
                    record(dict.getID(),PDFAuditor.COMMENTS_AND_3D,sizeObj(dict,true));
            }

        return true;
    }

    public void processFileAttachmentAnnot(PDFDict annotDict)
    {
        if(annotDict.contains("FS")) {
            PDFDict fsDict = (PDFDict) annotDict.get("FS");

            if (fsDict.contains("EF")) {
                PDFDict efDict = (PDFDict) fsDict.get("EF");
                if (efDict.contains("F")) {
                    PDFObject f = efDict.get("F");
                    record(f.getID(), PDFAuditor.EMBEDDED_FILES, sizeObj(f, true));
                }
            }
        }
        record(annotDict.getID(),PDFAuditor.COMMENTS_AND_3D,sizeObj(annotDict,true));
    }

    public void processResources(PDFDict resDict) {
        final String[] resourceCats = {"ExtGState", "ColorSpace", "Pattern", "Shading", "Font"};
        final String[] categories = {
                PDFAuditor.EXT_GRAPHIC_STATES, PDFAuditor.COLOR_SPACES, PDFAuditor.PATTERN,
                PDFAuditor.SHADING, PDFAuditor.FONTS
        };
        for (int i = resourceCats.length - 1; i >= 0; --i) {
            String resource = resourceCats[i];
            String category = categories[i];

            if (resDict.contains(resource)) {
                PDFDict catResDicts = (PDFDict) resDict.get(resource);
                if (catResDicts.getIndirect())
                    record(catResDicts.getID(), category, sizeObj(catResDicts, true));
                else {
                    for (PDFObject keyObj : catResDicts.getKeys()) {
                        PDFObject item = catResDicts.get((PDFName) keyObj);
                        if (item.getIndirect() && !evaluatedObjects.contains(item.getID()))
                            record(item.getID(), category, sizeObj(item, true));
                    }
                }
            }
        }
    }

    public void processSpiderInfo(PDFDict parent)
    {
        PDFObject spiderInfo = parent.get("SpiderInfo");
        if(spiderInfo.getIndirect())
            record(spiderInfo.getID(), PDFAuditor.WEB_CAPTURE,sizeObj(spiderInfo,true));
        else{
            byteTracker.put(PDFAuditor.WEB_CAPTURE,sizeObj(spiderInfo,true));
        }
    }

    public void processPieceInfo(PDFDict parent)
    {
        PDFObject pieceInfo = parent.get("PieceInfo");
        record(pieceInfo.getID(), PDFAuditor.PIECE_INFO,sizeObj(pieceInfo,true));

    }

    public void processPageDict(PDFDict pgDict)
    {
        if(pgDict.contains("Contents")) {
            PDFObject contents = pgDict.get("Contents");
            if(contents.getIndirect())
                record(contents.getID(), PDFAuditor.CONTENT_STREAMS, sizeObj(contents,true));
            else{
                Long cursize = byteTracker.get(PDFAuditor.CONTENT_STREAMS);
                cursize +=sizeObj(contents,true);
                byteTracker.put(PDFAuditor.CONTENT_STREAMS,cursize);
            }
        }
        if(pgDict.contains("Resources"))
            processResources((PDFDict)pgDict.get("Resources"));

        if(pgDict.contains("Thumb")) {
            PDFObject thumbnail = pgDict.get("Thumb");
            record(thumbnail.getID(), PDFAuditor.THUMBNAILS,sizeObj(thumbnail,false));
        }

        //page pieceInfo
        if(pgDict.contains("PieceInfo")) {
            processPieceInfo(pgDict);
        }

        evaluatedObjects.add(pgDict.getID());
    }

    public void processNameTrees(PDFDict namesDict)
    {
        final String[] namedTrees = {"Dests","EmbeddedFiles","IDS","URLS"};
        final String[] categories = {PDFAuditor.NAMED_DESTINATIONS,PDFAuditor.EMBEDDED_FILES,PDFAuditor.WEB_CAPTURE,PDFAuditor.WEB_CAPTURE};
        final Boolean[] descents ={false,true,true,true}; // might need to tweak this

        for(int i= namedTrees.length-1;i>=0;--i) {
            String namedTree = namedTrees[i];
            String category = categories[i];
            boolean descend =descents[i];

            if (namesDict.contains(namedTree)) {
                NameTree curNameTree = namesDict.getDocument().getNameTree(namedTree);
                HashSet<PDFString> namedItems = new HashSet<>();
                PDFObjectEnumProc treeIter = new PDFObjectEnumProc() {
                    @Override
                    public boolean Call(PDFObject pdfObject, PDFObject pdfObject1) {
                        namedItems.add(((PDFString) pdfObject));
                        return true;
                    }
                };
                curNameTree.enumEntries(treeIter);
                Iterator<PDFString> hashIt = namedItems.iterator();
                long cursize = byteTracker.get(category);
                while (hashIt.hasNext()) {
                    PDFString key = hashIt.next();
                    PDFObject obj = curNameTree.get(key);
                    cursize += sizeObj(key, false);
                    cursize += sizeObj(obj, descend);
                }
                byteTracker.put(category, cursize);
            }
        }

    }

    public void processAcroForm(PDFDict acroFormDict)
    {
        if(acroFormDict.contains("DR"))
            processResources((PDFDict)acroFormDict.get("DR"));

        record(acroFormDict.getID(),PDFAuditor.ACRO_FORMS,sizeObj(acroFormDict,true));
    }

    public void processStructTree(PDFDict structTree)
    {
        record(structTree.getID(),PDFAuditor.STRUCTURE_INFO,sizeObj(structTree,true));
    }

    public void processBookMarks(Bookmark node)
    {
        long cursize = byteTracker.get(PDFAuditor.BOOKMARKS);
        cursize+=sizeObj(node.getPDFDict(),false);
        byteTracker.put(PDFAuditor.BOOKMARKS,cursize);
        if(node.hasChildren())
        {
            processBookMarks(node.getFirstChild());
        }
        else{
            Bookmark nextNode = node.getNext();
            if(nextNode != null)
                processBookMarks(nextNode);
        }
    }

    public void determineXrefSize()
    {
        if(!hasStreamXRef && byteTracker.get(PDFAuditor.XREF_TABLE) ==0)
        {
            final long xRefEntrySize = 20L;
            byteTracker.put(PDFAuditor.XREF_TABLE, xRefEntrySize * highestindirectId);
        }
    }
}

/**
 * This class provides an easy way to audit a PDF, producing a map that
 * associates sizes with different categories. This auditor sizes the following
 * categories:
 *
 * *Acrobat Forms
 * *Bookmarks
 * *Comments and 3D Content
 * *Content Streams
 * *Color Spaces
 * *Cross Reference Table
 * *Document Overhead
 * *Embedded Files
 * *Extended Graphic States
 * *Fonts
 * *Images
 * *Link Annotations
 * *Named Destinations
 * *Piece Info
 * *Shading Info
 * *Structure Info
 * *Thumbnails
 * *XObject Forms
 * *Pattern Info
 * *Web Capture Info
 *
 * It also provides information on how many bytes were compressed in object
 * streams (represented as a negative number) and the total file size.
 *
 * NOTE: These sizes are not going to match the sizes returned by Acrobat and
 * were never intended to.
 *
 */
public class PDFAuditor {

    public PDFAuditor() {}

    public final static String ACRO_FORMS = "Acrobat Forms*";
    public final static String BOOKMARKS = "Bookmarks";
    public final static String COMMENTS_AND_3D = "Comments and 3D Content";
    public final static String CONTENT_STREAMS = "Content Streams";
    public final static String COLOR_SPACES = "Color Spaces*";
    public final static String DOC_OVERHEAD = "Document Overhead*";
    public final static String EMBEDDED_FILES = "Embedded Files*";
    public final static String EXT_GRAPHIC_STATES = "Extended Graphic States";
    public final static String FILE_SIZE = "Total file Size";
    public final static String FONTS = "Fonts";
    public final static String IMAGES = "Images";
    public final static String LINK_ANNOTATIONS = "Link Annotations";
    public final static String NAMED_DESTINATIONS = "Named Destinations*";
    public final static String OBJECT_COMPRESSION = "Object compression size reduction";
    public final static String STRUCTURE_INFO = "Structure Info*";
    public final static String THUMBNAILS = "Thumbnails";
    public final static String XOBJECT_FORMS = "XObject Forms";
    public final static String XREF_TABLE = "Cross Reference Table";
    public final static String SHADING = "Shading Info*";
    public final static String PATTERN = "Pattern Info*";
    public final static String WEB_CAPTURE = "Web Capture Info";
    public final static String PIECE_INFO = "Piece Info";

    /**
     * Method to audit PDF.  Calculates size of assorted aspects of the file
     * and returns them in a HashMap identifying those aspects and what size 
     * each is in bytes.
     * 
     * @param pdfIn PDFDocument 
     * @return HashMap<String, Long>
     */
    public HashMap<String, Long> auditPDF(Document pdfIn,long docSize) {
        HashMap<String, Long> toReturn = initializeMap(docSize);

        ObjectEvaluator objEval = new ObjectEvaluator(toReturn);

        pdfIn.enumIndirectPDFObjects(objEval);

        objEval.determineXrefSize();

        PDFDict root =pdfIn.getRoot();

        if(root.contains("PieceInfo"))
            objEval.processPieceInfo(root);

        if(root.contains("SpiderInfo"))
            objEval.processSpiderInfo(root);

        int numPages= pdfIn.getNumPages();
        for(int i=numPages-1;i>=0;--i)
        {
            Page pg = pdfIn.getPage(i);
            objEval.processPageDict(pg.getPDFDict());
        }
        if(root.contains("Names"))
            objEval.processNameTrees((PDFDict)(root.get("Names")));

        if(root.contains("Outlines"))
            objEval.processBookMarks(pdfIn.getBookmarkRoot());

        if(root.contains("AcroForm"))
            objEval.processAcroForm((PDFDict)root.get("AcroForm"));

        if(root.contains("StructTreeRoot"))
            objEval.processStructTree((PDFDict)root.get("StructTreeRoot"));

        final String[] cats = {XREF_TABLE, /*DOC_OVERHEAD, FILE_SIZE */ OBJECT_COMPRESSION, CONTENT_STREAMS,
                XOBJECT_FORMS, FONTS, IMAGES, COLOR_SPACES, EXT_GRAPHIC_STATES, PATTERN, SHADING, STRUCTURE_INFO,
                BOOKMARKS, NAMED_DESTINATIONS, LINK_ANNOTATIONS, ACRO_FORMS, COMMENTS_AND_3D, PIECE_INFO, THUMBNAILS,
                WEB_CAPTURE, EMBEDDED_FILES
        };

        //lump everything not accounted for into overhead.
        long accounted=0;
        for (final String cat : cats)
                accounted += toReturn.get(cat);
        toReturn.put(DOC_OVERHEAD,docSize-accounted);

        return toReturn;
    }

    /**
     * Helper Method to initialize our categories in the 
     * returned HashMap to 0
     * 
     * @return HashMap<String, Long>
     */
    private HashMap<String, Long> initializeMap(Long filesize) {
        HashMap<String, Long> toReturn = new HashMap<>();
        toReturn.put(FILE_SIZE, filesize);
        toReturn.put(ACRO_FORMS, (long) 0);
        toReturn.put(BOOKMARKS, (long) 0);
        toReturn.put(COLOR_SPACES, (long) 0);
        toReturn.put(COMMENTS_AND_3D, (long) 0);
        toReturn.put(SHADING, (long) 0);
        toReturn.put(PATTERN, (long) 0);
        toReturn.put(CONTENT_STREAMS, (long) 0);
        toReturn.put(DOC_OVERHEAD, (long) 0);
        toReturn.put(EXT_GRAPHIC_STATES, (long) 0);
        toReturn.put(FONTS, (long) 0);
        toReturn.put(IMAGES, (long) 0);
        toReturn.put(LINK_ANNOTATIONS, (long) 0);
        toReturn.put(NAMED_DESTINATIONS, (long) 0);
        toReturn.put(THUMBNAILS, (long) 0);
        toReturn.put(STRUCTURE_INFO, (long) 0);
        toReturn.put(XOBJECT_FORMS, (long) 0);
        toReturn.put(PIECE_INFO, (long) 0);
        toReturn.put(XREF_TABLE, (long) 0);
        toReturn.put(WEB_CAPTURE, (long) 0);
        toReturn.put(OBJECT_COMPRESSION, (long) 0);
        toReturn.put(EMBEDDED_FILES, (long) 0);

        return toReturn;
    }
}