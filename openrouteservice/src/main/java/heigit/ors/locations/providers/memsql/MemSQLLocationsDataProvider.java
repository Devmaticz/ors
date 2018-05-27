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
package heigit.ors.locations.providers.memsql;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.io.WKTWriter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import heigit.ors.locations.providers.LocationsDataProvider;
import heigit.ors.util.ArraysUtility;
import heigit.ors.exceptions.InternalServerException;
import heigit.ors.locations.LocationsCategory;
import heigit.ors.locations.LocationsCategoryClassifier;
import heigit.ors.locations.LocationsCategoryGroup;
import heigit.ors.locations.LocationsErrorCodes;
import heigit.ors.locations.LocationsRequest;
import heigit.ors.locations.LocationsResult;
import heigit.ors.locations.LocationsResultSortType;
import heigit.ors.locations.LocationsSearchFilter;

public class MemSQLLocationsDataProvider implements LocationsDataProvider 
{
	private static final Logger LOGGER = Logger.getLogger(MemSQLLocationsDataProvider.class.getName());

	private String _locationsQuery;
	private String _geomColumn;
	private String[] _queryColumns;
	private int _geomColumnIndex = -1;
	private int _latitudeColumnIndex = -1;
	private int _longitudeColumnIndex = -1;
	private GeometryFactory _geomFactory;
	private String _categoriesQuery;
	private HikariDataSource  _dataSource;

	public void init(Map<String, Object> parameters) throws Exception
	{
		_locationsQuery = null;
		_categoriesQuery = null;
		_dataSource = null;

		boolean queryHasWhere = false;
		String value = (String)parameters.get("locations_query");
		if (Helper.isEmpty(value))
			throw new InternalServerException(LocationsErrorCodes.UNKNOWN, "'locations_query' parameter can not be null or empty.");
		else
		{
			_locationsQuery = value;
			queryHasWhere = _locationsQuery.toLowerCase().indexOf("where") > 0;
		}

		value = (String)parameters.get("categories_query");
		if (Helper.isEmpty(value))
			throw new InternalServerException(LocationsErrorCodes.UNKNOWN, "'categories_query' parameter can not be null or empty.");
		else
			_categoriesQuery = value;

		_geomColumn = null;
		value = (String)parameters.get("geometry_column");
		if (Helper.isEmpty(value))
			throw new InternalServerException(LocationsErrorCodes.UNKNOWN, "'geometry_column' parameter can not be null or empty.");
		else
			_geomColumn = value;

		String latitudeColumn = (String)parameters.get("latitude_column");
		String longitudeColumn = (String)parameters.get("longitude_column");

		_locationsQuery = _locationsQuery.replace("!geometry_column!", _geomColumn) +  String.format(" with (index = %s, resolution = 8) ", _geomColumn) + (queryHasWhere ? " AND ": " WHERE ");

		_geomFactory = new GeometryFactory();

		HikariConfig config = new HikariConfig();
		String port = "3306";
		if (parameters.containsKey("port"))
			port = Integer.toString((Integer)parameters.get("port"));
		config.setJdbcUrl(String.format("jdbc:mysql://%s:%s/%s",parameters.get("host"), port, parameters.get("db_name")) + "?max_allowed_packet=104857600");
		config.addDataSourceProperty("user", parameters.get("user"));
		if (parameters.containsKey("password"))
			config.addDataSourceProperty("password", parameters.get("password"));
		if (parameters.containsKey("max_pool_size"))
			config.setMaximumPoolSize((Integer)parameters.get("max_pool_size"));
		config.setMinimumIdle(1);
		config.setConnectionTestQuery("SELECT 1");
		config.setAutoCommit(false);

		_dataSource = new HikariDataSource(config);

		// retrieve meta data
		Connection connection = _dataSource.getConnection();
		if (connection == null)
			throw new InternalServerException(LocationsErrorCodes.UNKNOWN, "Connection to Locations database has not been established.");

		LocationsRequest request = new LocationsRequest();
		request.setGeometry(_geomFactory.createPoint(new Coordinate(0,0)));
		request.setRadius(10);

		PreparedStatement statement = createLocationsStatement(request, connection);
		ResultSetMetaData rs = statement.getMetaData();
		_queryColumns = new String[rs.getColumnCount()];

		for (int i = 1; i <= rs.getColumnCount(); i++) 
		{
			String clmName = rs.getColumnName(i);

			_queryColumns[i - 1] = clmName;
			if (clmName.equalsIgnoreCase(_geomColumn))
				_geomColumnIndex = i;
			else if (latitudeColumn != null && clmName.equalsIgnoreCase(latitudeColumn))
				_latitudeColumnIndex = i;
			else if (longitudeColumn != null && clmName.equalsIgnoreCase(longitudeColumn))
				_longitudeColumnIndex = i;
		}

		if (_latitudeColumnIndex >=0 && _longitudeColumnIndex >= 0)
			_geomColumnIndex = -1;

		connection.close();
	}

