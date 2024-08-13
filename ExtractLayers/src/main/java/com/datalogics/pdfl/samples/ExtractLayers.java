package com.datalogics.pdfl.samples;

import com.datalogics.PDFL.*;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

/*
 *  This sample demonstrates how to extract the individual layers from a layered document.
 *
 *
 * The results are exported to a PDF output document.
 * 
 *
 * Copyright (c) 2007-2024, Datalogics, Inc. All rights reserved.
 *
 */


public class ExtractLayers
{
    private  static HashSet<String> findLayerNames(OptionalContentOrderArray ocoa)
    {
        HashSet<String> layerNames = new HashSet<>();
        int arraylen = ocoa.getLength();
        for(int i =0; i < arraylen;i++)
        {
            OptionalContentOrderNode node = ocoa.get(i);
            if(node == null)
                continue;

            if (node instanceof OptionalContentOrderArray )
                layerNames.addAll(findLayerNames((OptionalContentOrderArray)node));
            else
                layerNames.add(((OptionalContentOrderLeaf)node).getOptionalContentGroup().getName());
        }

        return layerNames;
    }

    /**
     * @param args Command-line arguments; an Optional path to an input PDF.
     */
    public static void main(String[] args) {
        System.out.println("ExtractLayers sample:");

        String sInput = Library.getResourceDirectory() + "Sample_Input/Layers.pdf";

        Library lib = new Library();

        try {
            if (args.length != 0)
                sInput = args[0];
            System.out.println("Input file: " + sInput);
            Document doc = new Document(sInput);
            HashSet<String> layerNames = new HashSet<>();
            //Step 1. Get the Layer Names from the Optional Content Configuration(s)
            List<OptionalContentConfig> cfgs = doc.getOptionalContentConfigs();
            for (OptionalContentConfig cfg : cfgs) {
                layerNames.addAll(findLayerNames(cfg.getOrder()));
            }
            doc.close();

            //step 2. for every layer name we found, extract from every page all Forms and Containers associated with this layer name.
            //NOTE: This assumes that all Optional Content Groups are at the top-level of the page's rather than nested within other Forms and Containers
            // this assumption is not likely to hold with all real-world documents.
            //NOTE: Annotations can also be part of Optional Content groups, but this version does not attempt to remove any annotations that are not
            //part of the current layer.
            for (String layer : layerNames) {
                doc = new Document(sInput);
                for (int i = 0; i < doc.getNumPages(); i++) {
                    Page pg = doc.getPage(i);
                    Content pgContent = pg.getContent();
                    for (int j = pgContent.getNumElements() - 1; j > -1; j--) {
                        boolean foundCurLayer = false;
                        Element elem = pgContent.getElement(j);
                        if (elem instanceof Form) {
                            Form curForm = (Form) elem;
                            OptionalContentMembershipDict md = curForm.getOptionalContentMembershipDict();
                            if (md != null) {
                                List<OptionalContentGroup> ocgs = md.getOptionalContentGroups();

                                for (OptionalContentGroup ocg : ocgs) {
                                    if (ocg.getName().equals(layer))
                                        foundCurLayer = true;
                                }
                            }
                            if (!foundCurLayer)
                                pgContent.removeElement(j);
                        } else if (elem instanceof Container) {
                            Container curMC = (Container) elem;
                            OptionalContentMembershipDict md = curMC.getOptionalContentMembershipDict();
                            if (md != null) {
                                List<OptionalContentGroup> ocgs = md.getOptionalContentGroups();

                                for (OptionalContentGroup ocg : ocgs) {
                                    if (ocg.getName().equals(layer))
                                        foundCurLayer = true;
                                }
                            }
                            if (!foundCurLayer)
                                pgContent.removeElement(j);
                        } else {
                            pgContent.removeElement(j);
                        }
                    }
                    pg.updateContent();
                }
                doc.save(EnumSet.of(SaveFlags.FULL), sInput.replace(".pdf", "_" + layer + ".pdf"));
                doc.close();
            }
        }
        finally {
            lib.delete();
        }
    }
}
