/*
 *  Licensed to GIScience Research Group, Heidelberg University (GIScience)
 *
 *   http://www.giscience.uni-hd.de
 *   http://www.heigit.org
 *
 *  under one or more contributor license agreements. See the NOTICE file 
 *  distributed with this work for additional information regarding copyright 
 *  ownership. The GIScience licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in compliance 
 *  with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package heigit.ors.locations;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import heigit.ors.exceptions.MissingParameterException;
import heigit.ors.util.FileUtility;
public class LocationsCategoryClassifier 
{
	private static int[] _categoryIdToGroupIndex;
	private static LocationsCategoryGroup[] _categoryGroups;
	private static Map<Integer, LocationsCategoryGroup> _groupsMap;
	
	private static JSONObject _categoriesJSON;
	 
	static
	{
		try
		{
			Path categoriesPath = Paths.get(FileUtility.getResourcesPath().toString(), "services", "locations", "categories.txt");
			File categoriesFile = categoriesPath.toFile();
			if (!categoriesFile.exists())
				throw new MissingParameterException("File 'categories.txt' is missing.");
			FileInputStream fstream = new FileInputStream(categoriesFile);
			BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
			List<LocationsCategoryGroup> groups = new ArrayList<LocationsCategoryGroup>();
			
			int groupId = -1;
			String groupName = null;
			int minId = Integer.MAX_VALUE;
			int maxId = Integer.MIN_VALUE;
			int totalMaxId = Integer.MIN_VALUE;
			Map<String, Integer> groupCategories = null;
			String strLine;
			while ((strLine = br.readLine()) != null)   {
				if (strLine == null || strLine == "")
					continue;
				if (strLine.startsWith("#"))
				{
					if (groupName != null)
					{
						LocationsCategoryGroup group = new LocationsCategoryGroup(groupId, groupName, minId, maxId, groupCategories);
						groups.add(group);		
					}
					
					String[] values = strLine.split("\t");
					groupName = values[0].substring(values[0].indexOf("#") + 1).trim();
					groupId = Integer.parseInt(values[2].trim());
					
					groupCategories = new LinkedHashMap<String, Integer>();
					
					minId = Integer.MAX_VALUE;
					maxId = Integer.MIN_VALUE;
				}
				else
				{
					String[] values = strLine.split("\t");
					if (values.length == 3)
					{
						// shop supermarket 102
						//String tagName = values[0];
						String tagValue = values[1];
						int catId = Integer.parseInt(values[2].trim());
						
						groupCategories.put(tagValue, catId);
						
						if (minId > catId)
							minId = catId;
						if (maxId < catId)
							maxId = catId;
						
						if (totalMaxId < catId)
							totalMaxId = catId;
					}
				}
			}
			br.close();
			
			if (groupName != null)
			{
				LocationsCategoryGroup group = new LocationsCategoryGroup(groupId, groupName, minId, maxId, groupCategories);
				groups.add(group);		
			}
			_groupsMap = new HashMap<Integer, LocationsCategoryGroup>();
			_categoryGroups  = groups.toArray(new LocationsCategoryGroup[groups.size()]);
			_categoryIdToGroupIndex = new int[totalMaxId + 1];
			
			for (int i = 0; i < _categoryIdToGroupIndex.length; i++)
				_categoryIdToGroupIndex[i] = -1;
			
		    Map<String, JSONObject> map = new TreeMap<String, JSONObject>(); 
			int j = 0;
			for(LocationsCategoryGroup group : _categoryGroups)
			{
				for (int i = group.getMinCategoryId(); i<= group.getMaxCategoryId();i++)
					_categoryIdToGroupIndex[i] = j;
				
				_groupsMap.put(group.getId(), group);
				
				JSONObject jGroup = new JSONObject(true);
				JSONObject jValues = new JSONObject(true);
				for(Map.Entry<String, Integer> category : group.getCategories().entrySet())
				{
					jValues.put(category.getKey(), category.getValue());
				}
				
				jGroup.put("id", group.getId());
				jGroup.put("values", jValues);
				
				map.put(group.getName(), jGroup);
				
				j++;
			}
			
			JSONObject jCategories = new JSONObject(true);
			for (Map.Entry<String, JSONObject> entry : map.entrySet())
				jCategories.put(entry.getKey(), entry.getValue());
			
			_categoriesJSON = jCategories;
		}
		catch(Exception ex)
		{
			Logger logger = Logger.getLogger(LocationsCategoryClassifier.class.getName());
			logger.error(ex);
		}
	}
	
	public static int getGroupsCount()
	{
		return _categoryGroups.length;
	}
	
	public static int getGroupIndex(int categoryId)
	{
		if (categoryId >=0 && categoryId < _categoryIdToGroupIndex.length)
			return _categoryIdToGroupIndex[categoryId];
		else
			return -1;
	}
	
	public static String getGroupName(int groupIndex)
	{
		if (_categoryGroups != null && groupIndex >=0 && groupIndex < _categoryGroups.length)
			return _categoryGroups[groupIndex].getName();
		else
			return null;
	}
	
	public static int getGroupId(int groupIndex)
	{
		if (_categoryGroups != null && groupIndex >=0 && groupIndex < _categoryGroups.length)
			return _categoryGroups[groupIndex].getId();
		else
			return -1;
	}
	
	public static LocationsCategoryGroup getGroupById(int groupId)
	{
		return _groupsMap.get(groupId);
	}
	
	public static JSONObject getCategoriesList()
	{
		return _categoriesJSON;
	}
}