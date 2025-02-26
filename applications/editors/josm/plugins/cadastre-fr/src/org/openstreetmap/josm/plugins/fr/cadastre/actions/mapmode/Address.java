// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.fr.cadastre.actions.mapmode;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IWaySegment;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Shortcut;

public class Address extends MapMode {

    private static final String MESSAGE_UNABLE_TO_PARSE_HOUSE_NUMBER = "Unable to parse house number \"{0}\"";

    // perhaps make all these tags configurable in the future
    private static final String TAG_HIGHWAY = "highway";
    private static final String TAG_HIGHWAY_NAME = "name";
    private static final String TAG_HOUSE_NUMBER = "addr:housenumber";
    private static final String TAG_HOUSE_STREET = "addr:street";
    private static final String TAG_BUILDING = "building";
    private static final String RELATION_ADDR_TYPE = "associatedStreet";
    private static final String RELATION_ADDR_NAME = "name";
    private static final String RELATION_ADDR_STREET_ROLE = "street";
    private static final String RELATION_MEMBER_HOUSE = "house";

    private JRadioButton plusOne = new JRadioButton("+1", false);
    private JRadioButton plusTwo = new JRadioButton("+2", true); // enable this by default
    private JRadioButton minusOne = new JRadioButton("-1", false);
    private JRadioButton minusTwo = new JRadioButton("-2", false);
    final JCheckBox tagPolygon = new JCheckBox(tr("on polygon"));

    JDialog dialog;
    JButton clearButton;
    final JTextField inputNumber = new JTextField();
    final JTextField inputStreet = new JTextField();
    JLabel link = new JLabel();
    private transient Way selectedWay;

    /**
     * Constructs a new {@code Address} map mode.
     */
    public Address() {
        super(tr("Add address"), "buildings",
                tr("Helping tool for tag address"),
                // CHECKSTYLE.OFF: LineLength
                Shortcut.registerShortcut("mapmode:cadastre-fr-buildings", tr("Mode: {0}", tr("CadastreFR - Buildings")), KeyEvent.VK_E, Shortcut.DIRECT),
                // CHECKSTYLE.ON: LineLength
                getCursor());
    }

    @Override public void enterMode() {
        super.enterMode();
        if (dialog == null) {
            createDialog();
        }
        dialog.setVisible(true);
        MainApplication.getMap().mapView.addMouseListener(this);
    }

    @Override public void exitMode() {
        if (MainApplication.getMap().mapView != null) {
            super.exitMode();
            MainApplication.getMap().mapView.removeMouseListener(this);
        }
        // kill the window completely to fix an issue on some linux distro and full screen mode.
        if (dialog != null) {
            dialog.dispose();
            dialog = null;
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1)
            return;
        updateKeyModifiers(e);
        MapView mv = MainApplication.getMap().mapView;
        Point mousePos = e.getPoint();
        Node currentMouseNode = mv.getNearestNode(mousePos, OsmPrimitive::isSelectable);
        if (currentMouseNode != null) {
            mousePressedExistingNode(currentMouseNode);
        } else {
            mousePressedNoExistingNode(e, mousePos, mv);
        }
    }

    private void mousePressedExistingNode(Node currentMouseNode) {
        // click on existing node
        setNewSelection(currentMouseNode);
        String num = currentMouseNode.get(TAG_HOUSE_NUMBER);
        if (num != null
                && currentMouseNode.get(TAG_HOUSE_STREET) == null
                && findWayInRelationAddr(currentMouseNode) == null
                && !inputStreet.getText().isEmpty()) {
            // house number already present but not linked to a street
            Collection<Command> cmds = new LinkedList<>();
            addStreetNameOrRelation(currentMouseNode, cmds);
            Command c = new SequenceCommand("Add node address", cmds);
            UndoRedoHandler.getInstance().add(c);
            setNewSelection(currentMouseNode);
        } else {
            if (num != null) {
                try {
                    // add new address
                    Integer.parseInt(num);
                    inputNumber.setText(num);
                    applyInputNumberChange();
                } catch (NumberFormatException ex) {
                    Logging.warn(MESSAGE_UNABLE_TO_PARSE_HOUSE_NUMBER, num);
                }
            }
            mousePressedExistingNode(currentMouseNode, num);
        }
    }

