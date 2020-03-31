/*
 * Copyright 2002-2006 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

/**
 * @author Juergen Hoeller
 * @since 18.01.2006
 */
public class BeanWrapperGenericsTests extends TestCase {

	public void testGenericSet() {
		GenericBean gb = new GenericBean();
		BeanWrapper bw = new BeanWrapperImpl(gb);
		Set input = new HashSet();
		input.add("4");
		input.add("5");
		bw.setPropertyValue("integerSet", input);
		assertTrue(gb.getIntegerSet().contains(new Integer(4)));
		assertTrue(gb.getIntegerSet().contains(new Integer(5)));
	}

	public void testGenericList() throws MalformedURLException {
		GenericBean gb = new GenericBean();
		BeanWrapper bw = new BeanWrapperImpl(gb);
		List input = new ArrayList();
		input.add("http://localhost:8080");
		input.add("http://localhost:9090");
		bw.setPropertyValue("resourceList", input);
		assertEquals(new UrlResource("http://localhost:8080"), gb.getResourceList().get(0));
		assertEquals(new UrlResource("http://localhost:9090"), gb.getResourceList().get(1));
	}

	public void testGenericListElement() throws MalformedURLException {
		GenericBean gb = new GenericBean();
		gb.setResourceList(new ArrayList<Resource>());
		BeanWrapper bw = new BeanWrapperImpl(gb);
		bw.setPropertyValue("resourceList[0]", "http://localhost:8080");
		assertEquals(new UrlResource("http://localhost:8080"), gb.getResourceList().get(0));
	}

	public void testGenericMap() {
		GenericBean gb = new GenericBean();
		BeanWrapper bw = new BeanWrapperImpl(gb);
		Map input = new HashMap();
		input.put("4", "5");
		input.put("6", "7");
		bw.setPropertyValue("shortMap", input);
		assertEquals(new Integer(5), gb.getShortMap().get(new Short("4")));
		assertEquals(new Integer(7), gb.getShortMap().get(new Short("6")));
	}

	public void testGenericMapElement() {
		GenericBean gb = new GenericBean();
		gb.setShortMap(new HashMap<Short,Integer>());
		BeanWrapper bw = new BeanWrapperImpl(gb);
		bw.setPropertyValue("shortMap[4]", "5");
		assertEquals(new Integer(5), gb.getShortMap().get(new Short("4")));
	}

	public void testGenericMapWithKeyType() {
		GenericBean gb = new GenericBean();
		BeanWrapper bw = new BeanWrapperImpl(gb);
		Map input = new HashMap();
		input.put("4", "5");
		input.put("6", "7");
		bw.setPropertyValue("longMap", input);
		assertEquals("5", gb.getLongMap().get(new Long("4")));
		assertEquals("7", gb.getLongMap().get(new Long("6")));
	}

	public void testGenericMapElementWithKeyType() {
		GenericBean gb = new GenericBean();
		gb.setLongMap(new HashMap<Long,Integer>());
		BeanWrapper bw = new BeanWrapperImpl(gb);
		bw.setPropertyValue("longMap[4]", "5");
		assertEquals("5", gb.getLongMap().get(new Long("4")));
		assertEquals("5", bw.getPropertyValue("longMap[4]"));
	}

	public void testGenericMapWithCollectionValue() {
		GenericBean gb = new GenericBean();
		BeanWrapper bw = new BeanWrapperImpl(gb);
		bw.registerCustomEditor(Number.class, new CustomNumberEditor(Integer.class, false));
		Map input = new HashMap();
		HashSet value1 = new HashSet();
		value1.add(new Integer(1));
		input.put("1", value1);
		ArrayList value2 = new ArrayList();
		value2.add(Boolean.TRUE);
		input.put("2", value2);
		bw.setPropertyValue("collectionMap", input);
		assertTrue(gb.getCollectionMap().get(new Integer(1)) instanceof HashSet);
		assertTrue(gb.getCollectionMap().get(new Integer(2)) instanceof ArrayList);
	}

	public void testGenericMapElementWithCollectionValue() {
		GenericBean gb = new GenericBean();
		gb.setCollectionMap(new HashMap<Number,Collection<? extends Object>>());
		BeanWrapper bw = new BeanWrapperImpl(gb);
		bw.registerCustomEditor(Number.class, new CustomNumberEditor(Integer.class, false));
		HashSet value1 = new HashSet();
		value1.add(new Integer(1));
		bw.setPropertyValue("collectionMap[1]", value1);
		assertTrue(gb.getCollectionMap().get(new Integer(1)) instanceof HashSet);
	}