	public List<LocationsResult> findLocations(LocationsRequest request) throws Exception
	{
		List<LocationsResult> results = new ArrayList<LocationsResult>();

		Connection connection = null;
		Exception exception = null;

		try
		{
			connection = _dataSource.getConnection();
			connection.setAutoCommit(false);

			StopWatch sw = null;
			if (LOGGER.isDebugEnabled())
			{
				sw = new StopWatch();
				sw.start();
			}

			PreparedStatement statement = null;
			ResultSet resSet = null;

			if (LOGGER.isDebugEnabled())
			{
				StopWatch sw2 = new StopWatch();
				sw2.start();

				statement = createLocationsStatement(request, connection);

				sw2.stop();

				LOGGER.debug(String.format("Preparing query took %.3f sec.", sw2.getSeconds()));

				sw2.start();
				resSet = statement.executeQuery();

				sw2.stop();
				LOGGER.debug(String.format("Executing query took %.3f sec.", sw2.getSeconds()));
			}
			else
			{
				statement = createLocationsStatement(request, connection);
				resSet = statement.executeQuery();
			}

			int nColumns = _queryColumns.length;

			while (resSet.next()) 
			{
				try {
					LocationsResult lr = new LocationsResult();

					for(int i = 1; i <= nColumns; i++)
					{
						if (i != _geomColumnIndex && i != _latitudeColumnIndex && i != _longitudeColumnIndex)
						{
							String value = resSet.getString(i);
							if (!Helper.isEmpty(value))
								lr.addProperty(_queryColumns[i - 1], value);
						}
					}

					if (_geomColumnIndex != -1)
					{
						String strGeom = resSet.getString(_geomColumnIndex);
						if (strGeom != null)
						{
							int pos1 = strGeom.indexOf('(');
							int pos2 = strGeom.indexOf(' ');
							String value = strGeom.substring(pos1 + 1, pos2);
							double x = Double.parseDouble(value);

							pos1 =  strGeom.indexOf(')');
							value = strGeom.substring(pos2+1, pos1);
							double y = Double.parseDouble(value);
							lr.setGeometry(_geomFactory.createPoint(new Coordinate(x,y)));
						}
					}
					else
					{
						double x = resSet.getDouble(_longitudeColumnIndex);
						double y = resSet.getDouble(_latitudeColumnIndex);
						lr.setGeometry(_geomFactory.createPoint(new Coordinate(x,y)));
					}

					results.add(lr);
				} catch (Exception ex) {
					LOGGER.error(ex);
					throw new IOException(ex.getMessage());
				}
			}

			resSet.close();
			statement.close();

			if (LOGGER.isDebugEnabled())
			{
				sw.stop();
				LOGGER.debug(String.format("Found %d locations in %.3f sec.", results.size(), sw.getSeconds()));
			}
		}
		catch(Exception ex)
		{
			LOGGER.error(ex);
			exception = new InternalServerException(LocationsErrorCodes.UNKNOWN, "Unable to retrieve data from the data source.");
		}
		finally
		{
			if (connection != null)
				connection.close();
		}

		if (exception != null)
			throw exception;

		return results;
	}