    private void mousePressedExistingNode(Node currentMouseNode, String num) {
        final boolean dontUseRelation = Config.getPref().getBoolean("cadastrewms.addr.dontUseRelation", false);
        final String houseStreet = currentMouseNode.get(TAG_HOUSE_STREET);
        if (houseStreet != null && dontUseRelation) {
            inputStreet.setText(currentMouseNode.get(TAG_HOUSE_STREET));
            if (ctrl) {
                Collection<Command> cmds = new LinkedList<>();
                addAddrToPrimitive(currentMouseNode, cmds);
                if (num == null)
                    applyInputNumberChange();
            }
            setSelectedWay(null);
        } else if (houseStreet == null){
            // check if the node belongs to an associatedStreet relation
            Way wayInRelationAddr = findWayInRelationAddr(currentMouseNode);
            if (wayInRelationAddr == null) {
                // node exists but doesn't carry address information : add tags like a new node
                if (ctrl) {
                    applyInputNumberChange();
                }
                Collection<Command> cmds = new LinkedList<>();
                addAddrToPrimitive(currentMouseNode, cmds);
            } else {
                inputStreet.setText(wayInRelationAddr.get(TAG_HIGHWAY_NAME));
                setSelectedWay(wayInRelationAddr);
            }
        }
    }

    private void mousePressedNoExistingNode(MouseEvent e, Point mousePos, MapView mv) {
        List<Way> mouseOnExistingWays = new ArrayList<>();
        List<Way> mouseOnExistingBuildingWays = new ArrayList<>();
        List<WaySegment> wss = mv.getNearestWaySegments(mousePos, OsmPrimitive::isSelectable);
        for (IWaySegment<Node, Way> ws : wss) {
            if (ws.getWay().get(TAG_HIGHWAY) != null && ws.getWay().get(TAG_HIGHWAY_NAME) != null)
                mouseOnExistingWays.add(ws.getWay());
            else if (ws.getWay().get(TAG_BUILDING) != null && ws.getWay().get(TAG_HOUSE_NUMBER) == null)
                mouseOnExistingBuildingWays.add(ws.getWay());
        }
        if (mouseOnExistingWays.size() == 1) {
            // clicked on existing highway => set new street name
            inputStreet.setText(mouseOnExistingWays.get(0).get(TAG_HIGHWAY_NAME));
            setSelectedWay(mouseOnExistingWays.get(0));
            inputNumber.setText("");
            setNewSelection(mouseOnExistingWays.get(0));
        } else if (mouseOnExistingWays.isEmpty()) {
            mousePressedNoExistingNodeNoWays(e, mouseOnExistingBuildingWays);
        }
    }

    private void mousePressedNoExistingNodeNoWays(MouseEvent e, List<Way> mouseOnExistingBuildingWays) {
        // clicked a non highway and not a node => add the new address
        if (inputStreet.getText().isEmpty() || inputNumber.getText().isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
        } else {
            Collection<Command> cmds = new LinkedList<>();
            if (ctrl) {
                applyInputNumberChange();
            }
            if (tagPolygon.isSelected()) {
                addAddrToPolygon(mouseOnExistingBuildingWays, cmds);
            } else {
                Node n = createNewNode(e, cmds);
                addAddrToPrimitive(n, cmds);
            }
        }
    }

    private static Way findWayInRelationAddr(Node n) {
        List<OsmPrimitive> l = n.getReferrers();
        for (OsmPrimitive osm : l) {
            if (osm instanceof Relation && osm.hasKey("type") && osm.get("type").equals(RELATION_ADDR_TYPE)) {
                for (RelationMember rm : ((Relation) osm).getMembers()) {
                    Way w = usableRelationRole(rm);
                    if (w != null) {
                        return w;
                    }
                }
            }
        }
        return null;
    }

    private static Way usableRelationRole(RelationMember rm) {
        if (rm.getRole().equals(RELATION_ADDR_STREET_ROLE)) {
            OsmPrimitive osp = rm.getMember();
            if (osp instanceof Way && osp.hasKey(TAG_HIGHWAY_NAME)) {
                return (Way) osp;
            }
        }
        return null;
    }

