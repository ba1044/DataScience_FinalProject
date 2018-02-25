package edu.unh.cs980.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ProjectUtils {
	// This class will contain some common utility methods for Project.

	public static void writeToFile(String filePath, ArrayList<String> runfileStrings) {
		String fullpath = filePath;
		try (FileWriter runfile = new FileWriter(new File(fullpath))) {
			for (String line : runfileStrings) {
				runfile.write(line + "\n");
			}

			runfile.close();
		} catch (IOException e) {
			System.out.println("Could not open " + fullpath);
		}
	}

	public static HashMap<String, Float> sortByValueDesc(Map<String, Float> unsortMap) {

		return getTopValuesInMap(unsortMap, 0);
	}

	// Sort by the value of key, descending.
	// Return the top k values, if k = 0, return all sorted map.
	public static HashMap<String, Float> getTopValuesInMap(Map<String, Float> unsortMap, int k) {
		List<Map.Entry<String, Float>> list = new LinkedList<Map.Entry<String, Float>>(unsortMap.entrySet());

		Collections.sort(list, new Comparator<Map.Entry<String, Float>>() {

			public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
				return (o2.getValue()).compareTo(o1.getValue());
			}
		});

		HashMap<String, Float> sortedMap = new LinkedHashMap<String, Float>();
		int i = 0;
		for (Map.Entry<String, Float> entry : list)

		{
			if (i < k || k == 0) {
				sortedMap.put(entry.getKey(), entry.getValue());
				i++;
			} else {
				break;
			}
		}

		return sortedMap;
	}
}