/*
 * AuditInfo
 *
 * Copyright (c) 2024 Datalogics, Inc. All rights reserved.
 *
 * The information and code in this sample is for the exclusive use of Datalogics customers and evaluation users only.
 * Datalogics permits you to use, modify and distribute this file in accordance with the terms of your license
 * agreement. Sample code is for demonstrative purposes only and is not intended for production use.
 *
 */

package com.datalogics.pdfl.samples;

import com.datalogics.PDFL.Document;
import com.datalogics.PDFL.Library;
import com.datalogics.PDFL.LibraryException;

import java.io.*;
import java.util.HashMap;


class AuditInfo {

    public static void process(String input_file, String userPwd, String ownerPwd) {
        Document pdfDocument = null;

        try {
            File inFile= new File(input_file);
            long filesize = inFile.length();
            pdfDocument = new Document(input_file);

            // Audit the file
            final PDFAuditor auditor = new PDFAuditor();
            final HashMap<String, Long> auditInfo = auditor.auditPDF(pdfDocument,filesize);

            System.out.println(input_file +" file size:"+filesize);

            final String[] cats = {
                    PDFAuditor.IMAGES,PDFAuditor.CONTENT_STREAMS, PDFAuditor.XOBJECT_FORMS, PDFAuditor.FONTS,
                    PDFAuditor.COLOR_SPACES, PDFAuditor.EXT_GRAPHIC_STATES, PDFAuditor.PATTERN, PDFAuditor.SHADING,
                     PDFAuditor.LINK_ANNOTATIONS,
                    PDFAuditor.COMMENTS_AND_3D,PDFAuditor.ACRO_FORMS,
                    PDFAuditor.PIECE_INFO,
                    PDFAuditor.THUMBNAILS, PDFAuditor.WEB_CAPTURE,
                    PDFAuditor.STRUCTURE_INFO,
                    PDFAuditor.BOOKMARKS, PDFAuditor.NAMED_DESTINATIONS,
                    PDFAuditor.DOC_OVERHEAD, PDFAuditor.OBJECT_COMPRESSION,
                    PDFAuditor.XREF_TABLE,
                    PDFAuditor.EMBEDDED_FILES,
                    PDFAuditor.FILE_SIZE};

            for (final String cat : cats) {
                if (auditInfo.containsKey(cat)) {
                    final long val = auditInfo.get(cat);
                    if(val > 0)
                        System.out.println(String.format("%36s:\t%,8d\t%8.2f%%",cat,val,(val * 100.0 / filesize)));
                }
            }
        } catch (LibraryException e) {
                            e.printStackTrace();
                        } catch (final Exception ex) {
                try (StringWriter sw = new StringWriter();
                     PrintWriter pw = new PrintWriter(sw)) {
                    ex.printStackTrace(pw);
                } catch (IOException ioe) {
                    System.out.println("error: " + input_file + ": "
                            + ex.getClass().getSimpleName());
                    throw new IllegalStateException(ioe);
                }
            } finally {
                if (pdfDocument != null) {
                    try {
                        pdfDocument.close();
                    } catch (final Exception ignored) {
                    }
                }
            }
    }


    /**
     * @param args command-line parameters.
     */
    public static void main(final String[] args) {
        System.out.println("AuditInfo sample:");
        Library lib = new Library();
        int i = 0;
        String ownerPwd= null;
        String userPwd = null;
        String input_file=null;
        int numArgs = args.length;
        while (i < numArgs-1)
        {
            if(args[i].equals("-o"))
                ownerPwd = args[++i];
            else if (args[i].equals("-u") || args[i].equals(("-p")))
                userPwd = args[++i];
            else
                break;
            ++i;
        }
        if(i < args.length)
            input_file = args[i];

        if(args.length >0)
            process(input_file,userPwd,ownerPwd);
    }
}