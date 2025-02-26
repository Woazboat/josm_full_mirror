// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.fr.cadastre.actions;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.fr.cadastre.wms.DownloadSVGTask;
import org.openstreetmap.josm.plugins.fr.cadastre.wms.WMSLayer;

/**
 * Extract commune boundary
 */
public class MenuActionBoundaries extends JosmAction {

    private static final String NAME = marktr("Administrative boundary");

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@code MenuActionBoundaries}.
     */
    public MenuActionBoundaries() {
        super(tr(NAME), "cadastre_small", tr("Extract commune boundary"), null, false);
    }

    @Override
    public void actionPerformed(ActionEvent arg0) {
        WMSLayer wmsLayer = WMSDownloadAction.getLayer();
        if (wmsLayer != null) {
            if (wmsLayer.isRaster()) {
                JOptionPane.showMessageDialog(MainApplication.getMainFrame(),
                        tr("Only on vectorized layers"), tr("Error"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            DownloadSVGTask.download(wmsLayer);
        }
    }
}
