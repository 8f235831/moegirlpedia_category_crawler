package pers.u8f23.crawler.houbun.category.entity;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * @author 8f23
 * @create 2023/7/2-17:11
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ParseResponse
{
	@SerializedName ("parse")
	private ParseResponseBody body;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ParseResponseBody
	{

		private String title;
		@SerializedName ("pageid")
		private long pageId;
		@SerializedName ("revid")
		private long revId;
		private Map<String, String> text;
		private List<Category> categories;
	}

	public static class Category
	{
		@SerializedName ("sortkey")
		private String sortKey;
		@SerializedName ("*")
		private String name;
	}
}
