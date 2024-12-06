package com.datalogics.pdfl.samples;

import com.datalogics.PDFL.*;
import java.util.List;
import java.awt.Font;

/*
 * 
 * A sample which demonstrates how to list
 * the fonts used in a document.
 * 
 * Copyright (c) 2007-2024, Datalogics, Inc. All rights reserved.
 *
 */
public class ListDocFonts {
	public static void main(String[] args) throws Throwable {
		Library lib = new Library();

		System.out.println("Initialized the library.");

		String sInput = "../../Resources/Sample_Input/sample.pdf";

		if (args.length > 0)
			sInput = args[0];

		System.out.println("Input file: " + sInput);

		Document doc = new Document(sInput);

		try {
			List<com.datalogics.PDFL.Font> docFonts = doc.getFonts(0, doc.getNumPages() - 1);

			for (com.datalogics.PDFL.Font font : docFonts) {
				System.out.println(font.getName());
				System.out.println("\t"+font.getFullName());
				System.out.println("\t"+font.getType());
				System.out.println("\tFont is" + (font.getEmbedded() ? "" : " not") + " embedded.");

				System.out.println("\t"+font.getEncoding());

				if (font.getType().equals("Type0") && font.getEmbedded()) {
					PDFArray descFontsArray = (PDFArray) font.getPDFDict().get("DescendantFonts");
					PDFDict descFont = (PDFDict) descFontsArray.get(0);
					PDFDict fontDescriptor = (PDFDict) descFont.get("FontDescriptor");
					if (fontDescriptor.contains("FontFile2")) {
						PDFStream fontStream = (PDFStream) fontDescriptor.get("FontFile2");

						java.awt.Font f = Font.createFont(Font.TRUETYPE_FONT, fontStream.getFilteredStream());

						System.out.println("\t\t"+f.getName());
						System.out.println("\t\t"+f.getFontName());
						System.out.println("\t\t"+f.getFamily());
						System.out.println("\t\t"+f.getPSName());
					}
				}
			}
		} finally {
			lib.delete();
		}
	}
};