	public void testGenericListOfLists() throws MalformedURLException {
		GenericBean gb = new GenericBean();
		List<List<Integer>> list = new LinkedList<List<Integer>>();
		list.add(new LinkedList<Integer>());
		gb.setListOfLists(list);
		BeanWrapper bw = new BeanWrapperImpl(gb);
		bw.setPropertyValue("listOfLists[0][0]", new Integer(5));
		assertEquals(new Integer(5), gb.getListOfLists().get(0).get(0));
	}

	public void testGenericListOfListsWithElementConversion() throws MalformedURLException {
		GenericBean gb = new GenericBean();
		List<List<Integer>> list = new LinkedList<List<Integer>>();
		list.add(new LinkedList<Integer>());
		gb.setListOfLists(list);
		BeanWrapper bw = new BeanWrapperImpl(gb);
		bw.setPropertyValue("listOfLists[0][0]", "5");
		assertEquals(new Integer(5), gb.getListOfLists().get(0).get(0));
	}

	public void testGenericListOfMaps() throws MalformedURLException {
		GenericBean gb = new GenericBean();
		List<Map<Integer, Long>> list = new LinkedList<Map<Integer, Long>>();
		list.add(new HashMap<Integer, Long>());
		gb.setListOfMaps(list);
		BeanWrapper bw = new BeanWrapperImpl(gb);
		bw.setPropertyValue("listOfMaps[0][10]", new Long(5));
		assertEquals(new Long(5), gb.getListOfMaps().get(0).get(10));
	}

	public void testGenericListOfMapsWithElementConversion() throws MalformedURLException {
		GenericBean gb = new GenericBean();
		List<Map<Integer, Long>> list = new LinkedList<Map<Integer, Long>>();
		list.add(new HashMap<Integer, Long>());
		gb.setListOfMaps(list);
		BeanWrapper bw = new BeanWrapperImpl(gb);
		bw.setPropertyValue("listOfMaps[0][10]", "5");
		assertEquals(new Long(5), gb.getListOfMaps().get(0).get(10));
	}

	public void testGenericMapOfMaps() throws MalformedURLException {
		GenericBean gb = new GenericBean();
		Map<String, Map<Integer, Long>> map = new HashMap<String, Map<Integer, Long>>();
		map.put("mykey", new HashMap<Integer, Long>());
		gb.setMapOfMaps(map);
		BeanWrapper bw = new BeanWrapperImpl(gb);
		bw.setPropertyValue("mapOfMaps[mykey][10]", new Long(5));
		assertEquals(new Long(5), gb.getMapOfMaps().get("mykey").get(10));
	}

	public void testGenericMapOfMapsWithElementConversion() throws MalformedURLException {
		GenericBean gb = new GenericBean();
		Map<String, Map<Integer, Long>> map = new HashMap<String, Map<Integer, Long>>();
		map.put("mykey", new HashMap<Integer, Long>());
		gb.setMapOfMaps(map);
		BeanWrapper bw = new BeanWrapperImpl(gb);
		bw.setPropertyValue("mapOfMaps[mykey][10]", "5");
		assertEquals(new Long(5), gb.getMapOfMaps().get("mykey").get(10));
	}

	public void testGenericMapOfLists() throws MalformedURLException {
		GenericBean gb = new GenericBean();
		Map<String, List<Integer>> map = new HashMap<String, List<Integer>>();
		map.put("mykey", new LinkedList<Integer>());
		gb.setMapOfLists(map);
		BeanWrapper bw = new BeanWrapperImpl(gb);
		bw.setPropertyValue("mapOfLists[mykey][0]", new Integer(5));
		assertEquals(new Integer(5), gb.getMapOfLists().get("mykey").get(0));
	}

	public void testGenericMapOfListsWithElementConversion() throws MalformedURLException {
		GenericBean gb = new GenericBean();
		Map<String, List<Integer>> map = new HashMap<String, List<Integer>>();
		map.put("mykey", new LinkedList<Integer>());
		gb.setMapOfLists(map);
		BeanWrapper bw = new BeanWrapperImpl(gb);
		bw.setPropertyValue("mapOfLists[mykey][0]", "5");
		assertEquals(new Integer(5), gb.getMapOfLists().get("mykey").get(0));
	}

}
