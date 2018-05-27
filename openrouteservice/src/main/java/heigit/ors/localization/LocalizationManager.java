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
package heigit.ors.localization;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import heigit.ors.util.StringUtility;

public class LocalizationManager {
	protected static Logger LOGGER = LoggerFactory.getLogger(LocalizationManager.class);

	private Map<String, LanguageResources> _langResources = null;
	private static volatile LocalizationManager m_instance = null;

	private LocalizationManager() throws Exception
	{
		_langResources = new HashMap<String, LanguageResources>();

		loadLocalizations();
	}

	public static LocalizationManager getInstance() throws Exception
	{
		if(null == m_instance)
		{
			synchronized(LocalizationManager.class)
			{
				m_instance = new LocalizationManager();
			}
		}

		return m_instance;
	}

	private void loadLocalizations() throws Exception
	{
		File classFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getFile());
		String classPath = classFile.getAbsolutePath();
		String classesPath = classPath.substring(0, classPath.indexOf("classes") + "classes".length());
		Path localesPath = Paths.get(classesPath, "resources", "locales");

		String filePattern = "ors_(.*?).resources";
		Pattern pattern = Pattern.compile(filePattern);

		File[] files = new File(localesPath.toString()).listFiles();

		if (files == null)
			throw new Exception("Resources can not be found.");

		for (File file : files) {
			try
			{
				if (file.isFile()) {
					if (file.getName().matches(filePattern))
					{
						Matcher matcher = pattern.matcher(file.getName());
						if (matcher.find())
						{
							String langCode = matcher.group(1).toLowerCase();
							String[] langCountry = langCode.split("-");
							Locale locale = new Locale(langCountry[0], langCountry.length == 2 ? langCountry[1] : "");
							Language lang = new Language(langCode, locale.getDisplayLanguage(), locale.getDisplayName());
							LanguageResources langResources = new LanguageResources(lang);

							Config allConfig = ConfigFactory.parseFile(file);

							allConfig.entrySet().forEach(entry -> {
								langResources.addLocalString(entry.getKey(), StringUtility.trim(entry.getValue().render(), '\"'));
							});

							_langResources.put(langCode, langResources);
						}
					}
				}
			}
			catch(Exception ex)
			{
				LOGGER.error("Unable to load resources from file " + file.getAbsolutePath());				
			}
		}
	}

	public String getTranslation(String langCode, String name, boolean throwException) throws Exception
	{
		if (name == null)
			return null;

		LanguageResources langRes = _langResources.get(langCode);

		if (langRes != null)
			return langRes.getTranslation(name, throwException);

		if (throwException)
			throw new Exception("Unable to find translation for '" + name + "' in language '" + langCode + "'.");
		else
			return null;
	}

	public LanguageResources getLanguageResources(String langCode)
	{
		String lang = langCode.toLowerCase();
		LanguageResources res = _langResources.get(lang);
		if (res == null)
		{
			if (!langCode.contains("-"))
				res = _langResources.get(lang + "-" + lang);
		}

		return res;
	}

	public boolean isLanguageSupported(String langCode)
	{
		String lang = langCode.toLowerCase();
		boolean res = _langResources.containsKey(lang);

		if (!res)
		{
			if (!langCode.contains("-"))
				res = _langResources.containsKey(lang + "-" + lang);
		}

		return res;
	}

	public String[] getLanguages()
	{
		String[] langs = new String[_langResources.size()];
		int i = 0;
		for (Map.Entry<String, LanguageResources> entry : _langResources.entrySet())
		{
			langs[i] = entry.getKey();
			i++;
		}

		Arrays.sort(langs);
		
		return langs;
	}
}