    private void addAddrToPolygon(List<Way> mouseOnExistingBuildingWays, Collection<Command> cmds) {
        for (Way w:mouseOnExistingBuildingWays) {
            addAddrToPrimitive(w, cmds);
        }
    }

    private void addAddrToPrimitive(OsmPrimitive osm, Collection<Command> cmds) {
        // add the current tag addr:housenumber in node and member in relation (if so configured)
        if (shift) {
            try {
                revertInputNumberChange();
            } catch (NumberFormatException ex) {
                Logging.warn(MESSAGE_UNABLE_TO_PARSE_HOUSE_NUMBER, inputNumber.getText());
            }
        }
        Map<String, String> tags = new HashMap<>();
        tags.put(TAG_HOUSE_NUMBER, inputNumber.getText());
        cmds.add(new ChangePropertyCommand(OsmDataManager.getInstance().getEditDataSet(), Collections.singleton(osm), tags));
        addStreetNameOrRelation(osm, cmds);
        try {
            applyInputNumberChange();
            Command c = new SequenceCommand("Add node address", cmds);
            UndoRedoHandler.getInstance().add(c);
            setNewSelection(osm);
        } catch (NumberFormatException ex) {
            Logging.warn(MESSAGE_UNABLE_TO_PARSE_HOUSE_NUMBER, inputNumber.getText());
        }
    }

    private static Relation findRelationAddr(Way w) {
        List<OsmPrimitive> l = w.getReferrers();
        for (OsmPrimitive osm : l) {
            if (osm instanceof Relation && osm.hasKey("type") && osm.get("type").equals(RELATION_ADDR_TYPE)) {
                return (Relation) osm;
            }
        }
        return null;
    }

    private void addStreetNameOrRelation(OsmPrimitive osm, Collection<Command> cmds) {
        DataSet ds = OsmDataManager.getInstance().getEditDataSet();
        if (Config.getPref().getBoolean("cadastrewms.addr.dontUseRelation", false)) {
            Map<String, String> tags = new HashMap<>();
            tags.put(TAG_HOUSE_STREET, inputStreet.getText());
            cmds.add(new ChangePropertyCommand(ds, Arrays.asList(osm), tags));
        } else if (selectedWay != null) {
            Relation selectedRelation = findRelationAddr(selectedWay);
            // add the node to its relation
            if (selectedRelation != null) {
                RelationMember rm = new RelationMember(RELATION_MEMBER_HOUSE, osm);
                Relation newRel = new Relation(selectedRelation);
                newRel.addMember(rm);
                cmds.add(new ChangeCommand(selectedRelation, newRel));
            } else {
                // create new relation
                Relation newRel = new Relation();
                newRel.put("type", RELATION_ADDR_TYPE);
                newRel.put(RELATION_ADDR_NAME, selectedWay.get(TAG_HIGHWAY_NAME));
                newRel.addMember(new RelationMember(RELATION_ADDR_STREET_ROLE, selectedWay));
                newRel.addMember(new RelationMember(RELATION_MEMBER_HOUSE, osm));
                cmds.add(new AddCommand(ds, newRel));
            }
        }
    }

    private static Node createNewNode(MouseEvent e, Collection<Command> cmds) {
        // DrawAction.mouseReleased() but without key modifiers
        Node n = new Node(MainApplication.getMap().mapView.getLatLon(e.getX(), e.getY()));
        cmds.add(new AddCommand(OsmDataManager.getInstance().getEditDataSet(), n));
        List<WaySegment> wss = MainApplication.getMap().mapView.getNearestWaySegments(e.getPoint(), OsmPrimitive::isSelectable);
        Map<Way, List<Integer>> insertPoints = new HashMap<>();
        for (IWaySegment<Node, Way> ws : wss) {
            List<Integer> is;
            if (insertPoints.containsKey(ws.getWay())) {
                is = insertPoints.get(ws.getWay());
            } else {
                is = new ArrayList<>();
                insertPoints.put(ws.getWay(), is);
            }

            is.add(ws.getLowerIndex());
        }
        Set<Pair<Node, Node>> segSet = new HashSet<>();
        ArrayList<Way> replacedWays = new ArrayList<>();
        ArrayList<Way> reuseWays = new ArrayList<>();
        for (Map.Entry<Way, List<Integer>> insertPoint : insertPoints.entrySet()) {
            Way w = insertPoint.getKey();
            List<Integer> is = insertPoint.getValue();
            Way wnew = new Way(w);
            pruneSuccsAndReverse(is);
            for (int i : is) {
                segSet.add(Pair.sort(new Pair<>(w.getNode(i), w.getNode(i+1))));
            }
            for (int i : is) {
                wnew.addNode(i + 1, n);
            }
            cmds.add(new ChangeCommand(insertPoint.getKey(), wnew));
            replacedWays.add(insertPoint.getKey());
            reuseWays.add(wnew);
        }
        adjustNode(segSet, n);

        return n;
    }

