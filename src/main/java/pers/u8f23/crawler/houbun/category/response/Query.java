package pers.u8f23.crawler.houbun.category.response;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author 8f23
 * @create 2023/8/4-23:05
 */
@Getter
@Setter
public class Query
{
	private static final Set<String> EXCEPT_CATEGORY_NAMES = new HashSet<>(Arrays.asList(
		"日本漫画", "日本动画"
	));
	private Map<String, Page> pages;
	@JsonAdapter (FieldNormalizedAdapter.class)
	private Map<String, String> normalized;

	public static class FieldNormalizedAdapter extends TypeAdapter<Map<String, String>>
	{
		@Override
		public Map<String, String> read(JsonReader in) throws IOException
		{
			Map<String, String> result = new HashMap<>();
			in.beginArray();
			while (in.hasNext())
			{
				in.beginObject();
				String from = null, to = null;
				while (in.hasNext())
				{
					String name = in.nextName();
					switch (name)
					{
					case "from":
						from = in.nextString(); break;
					case "to":
						to = in.nextString(); break;
					default:
						in.skipValue();
					}
				}
				if (from != null && to != null)
				{
					result.put(to, from);
				}
				in.endObject();
			}
			in.endArray();
			return result;
		}

		@Override
		public void write(JsonWriter out, Map<String, String> value)
		{
			// nop
		}
	}

	public static Map<String, Set<String>> flat(Query query)
	{
		Map<String, Set<String>> resultMap = new HashMap<>();
		query.pages
			.values()
			.stream()
			.filter(page -> !page.getTitle().isEmpty())
			.forEach(page -> {

				String trueTitle = query.normalized == null
					? page.getTitle()
					: query.normalized.get(page.getTitle());
				if (trueTitle == null)
				{
					return;
				}
				List<String> rawCategories = page.getCategories();
				if (rawCategories == null)
				{
					return;
				}
				Set<String> categories = rawCategories
					.stream()
					.map(rawCate -> {
						if (rawCate.startsWith("Category:") && rawCate.endsWith("作品"))
						{
							return rawCate
								.replace("Category:", "")
								.replace("作品", "");
						} return null;
					})
					.filter(Objects::nonNull)
					.filter(c -> !EXCEPT_CATEGORY_NAMES.contains(c))
					.collect(Collectors.toSet());
				resultMap.put(trueTitle, categories);
			});
		return resultMap;
	}
}
