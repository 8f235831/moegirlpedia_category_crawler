package pers.u8f23.crawler.houbun.category;

import lombok.NonNull;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author 8f23
 * @create 2023/7/2-17:09
 */
public class HttpUtils
{
	/**
	 * 请求10秒则超时。
	 */
	public static final int TIME_OUT = 10;
	public static final String BASE_URL = "https://zh.moegirl.org.cn";

	private static final Retrofit SERVICE_CREATOR;

	static
	{
		// 日志拦截器
		HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
		logging.setLevel(HttpLoggingInterceptor.Level.BODY);

		OkHttpClient client = new OkHttpClient.Builder()
			.addInterceptor(logging)
			.connectTimeout(TIME_OUT, TimeUnit.SECONDS)
			.build();

		SERVICE_CREATOR = new Retrofit.Builder()
			.addConverterFactory(GsonConverterFactory.create())
			.baseUrl(BASE_URL)
			.addCallAdapterFactory(RxJava3CallAdapterFactory.create())
			.client(client)
			.build();
	}

	/**
	 * 直接请求内容，内容不会置入缓存。离线不可用。
	 *
	 * @param clazz Retrofit接口类，应继承自<code>BaseHttpService</code> 。
	 * @return 接口实例。
	 */
	public static <S> S buildService(Class<S> clazz)
	{
		return SERVICE_CREATOR.create(clazz);
	}

	@NonNull
	public static Set<String> parseRawHtmlCategoryPage(Response<ResponseBody> rawResponse)
	{
		Set<String> resultSet = new HashSet<>();
		if (rawResponse == null)
		{
			return resultSet;
		}
		String content = null;
		try (ResponseBody body = rawResponse.body())
		{
			if (body == null)
			{
				return resultSet;
			}
			content = body.string();
		}
		catch (IOException ignored)
		{
		}
		if (content == null)
		{
			return resultSet;
		}
		Document document = Jsoup.parse(content);
		resultSet.addAll(findAllLinks(document.select("#mw-subcategories").first()));
		resultSet.addAll(findAllLinks(document.select("#mw-pages").first()));
		return resultSet;
	}

	private static List<String> findAllLinks(Element root)
	{
		LinkedList<String> result = new LinkedList<>();
		if (root == null)
		{
			return result;
		}
		Elements linkElements = root.select("a");
		for (Element linkElement : linkElements)
		{
			String link = linkElement.attr("href");
			if (!link.isEmpty())
			{
				try
				{
					link = URLDecoder.decode(link, "UTF-8");
				}
				catch (Exception ignored)
				{
				}
				// remove char '/'
				result.add(link.substring(1));
			}
		}
		return result;
	}
}
