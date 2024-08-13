package com.datalogics.pdfl.samples;

import com.datalogics.PDFL.*;

/*
 *  * This sample demonstrates walking through the field dictionaries of an Acroform PDF.
 *
 *
 * The results are exported to a PDF output document.
 * 
 *
 * Copyright (c) 2007-2023, Datalogics, Inc. All rights reserved.
 *
 */

public class FormWalker {

	static String[] Get_button_info(PDFDict field, boolean[] fieldFlags) {
		String svalue = "";
		String sdefault_value = "";
		String current_state = "";

		if (fieldFlags[17])
			return new String[]{"Pushbutton"}; //pushbutton is a purely interactive control that responds immediately to user input without retaining a permanent value.

		String buttonType = fieldFlags[16] ? "Radiobutton" : "Checkbox";

		if (field.contains("V")) {
			PDFObject entry = field.get("V");
			if (entry instanceof PDFName)
				svalue = "Value: " + ((PDFName) entry).getValue();
		}
		if (field.contains("DV")) {
			PDFObject entry = field.get("DV");
			if (entry instanceof PDFName)
				sdefault_value = "Default Value: " + ((PDFName) entry).getValue();
		}

		if (field.contains("AS")) {
			PDFObject entry = field.get("AS");
			if (entry instanceof PDFName)
				current_state = "Current State: " + ((PDFName) entry).getValue();
		}

		return new String[]{buttonType, svalue, sdefault_value, current_state};
	}

	static String[] Get_text_info(PDFDict field) {
		String svalue;
		String sdefault_value;
		String sMax_Length;
		PDFObject entry;

		entry = field.get("V");
		if (entry instanceof PDFString) {
			PDFString val = (PDFString) entry;
			svalue = "Value: " + val.getValue();
		} else
			svalue = "";

		entry = field.get("DV");
		if (entry instanceof PDFString) {
			PDFString defvalue = (PDFString) entry;
			sdefault_value = "Default Value: " + defvalue.getValue();
		} else
			sdefault_value = "";

		entry = field.get("MaxLen");
		if (entry instanceof PDFInteger) {
			PDFInteger int_entry = (PDFInteger) entry;
			int nMax_Length = int_entry.getValue();
			sMax_Length = String.format("Max Length: %d", nMax_Length);
		} else
			sMax_Length = "";

		return new String[]{svalue, sdefault_value, sMax_Length};
	}

	static String[] Get_choice_info(PDFObject field, boolean[] fieldFlags)
	{
		String choiceType = fieldFlags[18] ? "Combobox" : "Listbox";

		return new String[] { choiceType, };
	}

	static String[] Get_sigDict_info(PDFDict sigDict)
	{
		String filterName;
		String subFilterName;
		String signerName;
		String contactInfo;
		String location;
		String reason;
		String sigTime;
		String signatureType;

		filterName = ((PDFName)(sigDict.get("Filter"))).getValue();

		// In PDF32000_2008.pdf, see table 252 in section 12.8.1
		// or From Table 8.102 in section 8.7 The following are optional (and by no means a complete listing of the entries in this dictionary).
		signerName = (sigDict.contains("Name") ? ((PDFString)(sigDict.get("Name"))).getValue() : "");
		subFilterName = (sigDict.contains("SubFilter") ? ((PDFName)sigDict.get("SubFilter")).getValue() : "");
		reason = (sigDict.contains("Reason") ? ((PDFString)(sigDict.get("Reason"))).getValue() : "");
		contactInfo = (sigDict.contains("ContactInfo") ? ((PDFString)(sigDict.get("ContactInfo"))).getValue() : "");
		sigTime = (sigDict.contains("M") ? ((PDFString)sigDict.get("M")).getValue() : "");
		location = (sigDict.contains("Location") ? ((PDFString)sigDict.get("Location")).getValue() : "");
		signatureType = (sigDict.contains("Type") && ((PDFName)sigDict.get("Type")).getValue().equals("DocTimeStamp")) ? "DocTimeStamp" : "";
		return new String[] { filterName, subFilterName, signatureType, sigTime, signerName, contactInfo, location, reason };
	}

	static String[] Get_sig_info(PDFDict field)
	{

		PDFObject entry = field.get("V");
		if (entry instanceof PDFDict)
		{
			return Get_sigDict_info((PDFDict)entry);
		}

		return new String[] { "Unsigned", };
	}