    private static void adjustNode(Collection<Pair<Node, Node>> segs, Node n) {

        switch (segs.size()) {
        case 0:
            return;
        case 2:
            // This computes the intersection between
            // the two segments and adjusts the node position.
            Iterator<Pair<Node, Node>> i = segs.iterator();
            Pair<Node, Node> seg = i.next();
            EastNorth enA = seg.a.getEastNorth();
            EastNorth enB = seg.b.getEastNorth();
            seg = i.next();
            EastNorth enC = seg.a.getEastNorth();
            EastNorth enD = seg.b.getEastNorth();

            double u = det(enB.east() - enA.east(), enB.north() - enA.north(), enC.east() - enD.east(), enC.north() - enD.north());

            // Check for parallel segments and do nothing if they are
            // In practice this will probably only happen when a way has been duplicated

            if (u == 0) return;

            // q is a number between 0 and 1
            // It is the point in the segment where the intersection occurs
            // if the segment is scaled to lenght 1

            double q = det(enB.north() - enC.north(), enB.east() - enC.east(), enD.north() - enC.north(), enD.east() - enC.east()) / u;
            EastNorth intersection = new EastNorth(
                    enB.east() + q * (enA.east() - enB.east()),
                    enB.north() + q * (enA.north() - enB.north()));

            int snapToIntersectionThreshold
            = Config.getPref().getInt("edit.snap-intersection-threshold", 10);

            // only adjust to intersection if within snapToIntersectionThreshold pixel of mouse click; otherwise
            // fall through to default action.
            // (for semi-parallel lines, intersection might be miles away!)
            MapView mv = MainApplication.getMap().mapView;
            if (mv.getPoint(n).distance(mv.getPoint(intersection)) < snapToIntersectionThreshold) {
                n.setEastNorth(intersection);
                return;
            }
            // fall through
        default:
            EastNorth enP = n.getEastNorth();
            seg = segs.iterator().next();
            enA = seg.a.getEastNorth();
            enB = seg.b.getEastNorth();
            double a = enP.distanceSq(enB);
            double b = enP.distanceSq(enA);
            double c = enA.distanceSq(enB);
            q = (a - b + c) / (2*c);
            n.setEastNorth(new EastNorth(enB.east() + q * (enA.east() - enB.east()), enB.north() + q * (enA.north() - enB.north())));
        }
    }

    static double det(double a, double b, double c, double d) {
        return a * d - b * c;
    }

    private static void pruneSuccsAndReverse(List<Integer> is) {
        HashSet<Integer> is2 = new HashSet<>();
        for (int i : is) {
            if (!is2.contains(i - 1) && !is2.contains(i + 1)) {
                is2.add(i);
            }
        }
        is.clear();
        is.addAll(is2);
        Collections.sort(is);
        Collections.reverse(is);
    }

    private static Cursor getCursor() {
        try {
            return ImageProvider.getCursor("crosshair", null);
        } catch (RuntimeException e) {
            Logging.warn(e);
        }
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }

    private void applyInputNumberChange() {
        Integer num = Integer.parseInt(inputNumber.getText());
        if (plusOne.isSelected())
            num = num + 1;
        if (plusTwo.isSelected())
            num = num + 2;
        if (minusOne.isSelected() && num > 1)
            num = num - 1;
        if (minusTwo.isSelected() && num > 2)
            num = num - 2;
        inputNumber.setText(num.toString());
    }