	private String buildSearchFilter(String cmdText, LocationsSearchFilter filter) throws Exception
	{
		if (filter != null)
		{
			if (filter.getCategoryGroupIds() != null)
				cmdText +=  "(" + buildCategoryGroupIdsFilter(filter.getCategoryGroupIds()) + ")" + " AND ";
			else if (filter.getCategoryIds() != null)
				cmdText +=  "(" + buildCategoryIdsFilter(filter.getCategoryIds()) + ")" + " AND ";

			if (filter.getName() != null)
				cmdText += "(name = '" + filter.getName() + "')" + " AND ";

			if (!Helper.isEmpty(filter.getWheelchair()))
			{
				if (filter.getWheelchair().indexOf(',') > 0)
					cmdText +=  "(wheelchair IN ("+ fixStringValues(filter.getWheelchair(), true) +"))" + " AND ";
				else
					cmdText +=  "(wheelchair = "+ fixStringValues(filter.getWheelchair(), false) +")" + " AND ";
			}
			if (!Helper.isEmpty(filter.getSmoking()))
			{
				if (filter.getSmoking().indexOf(',') > 0)
					cmdText +=  "(smoking IN (" + fixStringValues(filter.getSmoking(), true) + "))" + " AND ";
				else
					cmdText +=  "(smoking = "+ fixStringValues(filter.getSmoking(), false) +")" + " AND ";
			}

			if (filter.getFee() != null)
			{
				cmdText +=  "(fee =" + (filter.getFee() == true ? "1": "0") + ")" + " AND ";
			}
		}

		return cmdText;
	}

	private PreparedStatement createLocationsStatement(LocationsRequest request, Connection conn) throws Exception
	{
		String stateText = buildSearchFilter(_locationsQuery, request.getSearchFilter());

		Geometry geom = request.getGeometry();
		Envelope bbox = request.getBBox();
		if (bbox != null)
			stateText += buildBboxFilter(bbox) + (geom == null ? "" : " AND ");

		if (geom != null)
		{
			if (geom instanceof Point)
			{
				Point p = (Point)geom;
				stateText += String.format("GEOGRAPHY_WITHIN_DISTANCE(%s, GEOGRAPHY_POINT(%.7f, %.7f), %.1f)", _geomColumn, p.getCoordinate().x, p.getCoordinate().y, request.getRadius());
			} 
			else 
			{
				WKTWriter wktWriter = new WKTWriter();
				stateText += String.format("GEOGRAPHY_WITHIN_DISTANCE(%s, \"%s\", %.1f)", _geomColumn, wktWriter.write(geom), request.getRadius());
			}
		}

		if (request.getSortType() != LocationsResultSortType.NONE)
		{
			if (request.getSortType() == LocationsResultSortType.CATEGORY)
				stateText += " ORDER BY category";
			else if (request.getSortType() == LocationsResultSortType.DISTANCE)
			{
				if (geom instanceof Point)
				{
					Point p = (Point)geom;
					stateText += String.format(" ORDER BY GEOGRAPHY_DISTANCE(%s, GEOGRAPHY_POINT(%.7f, %.7f))" ,_geomColumn, p.getCoordinate().x, p.getCoordinate().y);
				}
				else
				{
					// is not supported
					//stateText += String.format(" ORDER BY GEOGRAPHY_DISTANCE(%s, \"%s\")" ,_geomColumn, strGeomWkt);
				}
			}
		}

		if (request.getLimit() > 0)
			stateText += " LIMIT 0, " + request.getLimit() + ";";

		PreparedStatement statement = conn.prepareStatement(stateText);    
		statement.setMaxRows(request.getLimit());

		return statement;
	}

	public List<LocationsCategory> findCategories(LocationsRequest request) throws Exception
	{
		List<LocationsCategory> results = new ArrayList<LocationsCategory>();

		Connection connection = null;
		Exception exception = null;

		try
		{
			connection = _dataSource.getConnection();
			connection.setAutoCommit(false);

			PreparedStatement statement = createCategoriesStatement(request, connection);			
			ResultSet resSet = statement.executeQuery();

			Map<Integer, Map<Integer, Long>> groupsStats = new HashMap<Integer, Map<Integer, Long>>();
			long[] groupCount = new long[LocationsCategoryClassifier.getGroupsCount()];

			while (resSet.next()) 
			{
				// "SELECT category, COUNT(category) FROM planet_osm_pois_test GROUP BY category"
				int catIndex = resSet.getInt(1);
				int groupIndex = LocationsCategoryClassifier.getGroupIndex(catIndex);
				long count = resSet.getLong(2);

				Map<Integer, Long> stats = groupsStats.get(groupIndex);
				if (stats == null)
				{
					stats = new HashMap<Integer, Long>();
					groupsStats.put(groupIndex, stats);
				}

				groupCount[groupIndex] += count;
				stats.put(catIndex, count);
			}

			resSet.close();
			statement.close();

			for (Map.Entry<Integer, Map<Integer, Long>> stats : groupsStats.entrySet())
			{
				int groupIndex = stats.getKey();
				LocationsCategory lc = new LocationsCategory(LocationsCategoryClassifier.getGroupId(groupIndex), LocationsCategoryClassifier.getGroupName(groupIndex), stats.getValue(), groupCount[groupIndex]);

				results.add(lc);
			}
		}
		catch(Exception ex)
		{
			LOGGER.error(ex);
			exception = new InternalServerException(LocationsErrorCodes.UNKNOWN, "Unable to retrieve data from the data source.");
		}
		finally
		{
			if (connection != null)
				connection.close();
		}

		if (exception != null)
			throw exception;

		return results;
	}

