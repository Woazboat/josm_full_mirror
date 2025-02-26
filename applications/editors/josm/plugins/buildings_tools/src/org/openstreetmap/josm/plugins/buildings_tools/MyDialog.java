// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.buildings_tools;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.GBC;

/**
 * A customized dialog for buildings tools
 */
public abstract class MyDialog extends ExtendedDialog {
    private static final String[] BUTTON_TEXTS = new String[] {tr("OK"), tr("Cancel")};
    private static final String[] BUTTON_ICONS = new String[] {"ok", "cancel"};

    protected final JPanel panel = new JPanel(new GridBagLayout());

    protected final void addLabelled(String str, Component c) {
        JLabel label = new JLabel(str);
        panel.add(label, GBC.std());
        label.setLabelFor(c);
        panel.add(c, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
    }

    /**
     * Create a new dialog
     * @param title The title for the dialog
     */
    protected MyDialog(String title) {
        super(MainApplication.getMainFrame(), title, BUTTON_TEXTS, true);
        contentInsets = new Insets(15, 15, 5, 15);
        setButtonIcons(BUTTON_ICONS);

        setContent(panel);
        setDefaultButton(1);
    }
}