    private void revertInputNumberChange() {
        Integer num = Integer.parseInt(inputNumber.getText());
        if (plusOne.isSelected())
            num = num - 1;
        if (plusTwo.isSelected())
            num = num - 2;
        if (minusOne.isSelected() && num > 1)
            num = num + 1;
        if (minusTwo.isSelected() && num > 2)
            num = num + 2;
        inputNumber.setText(num.toString());
    }

    private void createDialog() {
        ImageIcon iconLink = ImageProvider.get(null, "Osm_element_relation");
        link.setIcon(iconLink);
        link.setEnabled(false);
        JPanel p = new JPanel(new GridBagLayout());
        JLabel number = new JLabel(tr("Next no"));
        JLabel street = new JLabel(tr("Street"));
        p.add(number, GBC.std().insets(0, 0, 0, 0));
        p.add(inputNumber, GBC.eol().fill(GridBagConstraints.HORIZONTAL).insets(5, 5, 0, 5));
        p.add(street, GBC.std().insets(0, 0, 0, 0));
        JPanel p2 = new JPanel(new GridBagLayout());
        inputStreet.setEditable(false);
        p2.add(inputStreet, GBC.std().fill(GridBagConstraints.HORIZONTAL).insets(5, 0, 0, 0));
        p2.add(link, GBC.eol().insets(10, 0, 0, 0));
        p.add(p2, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> {
            inputNumber.setText("");
            inputStreet.setText("");
            setSelectedWay(null);
        });
        ButtonGroup bgIncremental = new ButtonGroup();
        bgIncremental.add(plusOne);
        bgIncremental.add(plusTwo);
        bgIncremental.add(minusOne);
        bgIncremental.add(minusTwo);
        p.add(minusOne, GBC.std().insets(10, 0, 10, 0));
        p.add(plusOne, GBC.std().insets(0, 0, 10, 0));
        tagPolygon.setSelected(Config.getPref().getBoolean("cadastrewms.addr.onBuilding", false));
        tagPolygon.addChangeListener(ignored -> Config.getPref().putBoolean("cadastrewms.addr.onBuilding", tagPolygon.isSelected()));
        p.add(tagPolygon, GBC.eol().fill(GridBagConstraints.HORIZONTAL).insets(0, 0, 0, 0));
        p.add(minusTwo, GBC.std().insets(10, 0, 10, 0));
        p.add(plusTwo, GBC.std().insets(0, 0, 10, 0));
        p.add(clearButton, GBC.eol().fill(GridBagConstraints.HORIZONTAL).insets(0, 0, 0, 0));

        final Object[] options = {};
        final JOptionPane pane = new JOptionPane(p,
                JOptionPane.PLAIN_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION,
                null, options, null);
        dialog = pane.createDialog(MainApplication.getMainFrame(), tr("Enter addresses"));
        dialog.setModal(false);
        dialog.setAlwaysOnTop(true);
        dialog.addComponentListener(new ComponentAdapter() {
            protected void rememberGeometry() {
                Config.getPref().put("cadastrewms.addr.bounds", dialog.getX()+","+dialog.getY()+","+dialog.getWidth()+","+dialog.getHeight());
            }

            @Override public void componentMoved(ComponentEvent e) {
                rememberGeometry();
            }

            @Override public void componentResized(ComponentEvent e) {
                rememberGeometry();
            }
        });
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent arg) {
                MainApplication.getMap().selectMapMode((MapMode) MainApplication.getMap().getDefaultButtonAction());
            }
        });
        String bounds = Config.getPref().get("cadastrewms.addr.bounds", null);
        if (bounds != null) {
            String[] b = bounds.split(",");
            dialog.setBounds(new Rectangle(
                    Integer.parseInt(b[0]), Integer.parseInt(b[1]), Integer.parseInt(b[2]), Integer.parseInt(b[3])));
        }
    }

    private void setSelectedWay(Way w) {
        this.selectedWay = w;
        link.setEnabled(w != null);
        link.repaint();
    }

    private static void setNewSelection(OsmPrimitive osm) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        Collection<OsmPrimitive> newSelection = new LinkedList<>(ds.getSelected());
        newSelection.clear();
        newSelection.add(osm);
        ds.setSelected(osm);
    }
}
