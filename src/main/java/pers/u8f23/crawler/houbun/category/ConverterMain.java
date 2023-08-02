package pers.u8f23.crawler.houbun.category;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * @author 8f23
 * @create 2023/7/2-22:32
 */
@Slf4j
public class ConverterMain
{
	public static void main(String[] args) throws Exception
	{
		String src =
			"D:\\Myworks\\Java\\20230702-01-moegirlpedia-houbun-category\\output\\result.json";
		String des =
			"D:\\Myworks\\Java\\20230702-01-moegirlpedia-houbun-category\\output\\simplified.txt";
		File f = new File(src);
		InputStream is = Files.newInputStream(f.toPath());
		byte[] bytes = new byte[(int) f.length()];
		is.read(bytes);
		is.close();
		String json = new String(bytes);
		Map<String, Set<String>> obj = new Gson().fromJson(json, new TypeToken<Map<String,
			Set<String>>>(){}.getType());
		Set<String> resultMap = new HashSet<>();
		obj.forEach((k, v) -> {
			resultMap.add(k);
			resultMap.addAll(v);
		});
		List<String> result = new ArrayList<>(resultMap);
		Collections.sort(result);
		PrintStream ps = new PrintStream(des);
		result.forEach(ps::println);
		ps.close();
	}

}
