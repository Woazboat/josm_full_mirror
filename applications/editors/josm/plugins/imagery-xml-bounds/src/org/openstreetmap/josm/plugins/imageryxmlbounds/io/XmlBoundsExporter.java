// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.imageryxmlbounds.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.openstreetmap.josm.gui.io.importexport.FileExporter;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.imageryxmlbounds.XmlBoundsConstants;
import org.openstreetmap.josm.plugins.imageryxmlbounds.actions.ComputeBoundsAction;

/**
 * XML bounds exporter.
 * @author Don-vip
 */
public class XmlBoundsExporter extends FileExporter implements XmlBoundsConstants {

    /**
     * Constructs a new {@code XmlBoundsExporter}.
     */
    public XmlBoundsExporter() {
        super(FILE_FILTER);
    }

    @Override
    public void exportData(File file, Layer layer) throws IOException {
        if (layer instanceof OsmDataLayer) {
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), ENCODING))) {
                writer.write(new ComputeBoundsAction((OsmDataLayer) layer).getXml());
            }
        }
    }
}