	static void Enumerate_field(PDFObject field_entry, String prefix) {
		String name_part = "";  //optional
		String field_name;

		if (field_entry instanceof PDFDict) {
			PDFDict field = (PDFDict) field_entry;
			if (field.contains("T")) {
				PDFObject entry = field.get("T");
				if (entry instanceof PDFString) {
					name_part = ((PDFString) entry).getValue();
				}
			}

			if (prefix.isEmpty())
				field_name = name_part;
			else
				field_name = String.format("%s.%s", prefix, name_part);

			if (field.contains("Kids")) {
				PDFObject entry = field.get("Kids");
				if (entry instanceof PDFArray) {
					PDFArray kids = (PDFArray) entry;
					for (int i = 0; i < kids.getLength(); i++) {
						PDFObject kid_entry = kids.get(i);
						Enumerate_field(kid_entry, field_name);
					}
				}
			} else //no kids, so we are at an end-node.
			{
				String alternate_name = null;
				String mapping_name = null;
				String field_type;
				String[] field_info = new String[0];
				boolean additional_actions = false;
				boolean javascript_formatting = false;
				boolean javascript_calculation = false;
				boolean javascript_validation = false;
				int field_flags = 0;

				System.out.println("Name: " + field_name);

				if (field.contains("Ff")) {
					PDFObject entry = field.get("Ff");
					if (entry instanceof PDFInteger) {
						field_flags = ((PDFInteger)entry).getValue();
					}
				}
				boolean[] flags = new boolean[28];
				for (int bitpos = 1; bitpos < flags.length; bitpos++) {
					flags[bitpos] = (0 != (field_flags & (0x1 << bitpos - 1)));
				}

				{
					PDFObject entry = field.get("FT");
					if (entry instanceof PDFName) {
						switch (((PDFName) entry).getValue()) {
							case "Btn":
								field_type = "Button";
								field_info = Get_button_info(field, flags);
								break;
							case "Tx":
								field_type = "Text";
								field_info = Get_text_info(field);
								break;
							case "Ch":
								field_type = "Choice";
								field_info = Get_choice_info(field, flags);
								break;
							case "Sig":
								field_type = "Signature";
								field_info = Get_sig_info(field);
								break;
							default:
								field_type = ((PDFName) entry).getValue();
								return;
						}
					} else {
						field_type = "inherited?"; //This entry may be present in a non-terminal field (one whose descendants are fields) to provide an inheritable FT value. However, a non-terminal field does not logically have a type of its own; it is merely a container for inheritable attributes that are intended for descendant terminal fields of any type.
						//field_info ={};
					}
				}

				if (field.contains("TU")) {
					PDFObject entry = field.get("TU");
					if (entry instanceof PDFString) {
						alternate_name = ((PDFString) entry).getValue();
					}
				}

				if (field.contains("TM")) {
					PDFObject entry = field.get("TM");
					if (entry instanceof PDFString) {
						mapping_name = ((PDFString) entry).getValue();
					}
				}

				if (field.contains("AA")) {
					PDFObject entry = field.get("AA");
					if (entry instanceof PDFDict) {
						additional_actions = true;
						PDFDict aadict = (PDFDict) entry;
						javascript_formatting = aadict.contains("F");
						javascript_calculation = aadict.contains("C");
						javascript_validation = aadict.contains("V");
					}
				}

				if (alternate_name != null)
					System.out.println("Alternate Name: " + alternate_name);
				if (mapping_name != null)
					System.out.println("Mapping Name: " + mapping_name);
				if (additional_actions)
					System.out.printf("Additional Actions: Javascript %s%s%s.%n",
							javascript_validation ? "Validation, " : "",
							javascript_calculation ? "Calculation, " : "",
							javascript_formatting ? "Formatting" : "");

				System.out.println("Type: " + field_type);

				if (field_flags != 0) {
					if (field_type.equals("Signature"))
						System.out.printf("Signature Flags: %08X: requires %s%s%s%s%s%s%s%n", field_flags,
								flags[1] ? "Filter, " : "",
								flags[2] ? "SubFilter, " : "",
								flags[3] ? "V, " : "",
								flags[4] ? "Reason, " : "",
								flags[5] ? "LegalAttestation, " : "",
								flags[6] ? "AddRevInfo, " : "",
								flags[7] ? "DigestMethod" : ""
						);
					else
						System.out.printf("Format Flags: %08X: %s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%n", field_flags,
								flags[1] ? "ReadOnly " : "",
								flags[2] ? "Required " : "",
								flags[3] ? "NoExport " : "",
								flags[13] ? "MultiLine " : "",
								flags[14] ? "Password " : "",
								flags[15] ? "NoToggleToOff " : "",
								flags[16] ? "Radio " : "",
								flags[17] ? "PushButton " : "",
								flags[18] ? "Combo " : "",
								flags[19] ? "Edit " : "",
								flags[20] ? "Sort " : "",
								flags[21] ? "FileSelect " : "",
								flags[22] ? "MultiSelect " : "",
								flags[23] ? "DoNotSpellCheck " : "",
								flags[24] ? "DoNotScroll " : "",
								flags[25] ? "Comb " : "",
								flags[26] ? (field_type.equals("Text") ? "RichText" : (field_type.equals("Button") ? "RadiosInUnison" : "?")) : "",
								flags[27] ? "CommitOnSelChange " : ""
						);
				}

				for (String item : field_info) {
					if (!item.isEmpty())
						System.out.println("\t" + item);
				}
				System.out.println();
			}
		}
	}


