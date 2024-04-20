package pers.u8f23.crawler.houbun.category.response;

import lombok.Getter;
import lombok.Setter;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import retrofit2.Response;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author 8f23
 * @create 2024/4/20-23:17
 */
@Getter
@Setter
public class CategoryPageParsed
{

	private Set<String> nextPagePaths;
	private Set<String> subCategories;
	private Set<String> subPages;

	public static final CategoryPageParsed EMPTY_RESULT;

	static
	{
		EMPTY_RESULT = new CategoryPageParsed();
		EMPTY_RESULT.nextPagePaths = Collections.emptySet();
		EMPTY_RESULT.subCategories = Collections.emptySet();
		EMPTY_RESULT.subPages = Collections.emptySet();
	}

	private CategoryPageParsed(){}

	public static CategoryPageParsed parse(Response<ResponseBody> rawResponse)
	{
		if (rawResponse == null)
		{
			return EMPTY_RESULT;
		}
		String content = null;
		try (ResponseBody body = rawResponse.body())
		{
			if (body == null)
			{
				return EMPTY_RESULT;
			}
			content = body.string();
		}
		catch (IOException ignored)
		{
		}
		if (content == null)
		{
			return EMPTY_RESULT;
		}
		Document document = Jsoup.parse(content);
		CategoryPageParsed res = new CategoryPageParsed();
		Element subCateRoot = document.select("#mw-subcategories").first();
		if (subCateRoot != null)
		{
			Elements linkElements = subCateRoot.select("a");
			res.subCategories = new LinkedHashSet<>();
			for (Element linkElement : linkElements)
			{
				String link = linkElement.attr("href");
				if (linkElement.text().equals("下一页") && !link.isEmpty())
				{
					if (res.nextPagePaths == null)
					{
						res.nextPagePaths = new LinkedHashSet<>();
					}
					res.nextPagePaths.add(link);
					continue;
				}
				if (!link.isEmpty())
				{
					try
					{
						link = URLDecoder.decode(link, StandardCharsets.UTF_8);
					}
					catch (Exception ignored)
					{
					}
					// remove char '/'
					res.subCategories.add(link.substring(1));
				}
			}
		}
		Element subPageRoot = document.select("#mw-pages").first();
		if (subPageRoot != null)
		{
			Elements linkElements = subPageRoot.select("a");
			res.subPages = new LinkedHashSet<>();
			for (Element linkElement : linkElements)
			{
				String link = linkElement.attr("href");
				if (linkElement.text().equals("下一页") && !link.isEmpty())
				{
					if (res.nextPagePaths == null)
					{
						res.nextPagePaths = new LinkedHashSet<>();
					}
					res.nextPagePaths.add(link);
					continue;
				}
				if (!link.isEmpty())
				{
					try
					{
						link = URLDecoder.decode(link, StandardCharsets.UTF_8);
					}
					catch (Exception ignored)
					{
					}
					// remove char '/'
					res.subPages.add(link.substring(1));
				}
			}
		}
		if (res.subCategories == null)
		{
			res.subCategories = Collections.emptySet();
		}
		if (res.subPages == null)
		{
			res.subPages = Collections.emptySet();
		}
		if (res.nextPagePaths == null)
		{
			res.nextPagePaths = Collections.emptySet();
		}
		return res;
	}
}
