// License: GPL. For details, see Readme.txt file.
package org.openstreetmap.gui.jmapviewer;


import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests of {@link TileRange} class.
 */
class TileRangeTest {

    /**
     * Unit test of {@link TileRange#size}.
     */
    @Test
    void testSize() {
        assertEquals(16, new TileRange(
                new TileXY(3, 3), 
                new TileXY(6, 6), 10).size());
    }
}