	static void DisplayRootDictionary(PDFDict formsRoot) {
		PDFObject entry;
		boolean bNeedAppearances = false;
		int nSigFlags = 0;
		boolean bCalcOrder = false;
		boolean bDefaultResource = false;
		boolean bDefaultAppearance = false;
		boolean bXFAForms = false;
		int QuadMode = -1;
		String sQuadMode = "unkown";

		entry = formsRoot.get("NeedAppearances");
		if (entry instanceof PDFBoolean) {
			bNeedAppearances = ((PDFBoolean) entry).getValue();
		}

		System.out.println("NeedAppearances: " + (bNeedAppearances ? "True" : "False"));

		entry = formsRoot.get("SigFlags");
		if (entry instanceof PDFInteger) {
			nSigFlags = ((PDFInteger) entry).getValue();
		}

		if (nSigFlags == 0)
			System.out.println("Document has no signatures.");
		else {
			if ((nSigFlags & 1) == 1)
				System.out.println("Document has signatures.");
			if ((nSigFlags & 2) == 2)
				System.out.println("Signatures: Document may append only.");
		}

		entry = formsRoot.get("CO");
		if (entry instanceof PDFDict)
			bCalcOrder = true;
		System.out.printf("Calculation Order Dictionary is %spresent.%n", (bCalcOrder ? "" : "not "));

		entry = formsRoot.get("DR");
		if (entry instanceof PDFDict)
			bDefaultResource = true;
		System.out.printf("Default Resource Dictionary is %spresent.%n", (bDefaultResource ? "" : "not "));

		entry = formsRoot.get("DA");
		if (entry instanceof PDFString)
			bDefaultAppearance = true;
		System.out.printf("Default Appearance String is %spresent.%n", (bDefaultAppearance ? "" : "not "));

		entry = formsRoot.get("Q");
		if (entry instanceof PDFInteger) {
			QuadMode = ((PDFInteger) entry).getValue();
		}
		switch (QuadMode) {
			case -1:
				sQuadMode = "not present";
				break;
			case 0:
				sQuadMode = "Left";
				break;
			case 1:
				sQuadMode = "Centered";
				break;
			case 2:
				sQuadMode = "Right";
				break;
		}
		System.out.printf("Default Quad Mode is %s.%n", sQuadMode);

		entry = formsRoot.get("XFA");
		if (entry instanceof PDFString)
			bXFAForms = true;
		System.out.printf("XFA Forms are %spresent.%n", (bXFAForms ? "" : "not "));
		System.out.println();
	}

	static void DisplayPermSignatures(PDFDict docPerms)
	{
		// From section 8.7.3's TABLE 8.107 Entries in a permissions dictionary
		if (docPerms.contains("DocMDP"))
		{
			System.out.println("Document has an Author signature:");
			for(String item : Get_sigDict_info((PDFDict)docPerms.get("DocMDP")))
			{
				if (!item.isEmpty())
					System.out.println(item);
			}
			System.out.println();
		}
		if (docPerms.contains("UR3"))
		{
			System.out.println("Document has a Usage Rights signature:");
			for(String item : Get_sigDict_info((PDFDict)docPerms.get("UR3")))
			{
				if (!item.isEmpty())
					System.out.println(item);
			}
			System.out.println();
		}
		if (docPerms.contains("UR"))
		{
			System.out.println("Document has an old-style Usage Rights signature:");
			for(String item : Get_sigDict_info((PDFDict)docPerms.get("UR")))
			{
				if (!item.isEmpty())
					System.out.println(item);
			}
			System.out.println();
		}
	}


	public static void main (String[] args) throws Throwable {
		System.out.println("FormWalker sample:");

		Library lib = new Library();

		try {
			String sInput = Library.getResourceDirectory() + "Sample_Input/AcroForm.pdf";
			if (args.length > 0)
				sInput = args[0];

			Document doc = new Document(sInput);
			System.out.printf("Opened document %s%n", sInput);

			if (doc.getHasSignature())
				System.out.println("Document has a Digital Signature.");

			if (doc.getRoot().contains("Perms"))
				DisplayPermSignatures((PDFDict)doc.getRoot().get("Perms"));

			PDFObject form_entry = doc.getRoot().get("AcroForm");
			if (form_entry instanceof PDFDict)
			{
				PDFDict form_root = (PDFDict)form_entry;
				DisplayRootDictionary(form_root);

				if (form_root.contains("Fields")) {
					PDFObject fields_entry = form_root.get("Fields");
					if (fields_entry instanceof PDFArray) {
						PDFArray fields = (PDFArray) fields_entry;
						for (int i = 0; i < fields.getLength(); i++) {
							PDFObject field_entry = fields.get(i);
							Enumerate_field(field_entry, "");
						}
					}
				}
			}
			System.out.println("Done.");

		} finally {
			lib.delete();
		}
	}
}
