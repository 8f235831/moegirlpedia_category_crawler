package pers.u8f23.crawler.houbun.category.response;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author 8f23
 * @create 2023/8/4-23:02
 */
@Setter
@Getter
public class Page
{
	@SerializedName ("pageid")
	private long pageId;
	private String title;
	private String missing;
	@JsonAdapter (FieldCategoriesAdapter.class)
	private List<String> categories;

	public static class FieldCategoriesAdapter extends TypeAdapter<List<String>>
	{
		@Override
		public List<String> read(JsonReader in) throws IOException
		{
			List<String> result = new ArrayList<>();
			in.beginArray();
			while (in.hasNext())
			{
				in.beginObject();
				String title = null;
				while (in.hasNext())
				{
					String name = in.nextName();
					if (name.equals("title"))
					{
						title = in.nextString();
					}
					else
					{
						in.skipValue();
					}
				}
				if (title != null)
				{
					result.add(title);
				}
				in.endObject();
			}
			in.endArray();
			return result;
		}

		@Override
		public void write(JsonWriter out, List<String> value)
		{

		}
	}
}