	private PreparedStatement createCategoriesStatement(LocationsRequest request, Connection conn) throws Exception 
	{
		String cmdFilter = buildSearchFilter("", request.getSearchFilter()); 

		Geometry geom = request.getGeometry();
		Envelope bbox = request.getBBox();
		if (bbox != null)
			cmdFilter += buildBboxFilter(bbox) + (geom == null ? "" : " AND ");

		if (geom != null)
		{
			if (geom instanceof Point)
			{
				Point p = (Point)geom;
				cmdFilter += String.format("GEOGRAPHY_WITHIN_DISTANCE(%s, GEOGRAPHY_POINT(%.7f, %.7f), %.1f)", _geomColumn, p.getCoordinate().x, p.getCoordinate().y, request.getRadius());
			} 
			else 
			{
				WKTWriter wktWriter = new WKTWriter();
				cmdFilter += String.format("GEOGRAPHY_WITHIN_DISTANCE(%s, \"%s\", %.1f)", _geomColumn, wktWriter.write(geom), request.getRadius());
			}
		}

		if (cmdFilter != "")
			cmdFilter = " WHERE " + cmdFilter;

		String stateText = _categoriesQuery.replace("!where_clause!", cmdFilter);

		return conn.prepareStatement(stateText);    
	}

	private String buildBboxFilter(Envelope bbox)
	{
		String polygon = "'POLYGON((" + bbox.getMinX() + " " + bbox.getMinY() + "," +
				bbox.getMinX() + " " + bbox.getMaxY() + "," + 
				bbox.getMaxX() + " " + bbox.getMaxY() + "," + 
				bbox.getMinY() + " " + bbox.getMaxY() + "," +
				bbox.getMinX() + " " + bbox.getMinY() +"))'";
		return "GEOGRAPHY_INTERSECTS(" + polygon +  "," + _geomColumn + ")";
	}

	private String buildCategoryIdsFilter(int[] ids)
	{
		if (ids == null)
			return "";

		if (ids.length == 1)
			return " category = " + ids[0];
		else
			return " category IN (" + ArraysUtility.toString(ids, ", ") + ")";
	}

	private String buildCategoryGroupIdsFilter(int[] ids) throws Exception
	{
		if (ids == null)
			return "";

		String result = "";

		if (ids.length > 1)
		{
			int nValues = ids.length;

			for (int i = 0; i< nValues; i++)
			{
				int groupId = ids[i];
				LocationsCategoryGroup group = LocationsCategoryClassifier.getGroupById(groupId);
				if (group == null)
					throw  new InternalServerException(LocationsErrorCodes.UNKNOWN, "Unknown group id '" + groupId + "'.");

				result += "("+ group.getMinCategoryId() + " <= category AND category <= " + group.getMaxCategoryId() + ")";

				if (i < nValues - 1)
					result += " OR ";
			}

			result = "(" + result +")";
		}
		else
		{
			int groupId = ids[0];

			LocationsCategoryGroup group = LocationsCategoryClassifier.getGroupById(groupId);
			if (group == null)
				throw new InternalServerException(LocationsErrorCodes.UNKNOWN, "Unknown group id '" + groupId + "'.");

			result = group.getMinCategoryId() + " <= category AND category <= " + group.getMaxCategoryId(); 
		}

		return result;
	}

	private String fixStringValues(String value, Boolean multipeValues)
	{
		if (multipeValues)
		{
			String result = "";

			String[] values = value.split(",");
			int nValues = values.length;
			for(int i = 0; i < nValues; i++)
			{
				result += '\''+ values[i].trim()+'\'';
				if (i < nValues - 1)
					result += ",";
			}

			return result;
		}
		else
		{
			if (value.indexOf('\'') > 0)
				return value;
			else
				return '\''+ value+'\'';
		}
	}
	
	public void close()
	{
		if (_dataSource != null)
		{
			_dataSource.close();
			_dataSource= null;
		}
	}

	public String getName()
	{
		return "memsql";
	}
}
