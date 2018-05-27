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
package heigit.ors.util;

import java.io.UnsupportedEncodingException;

public class StringUtility {

	public static boolean isEmpty( String str )
    {
        return str == null || str.trim().length() == 0;
    }
	
	public static String substring(String str, char pattern)
	{
		int pos1 = -1, pos2 = -1;
		for (int j = 0; j < str.length(); j++)
		{
			if (str.charAt(j) == pattern) 
			{
				if (pos1 == -1)
					pos1 = j;
				else
				{
					pos2 = j;
					break;
				}
			}
		}
		
		if (pos1 != -1 && pos2 != -1)
			return str.substring(pos1 + 1, pos2);
		else
			return null;
	}
	
	public static boolean containsDigit(String s) {
		if (s != null && !s.isEmpty()) {
			 for ( int i = 0; i < s.length(); ++i ) {
				 char ch = s.charAt(i);
			      if ((ch >= '0' && ch <= '9' ))
			           return true;
			 }
		}

		return false;
	}
	
	public static String trimQuotes(String str)
	{
		return trim(str, '"');  
	}
	
	public static String trim(String str, char ch) {
		if (str == null)
			return null;
		String result = str;
		int firstChar = str.indexOf(ch);
		int lastChar = str.lastIndexOf(ch);
		int length = str.length();
		if (firstChar == 0 && lastChar == length - 1) 
			result = result.substring(1, length - 1);

		return result;
	}

	public static String arrayToString(double[] array, String separator)
	{
		int length = array.length;
	    if (length == 0) return "";
	    StringBuilder sb = new StringBuilder();	    
	    
	    for (int i = 0; i < length; ++i)
	    {
	    	sb.append(array[i]);
	    	if (i < length -1 )
	    		sb.append(separator);
	    }
	    return sb.toString();
	}
	
	public static String combine(String[] values, String separator)
	{
		if (values == null)
			return null;
		
		String result = "";
		
		int n = values.length;
		for (int i = 0 ; i < n; ++i)
		{
			result += values[i] + (i < n -1 ? separator: "");
		}
		
		return result;
	}
	
	public static String decodeRequestString(String inputString) throws UnsupportedEncodingException {
		if (inputString.startsWith("REQUEST=")) {
			inputString = inputString.substring(8, inputString.length());
		}
		if (inputString.startsWith("xml=")) {
			inputString = inputString.substring(4, inputString.length());
		} else if (inputString.startsWith("---")) {
			int iIndexStart = inputString.indexOf("<?xml");
			inputString = inputString.substring(iIndexStart, inputString.length());
			int iIndexEnd = inputString.indexOf("---");
			inputString = inputString.substring(0, iIndexEnd);
		}

		// Decode the application/x-www-form-url encoded query string
		return java.net.URLDecoder.decode(inputString, "ISO-8859-1");// "UTF-8");
	}

	public static String decodeRequestString2(String inputString) throws UnsupportedEncodingException {
		if (inputString.startsWith("REQUEST=")) {
			inputString = inputString.substring(8, inputString.length());
		} else if (inputString.startsWith("---")) {
			int iIndexStart = inputString.indexOf("<?xml");
			inputString = inputString.substring(iIndexStart, inputString.length());
			int iIndexEnd = inputString.indexOf("---");
			inputString = inputString.substring(0, iIndexEnd);
		}

		// Decode the application/x-www-form-url encoded query string
		return java.net.URLDecoder.decode(inputString, "ISO-8859-1");// "UTF-8");
	}
}
