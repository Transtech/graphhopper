/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

import com.graphhopper.util.Helper;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class RAMDirectoryTest {

    String location = "./target/tmp/ramdir";
    File file = new File(location);

    @After
    @Before
    public void setUp() {
        Helper.deleteDir(file);
    }

    @Test
    public void testNoDuplicates() {
        RAMDirectory dir = new RAMDirectory();
        DataAccess da1 = dir.findAttach("testing");
        DataAccess da2 = dir.findAttach("testing");
        assertTrue(da1 == da2);
    }

    @Test
    public void testNoErrorForDACreate() {
        RAMDirectory dir = new RAMDirectory(location, true);
        DataAccess da = dir.findAttach("testing");
        da.createNew(100);
        da.flush();
    }

    @Test
    public void testLoadProperties() {
        final Reader reader = new StringReader("testing=testing0\nnice=nice1\n_version=0.1");
        RAMDirectory dir = new RAMDirectory(location, true) {
            @Override protected Reader createReader(String location) {
                return reader;
            }
        };
        assertEquals(location + "/nice1", dir.findAttach("nice").getLocation());
        DataAccess da = dir.findAttach("ui");
        assertEquals("ui", da.getId());
        assertEquals(location + "/ui3", da.getLocation());
    }

    @Test
    public void testLoadPropertiesNoFile() {
        RAMDirectory dir = new RAMDirectory(location, true) {
            @Override protected Reader createReader(String location) throws IOException {
                throw new IOException("I cannot find location");
            }
        };
        assertEquals("nodes", dir.findAttach("nodes").getId());
        assertEquals("edges", dir.findAttach("edges").getId());
    }

    @Test
    public void testStoreProperties() {
        final Writer sw = new StringWriter();
        RAMDirectory dir = new RAMDirectory(location, true) {
            @Override protected Writer createWriter(String location) {
                return sw;
            }
        };
        dir.findAttach("testing");
        dir.findAttach("nice");
        Set<String> mySet = new HashSet<String>(Arrays.asList(sw.toString().split("\n")));
        assertTrue(mySet.toString(), mySet.contains("testing=testing0"));
        assertTrue(mySet.toString(), mySet.contains("nice=nice1"));
    }
}
