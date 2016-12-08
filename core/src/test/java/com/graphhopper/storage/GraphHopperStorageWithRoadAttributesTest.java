/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage;

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Karl Hübner
 */
public class GraphHopperStorageWithRoadAttributesTest extends GraphHopperStorageTest {
    private RoadAttributeExtension roadAttributeStorage;

    @Override
    protected GraphHopperStorage newGHStorage(Directory dir, boolean is3D) {
        roadAttributeStorage = new RoadAttributeExtension();
        encodingManager.enableRoadAttributes(true);
        return new GraphHopperStorage(dir, encodingManager, is3D, roadAttributeStorage);
    }

    @Override
    @Test
    public void testSave_and_fileFormat() throws IOException {
        graph = newGHStorage(new RAMDirectory(defaultGraphLoc, true), false).create(defaultSize);
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 10, 10);
        na.setNode(1, 11, 20);
        na.setNode(2, 12, 12);

        EdgeIteratorState iter1 = graph.edge(0, 1, 100, true);
        iter1.setWayGeometry(Helper.createPointList(1.5, 1, 2, 3));
        EdgeIteratorState iter2 = graph.edge(0, 2, 200, true);
        iter2.setWayGeometry(Helper.createPointList(3.5, 4.5, 5, 6));

        roadAttributeStorage.addRoadAttribute(iter1, RoadAttributeExtension.Type.HEIGHT, 44);
        roadAttributeStorage.addRoadAttribute(iter1, RoadAttributeExtension.Type.WEIGHT, 40);
        roadAttributeStorage.addRoadAttribute(iter1, RoadAttributeExtension.Type.LENGTH, 120);
        roadAttributeStorage.addRoadAttribute(iter1, RoadAttributeExtension.Type.WIDTH, 200);
        roadAttributeStorage.addRoadAttribute(iter2, RoadAttributeExtension.Type.LENGTH, 120);

        graph.flush();
        graph.close();

        graph = newGHStorage(new MMapDirectory(defaultGraphLoc), false);
        assertTrue(graph.loadExisting());
        roadAttributeStorage = (RoadAttributeExtension)graph.getExtension();

        int n0 = AbstractGraphStorageTester.getIdOf(graph, 10, 10);
        int n1 = AbstractGraphStorageTester.getIdOf(graph, 11, 20);
        int n2 = AbstractGraphStorageTester.getIdOf(graph, 12, 12);
        iter1 = GHUtility.getEdge(graph, n0, n1);
        iter2 = GHUtility.getEdge(graph, n0, n2);

        assertEquals(44, roadAttributeStorage.getRoadAttribute(iter1, RoadAttributeExtension.Type.HEIGHT));
        assertEquals(40, roadAttributeStorage.getRoadAttribute(iter1, RoadAttributeExtension.Type.WEIGHT));
        assertEquals(120, roadAttributeStorage.getRoadAttribute(iter1, RoadAttributeExtension.Type.LENGTH));
        assertEquals(200, roadAttributeStorage.getRoadAttribute(iter1, RoadAttributeExtension.Type.WIDTH));
        assertEquals(120, roadAttributeStorage.getRoadAttribute(iter2, RoadAttributeExtension.Type.LENGTH));
    }
/*

    @Test
    public void testEnsureCapacity() throws IOException {
        graph = newGHStorage(new MMapDirectory(defaultGraphLoc), false);
        graph.setSegmentSize(128);
        graph.create(100); // 100 is the minimum size

        // assert that roadAttributeStorage can hold 104 turn cost entries at the beginning
        assertEquals(104, roadAttributeStorage.getCapacity() / 16);

        Random r = new Random();

        NodeAccess na = graph.getNodeAccess();
        for (int i = 0; i < 100; i++) {
            double randomLat = 90 * r.nextDouble();
            double randomLon = 180 * r.nextDouble();

            na.setNode(i, randomLat, randomLon);
        }

        // Make node 50 the 'center' node
        for (int nodeId = 51; nodeId < 100; nodeId++) {
            graph.edge(50, nodeId, r.nextDouble(), true);
        }
        for (int nodeId = 0; nodeId < 50; nodeId++) {
            graph.edge(nodeId, 50, r.nextDouble(), true);
        }

        // add 100 turn cost entries around node 50
        for (int edgeId = 0; edgeId < 50; edgeId++) {
            roadAttributeStorage.addTurnInfo(edgeId, 50, edgeId + 50, 1337);
            roadAttributeStorage.addTurnInfo(edgeId + 50, 50, edgeId, 1337);
        }

        roadAttributeStorage.addTurnInfo(0, 50, 1, 1337);
        assertEquals(104, roadAttributeStorage.getCapacity() / 16); // we are still good here

        roadAttributeStorage.addTurnInfo(0, 50, 2, 1337);
        // A new segment should be added, which will support 128 / 16 = 8 more entries.
        assertEquals(112, roadAttributeStorage.getCapacity() / 16);
    }
*/
}
